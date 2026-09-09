package com.talkswithtanha.twt.core.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.talkswithtanha.twt.core.session.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(
    private val session: SessionRepository
) : ViewModel() {

    /** The three states the shell can be in, plus "not yet known". */
    enum class Gate { Undecided, SignedOut, NeedsAccessCode, Allowed }

    val gate: StateFlow<Gate> = combine(
        session.state,
        session.currentUser
    ) { state, user ->
        when (state) {
            // Deliberately undecided rather than signed-out. Treating a session
            // that is still being restored as signed-out flashes the onboarding
            // screen at every returning member for the fraction of a second it
            // takes Firebase to hand back the cached credential.
            SessionRepository.State.Restoring -> Gate.Undecided
            SessionRepository.State.SignedOut -> Gate.SignedOut
            SessionRepository.State.LoadingProfile -> Gate.Undecided
            SessionRepository.State.SignedIn ->
                // The derived check, never the raw `membershipType` field --
                // reading the field directly keeps a lapsed member in forever,
                // because nothing rewrites it when the date passes.
                if (user?.hasAppAccess == true) Gate.Allowed else Gate.NeedsAccessCode
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Gate.Undecided)

    val wasKickedByOtherDevice: StateFlow<Boolean> = session.wasKickedByOtherDevice

    fun acknowledgeKickedOut() = session.acknowledgeKickedOut()
}
