package com.talkswithtanha.twt.core.session

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestoreException
import com.talkswithtanha.twt.core.data.Snapshot
import com.talkswithtanha.twt.core.data.UserRepository
import com.talkswithtanha.twt.core.model.LoginProvider
import com.talkswithtanha.twt.core.model.User
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app's single answer to "who is using this, and what may they do".
 *
 * The Android counterpart of iOS's `SessionManager`. Application-scoped, because
 * the profile listener must outlive any one screen: membership is changed from
 * the admin panel, and a member who has just redeemed a code should be let in
 * without force-quitting.
 */
@Singleton
class SessionRepository @Inject constructor(
    private val authService: AuthService,
    private val userRepository: UserRepository,
    private val deviceIdentity: DeviceIdentity,
    private val scope: CoroutineScope
) {

    /** What the UI should show while the session is still being worked out. */
    sealed interface State {
        data object Restoring : State
        data object SignedOut : State
        /** Signed in, but the profile document has not arrived yet. */
        data object LoadingProfile : State
        data object SignedIn : State
    }

    private val _state = MutableStateFlow<State>(State.Restoring)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    /** Set when the profile could not be loaded, so a screen can offer a retry
     *  instead of silently showing an empty account. */
    private val _profileError = MutableStateFlow<String?>(null)
    val profileError: StateFlow<String?> = _profileError.asStateFlow()

    /** Raised when this device is signed out because the account was taken over
     *  on another device. Consumed by the UI, then acknowledged. */
    private val _wasKickedByOtherDevice = MutableStateFlow(false)
    val wasKickedByOtherDevice: StateFlow<Boolean> = _wasKickedByOtherDevice.asStateFlow()

    /**
     * The gate the whole app turns on.
     *
     * Derived from [currentUser] rather than stored, so it re-evaluates when the
     * profile listener delivers a change — which is what closes the app again
     * within a second of a code being revoked from the admin panel.
     */
    val hasAppAccess: StateFlow<Boolean> = currentUser
        .map { it?.hasAppAccess == true }
        .stateIn(scope, SharingStarted.Eagerly, false)

    private var authListener: AutoCloseable? = null
    private var profileJob: Job? = null
    private var watchedUid: String? = null

    /**
     * Whether the auth listener has delivered its first callback yet.
     *
     * Firebase calls an auth state listener once immediately on registration
     * with whatever session it restored, and then again on every later sign-in
     * and sign-out. Those two kinds of callback need opposite handling -- see
     * [onAuthChanged] -- and this is how they are told apart.
     */
    private var initialAuthStateHandled = false

    /**
     * Every session id this installation has written during this process.
     *
     * A snapshot can legitimately carry an id that is not the latest one this
     * device holds: a claim rotates the stored id *before* its write reaches the
     * server, and a snapshot already on its way in still shows the previous one.
     * If that previous id was ours too, nobody took the account. Only an id this
     * device never generated is evidence of another device.
     */
    private val ownSessionIds = mutableSetOf<String>()

    /** Serialises claims, so two can never rotate the id out from under each other. */
    private val claimMutex = Mutex()

    /** Attaches the auth listener. Safe to call more than once. */
    fun start() {
        if (authListener != null) return
        authListener = authService.observeAuthState { uid -> onAuthChanged(uid) }
    }

    /**
     * ### Why a fresh sign-in is ignored here
     *
     * This is the bug that bounced a correct password straight back to the
     * login screen, so it is worth spelling out.
     *
     * Firebase notifies this listener *during* `signInWithEmailAndPassword`,
     * before the call returns to [establishSession]. Handling that callback here
     * meant two paths ran for one sign-in, concurrently: this one attached the
     * profile listener, while [establishSession] was still creating the profile
     * and claiming the session. The listener's first snapshot carried the
     * *previous* device's session id, which never matches this one -- so the
     * device signed itself out four seconds after signing in. It is exactly the
     * ordering bug §8 of the build brief warns about, arrived at by a different
     * route.
     *
     * So the work is split by who owns it:
     *
     *  - **The first callback** is a session Firebase restored on launch. Nobody
     *    else will handle it, so it is handled here -- by *watching only*, never
     *    claiming. See [observeRestoredSession].
     *  - **Every later non-null callback** is a sign-in that [establishSession]
     *    owns end to end. Doing anything here would race it.
     *  - **Null** is a sign-out, whenever it arrives.
     */
    private fun onAuthChanged(uid: String?) {
        val isInitial = !initialAuthStateHandled
        initialAuthStateHandled = true

        if (uid == null) {
            profileJob?.cancel()
            profileJob = null
            watchedUid = null
            _currentUser.value = null
            _profileError.value = null
            _state.value = State.SignedOut
            return
        }

        if (!isInitial) return

        _state.value = State.LoadingProfile
        scope.launch { observeRestoredSession(uid) }
    }

    /**
     * A session restored on launch: watch, and do not claim.
     *
     * Claiming here would be wrong in a way that is easy to miss. Say this phone
     * was signed in, its app was killed, and the member then signed in on a
     * second phone. When this one next launches, the account belongs to the
     * second phone -- and a restore that claimed would silently take it back and
     * kick the device the member is actually holding. Watching instead compares
     * against the id this device last wrote, sees the newer device's id, and
     * signs *this* one out, which is the right way round.
     */
    private suspend fun observeRestoredSession(uid: String) {
        ownSessionIds += deviceIdentity.currentSessionId()
        observeProfile(uid)
    }

    /**
     * Called by the auth flow once a sign-in succeeds, and the sole owner of
     * that sign-in.
     *
     * The order of the last two steps is the whole point, and it is the bug §8
     * of the build brief describes. Attaching the listener first means it fires
     * with whatever `currentSessionId` the *previous* device left behind, which
     * never matches the id this device just generated -- so the device taking
     * over kicks itself out instead. Claiming first makes the first snapshot the
     * listener ever sees already contain this device's own id.
     */
    suspend fun establishSession(auth: AuthResult, country: String? = null) {
        // Run in the application scope and wait for it, rather than running in
        // whatever scope called this.
        //
        // The caller is the sign-in screen's view model. The moment this marks
        // the member signed in, the root gate navigates away from that screen,
        // which destroys the view model and cancels its scope -- so a sign-in run
        // in that scope was cancelled by its own success, part-way through
        // claiming the device, and left the app stuck on a loading state. Setting
        // up a session is not screen work and must not die with a screen.
        scope.async { establishSessionInAppScope(auth, country) }.await()
    }

    private suspend fun establishSessionInAppScope(auth: AuthResult, country: String?) {
        // Stop any watcher left from a previous account before touching this one.
        profileJob?.cancel()
        profileJob = null
        watchedUid = null

        _state.value = State.LoadingProfile
        _profileError.value = null
        _wasKickedByOtherDevice.value = false

        try {
            val user = userRepository.createUserIfNeeded(
                uid = auth.uid,
                email = auth.email,
                fullName = auth.fullName,
                photoUrl = auth.photoUrl,
                provider = auth.provider,
                country = country
            )

            // The profile document exists by now, which matters: the claim is an
            // `update`, and an update against a document that is not there yet
            // -- a brand new account whose create has not landed -- fails.
            claimDeviceSession(auth.uid)

            // Signed in only once the claim has landed. Publishing this earlier
            // lets the gate react -- and navigate, and tear screens down -- while
            // the session is still half set up.
            _currentUser.value = user
            _state.value = State.SignedIn

            observeProfile(auth.uid)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The profile could not be created or read -- in practice, security
            // rules refusing `users/{uid}`. Without this branch the sign-in is
            // thrown away and a correctly-authenticated member is sent back to
            // the login screen, which is indistinguishable from a crash.
            Log.e(TAG, "Could not establish session for ${auth.uid}", e)
            _profileError.value =
                "Signed in, but your profile could not be loaded. Pull to retry."
            _state.value = State.LoadingProfile
        }
    }

    /**
     * Writes this device's claim on the account.
     *
     * Deliberately non-throwing. A failed claim leaves the previous device's id
     * in place, which is a lost takeover -- annoying. A claim that throws out of
     * sign-in is a member who cannot get in at all -- much worse.
     */
    private suspend fun claimDeviceSession(uid: String) = claimMutex.withLock {
        try {
            val sessionId = deviceIdentity.rotateSessionId()
            // Recorded before the write goes out, so a snapshot that races the
            // write back in is already recognised as ours.
            ownSessionIds += sessionId
            userRepository.claimDeviceSession(
                uid = uid,
                sessionId = sessionId,
                deviceId = deviceIdentity.deviceId(),
                deviceModel = deviceIdentity.deviceModel
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not claim the device session; carrying on", e)
        }
    }

    private fun observeProfile(uid: String) {
        profileJob?.cancel()
        watchedUid = uid
        profileJob = scope.launch {
            userRepository.observeUser(uid).collect { snapshot ->
                when (snapshot) {
                    is Snapshot.Failed -> onProfileListenerFailed(snapshot.error)
                    is Snapshot.Data -> onProfileArrived(uid, snapshot.value)
                }
            }
        }
    }

    /**
     * **A failed read is not a takeover.**
     *
     * Single-device enforcement fails open, on purpose. The only thing that may
     * sign a member out is a snapshot that actually arrived and actually carried
     * a session id this device never wrote. A network blip, a permission error,
     * an expired token mid-refresh -- none of those say anything about whether
     * somebody else is using the account.
     *
     * A permission denial is the one worth surfacing, because it means the rules
     * refused this account rather than the network failing.
     */
    private fun onProfileListenerFailed(error: Throwable) {
        Log.w(TAG, "Profile listener failed; keeping the current session", error)

        val denied = (error as? FirebaseFirestoreException)?.code ==
            FirebaseFirestoreException.Code.PERMISSION_DENIED

        if (denied && _currentUser.value == null) {
            _profileError.value = "This account could not be read. Contact support."
            _state.value = State.LoadingProfile
        }
    }

    private suspend fun onProfileArrived(uid: String, user: User?) {
        if (user == null) {
            // The document is genuinely absent -- a first sign-in whose create
            // has not landed yet, or an account deleted from the console.
            if (_currentUser.value == null) _state.value = State.LoadingProfile
            return
        }

        val remoteSessionId = user.currentSessionId

        when {
            // No prior session recorded -- an account that has never claimed, or
            // one whose claim failed earlier. Take it now, off this collector so
            // the snapshot that confirms it can be delivered.
            remoteSessionId.isNullOrBlank() -> scope.launch { claimDeviceSession(uid) }

            // Somebody else took the account. The only path that signs anyone
            // out: a snapshot that arrived, carrying an id this device never
            // generated.
            remoteSessionId !in ownSessionIds -> {
                Log.i(TAG, "Account claimed on another device; ending this session")
                _wasKickedByOtherDevice.value = true
                signOut()
                return
            }
        }

        _currentUser.value = user
        _profileError.value = null
        _state.value = State.SignedIn
    }

    fun acknowledgeKickedOut() {
        _wasKickedByOtherDevice.value = false
    }

    suspend fun refreshProfile() {
        val uid = authService.currentUid ?: return
        runCatching { userRepository.fetchUser(uid) }
            .onSuccess { user ->
                if (user != null) {
                    _currentUser.value = user
                    _profileError.value = null
                    _state.value = State.SignedIn
                }
            }
            .onFailure { Log.w(TAG, "Manual profile refresh failed", it) }
    }

    /**
     * Saves the fields a member owns about themselves.
     *
     * No local mutation on success. The profile listener is already watching
     * this document and delivers the change, which keeps one source of truth
     * instead of two that can disagree if the write is rejected.
     */
    suspend fun updateProfile(
        fullName: String? = null,
        phone: String? = null,
        country: String? = null,
        bio: String? = null,
        profilePhoto: String? = null
    ) {
        val uid = _currentUser.value?.id ?: return
        userRepository.updateProfile(
            uid = uid,
            fullName = fullName,
            phone = phone,
            profilePhoto = profilePhoto,
            country = country,
            bio = bio
        )
    }

    suspend fun registerPushToken(token: String) {
        val uid = _currentUser.value?.id ?: authService.currentUid ?: return
        runCatching { userRepository.registerPushToken(uid, token) }
            .onFailure { Log.w(TAG, "Could not register the push token", it) }
    }

    fun signOut() {
        ownSessionIds.clear()
        // Cancel the profile listener first. The rules deny reads to signed-out
        // clients, so leaving it attached produces a permission error on the way
        // out — which the fail-open branch would then have to reason about.
        profileJob?.cancel()
        profileJob = null
        watchedUid = null
        authService.signOut()
        _currentUser.value = null
        _profileError.value = null
        _state.value = State.SignedOut
    }

    suspend fun deleteAccount() {
        profileJob?.cancel()
        profileJob = null
        watchedUid = null
        authService.deleteAccount()
        _currentUser.value = null
        _state.value = State.SignedOut
    }

    private companion object {
        const val TAG = "SessionRepository"
    }
}
