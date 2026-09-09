package com.talkswithtanha.twt.core.session

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestoreException
import com.talkswithtanha.twt.core.data.Snapshot
import com.talkswithtanha.twt.core.data.UserRepository
import com.talkswithtanha.twt.core.model.LoginProvider
import com.talkswithtanha.twt.core.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

    /** Attaches the auth listener. Safe to call more than once. */
    fun start() {
        if (authListener != null) return
        authListener = authService.observeAuthState { uid -> onAuthChanged(uid) }
    }

    private fun onAuthChanged(uid: String?) {
        if (uid == null) {
            profileJob?.cancel()
            profileJob = null
            watchedUid = null
            _currentUser.value = null
            _profileError.value = null
            _state.value = State.SignedOut
            return
        }

        // Already watching this account. Re-attaching would tear down a healthy
        // listener and re-run the whole first-snapshot path for nothing.
        if (uid == watchedUid && profileJob?.isActive == true) return

        if (_currentUser.value == null) _state.value = State.LoadingProfile

        // A restored session on cold start. The device already holds a session
        // id from last time, so it claims the account again before watching —
        // same ordering as a fresh sign-in, and for the same reason.
        scope.launch {
            claimDeviceSession(uid)
            observeProfile(uid)
        }
    }

    /**
     * Called by the auth flow once a sign-in succeeds.
     *
     * The order of the last two steps is the whole point, and it is the bug §8
     * of the build brief describes.
     *
     * Attaching the listener first means it fires with whatever
     * `currentSessionId` the *previous* device left behind, which will never
     * match the id this device just generated — so the watcher concludes the
     * account is in use elsewhere and signs this device out, moments after it
     * successfully signed in. The device taking over kicks itself out instead of
     * taking over, and from the outside that is a login that bounces straight
     * back to the splash screen.
     *
     * Claiming first makes the first snapshot the listener ever sees already
     * contain this device's own id.
     */
    suspend fun establishSession(auth: AuthResult, country: String? = null) {
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
            _currentUser.value = user
            _state.value = State.SignedIn

            claimDeviceSession(auth.uid)
            observeProfile(auth.uid)
        } catch (e: Exception) {
            // The profile could not be created or read — in practice, security
            // rules refusing `users/{uid}`. Without this branch the sign-in is
            // thrown away: `currentUser` stays null and the caller sends a
            // correctly-authenticated member back to the login screen, which is
            // indistinguishable from a crash from the outside.
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
     * in place, which is a lost takeover — annoying. A claim that throws out of
     * sign-in is a member who cannot get in at all — much worse.
     */
    private suspend fun claimDeviceSession(uid: String) {
        try {
            userRepository.claimDeviceSession(
                uid = uid,
                sessionId = deviceIdentity.rotateSessionId(),
                deviceId = deviceIdentity.deviceId(),
                deviceModel = deviceIdentity.deviceModel
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not claim the device session; carrying on", e)
        }
    }

    private fun observeProfile(uid: String) {
        profileJob?.cancel()
        watchedUid = uid
        profileJob = scope.launch {
            val localSessionId = deviceIdentity.currentSessionId()

            userRepository.observeUser(uid).collect { snapshot ->
                when (snapshot) {
                    is Snapshot.Failed -> onProfileListenerFailed(snapshot.error)
                    is Snapshot.Data -> onProfileArrived(uid, snapshot.value, localSessionId)
                }
            }
        }
    }

    /**
     * **A failed read is not a takeover.**
     *
     * Single-device enforcement fails open, on purpose. The only thing that may
     * sign a member out is a snapshot that actually arrived and actually carried
     * a different session id. A network blip, a permission error, an expired
     * token mid-refresh — none of those say anything about whether somebody else
     * is using the account, and treating them as evidence logs a member out on a
     * patchy connection for no reason.
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
        // Otherwise: say nothing, change nothing. The listener retries on its
        // own, and whatever profile is already in hand stays valid.
    }

    private fun onProfileArrived(uid: String, user: User?, localSessionId: String) {
        if (user == null) {
            // The document is genuinely absent — a first sign-in whose create
            // has not landed yet, or an account deleted from the console.
            if (_currentUser.value == null) _state.value = State.LoadingProfile
            return
        }

        val remoteSessionId = user.currentSessionId

        when {
            // Somebody else took the account. This is the only path that signs
            // anyone out: a snapshot that arrived, with a non-empty id, that is
            // not ours.
            !remoteSessionId.isNullOrBlank() && remoteSessionId != localSessionId -> {
                Log.i(TAG, "Account claimed on another device; ending this session")
                _wasKickedByOtherDevice.value = true
                signOut()
                return
            }

            // No prior session recorded — an account that has never claimed, or
            // one whose claim failed earlier. Take it now.
            remoteSessionId.isNullOrBlank() -> {
                scope.launch { claimDeviceSession(uid) }
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
        bio: String? = null
    ) {
        val uid = _currentUser.value?.id ?: return
        userRepository.updateProfile(
            uid = uid,
            fullName = fullName,
            phone = phone,
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
