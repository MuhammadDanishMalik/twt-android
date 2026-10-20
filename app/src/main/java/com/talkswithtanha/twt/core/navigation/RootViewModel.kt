package com.talkswithtanha.twt.core.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.talkswithtanha.twt.core.session.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import com.talkswithtanha.twt.core.session.AuthService
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(
    private val session: SessionRepository,
    private val auth: AuthService,
    private val activity: com.talkswithtanha.twt.core.analytics.ActivityTracker
) : ViewModel() {

    /** Counts a screen view towards today's usage, for the admin panel. */
    fun screenShown(route: String?) = activity.screen(route)

    /**
     * The states the shell can be in, plus "not yet known".
     *
     * Ordered the way a member passes through them: prove who you are, prove
     * you can read the address you gave, then present a code. Email
     * verification belongs here rather than as a push from the sign-up screen —
     * the gate wipes the back stack on every change, so anything the sign-up
     * screen navigated to would be thrown away a frame later.
     */
    enum class Gate {
        Undecided,
        SignedOut,
        NeedsEmailVerification,
        NeedsProfileSetup,
        NeedsAccessCode,

        /** Had a code, and it ran out. This device is signed out — see RootScreen. */
        AccessExpired,
        Allowed
    }

    val gate: StateFlow<Gate> = combine(
        session.state,
        session.currentUser,
        auth.emailVerified
    ) { state, user, emailVerified ->
        when (state) {
            // Deliberately undecided rather than signed-out. Treating a session
            // that is still being restored as signed-out flashes the onboarding
            // screen at every returning member for the fraction of a second it
            // takes Firebase to hand back the cached credential.
            SessionRepository.State.Restoring -> Gate.Undecided
            SessionRepository.State.SignedOut -> Gate.SignedOut
            SessionRepository.State.LoadingProfile -> Gate.Undecided
            SessionRepository.State.SignedIn -> when {
                // Before anything else. An account whose address nobody has
                // proved is an account that cannot be recovered, cannot be
                // contacted, and may belong to somebody who never asked for it.
                //
                // Accounts that predate this check are let through: they were
                // created when the app had no verification step, and locking
                // existing members out of an app they paid for to fix a gap
                // they did not create is not a trade worth making.
                !emailVerified && user?.createdAt?.after(VERIFICATION_REQUIRED_FROM) == true ->
                    Gate.NeedsEmailVerification

                // The derived check, never the raw `membershipType` field --
                // reading the field directly keeps a lapsed member in forever,
                // because nothing rewrites it when the date passes.
                user?.hasAppAccess == true -> Gate.Allowed

                // A few questions about their trading before the code screen,
                // once. Members who already have access never see it — it is
                // for getting to know new people, not a toll on existing ones.
                user?.profileSetupAt == null -> Gate.NeedsProfileSetup

                // A code that has run out signs this device out; a member who
                // has simply not redeemed one yet goes to the gate. Both have
                // `hasAppAccess == false`, so testing that alone would throw
                // every new member off the app in the seconds between signing
                // up and redeeming.
                user?.accessExpired == true -> Gate.AccessExpired

                else -> Gate.NeedsAccessCode
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Gate.Undecided)

    val wasKickedByOtherDevice: StateFlow<Boolean> = session.wasKickedByOtherDevice

    /**
     * Re-reads the account, so a member who just verified moves on.
     *
     * The flag is set server-side with the Admin SDK, which this phone's cached
     * user knows nothing about until it asks.
     */
    fun refreshVerification() {
        viewModelScope.launch { auth.reload() }
    }

    fun acknowledgeKickedOut() = session.acknowledgeKickedOut()

    /**
     * Ends the session on this device when the member's access has expired.
     *
     * The client asked for this: access ending should end the session, not leave
     * the app sitting on the gate with a live login behind it. The account is
     * untouched — a new code signs them straight back in.
     */
    fun signOutForExpiredAccess() = session.signOut()
}

/**
 * Accounts created before this instant skip the email check.
 *
 * The date the verification step shipped. Everyone who signed up earlier was
 * never asked, and this is the line that stops a new requirement from locking
 * them out of an app they already have access to.
 */
internal val VERIFICATION_REQUIRED_FROM = Date(1_790_136_000_000L) // 2026-09-23T04:00:00Z
