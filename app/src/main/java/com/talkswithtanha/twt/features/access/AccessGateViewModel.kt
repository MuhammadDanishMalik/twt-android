package com.talkswithtanha.twt.features.access

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.talkswithtanha.twt.core.data.AccessTokenRepository
import com.talkswithtanha.twt.core.data.SupportConfigRepository
import com.talkswithtanha.twt.core.model.AccessCode
import com.talkswithtanha.twt.core.model.AccessRedemptionException
import com.talkswithtanha.twt.core.model.SupportConfig
import com.talkswithtanha.twt.core.model.User
import com.talkswithtanha.twt.core.session.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccessGateUiState(
    val code: String = "",
    val isRedeeming: Boolean = false,
    val error: String? = null,
    val redeemed: Boolean = false
) {
    /** The normalised form is what gets looked up, so it is what is validated. */
    val canSubmit: Boolean
        get() = !isRedeeming && AccessCode.isWellFormed(AccessCode.normalise(code))
}

@HiltViewModel
class AccessGateViewModel @Inject constructor(
    private val accessTokens: AccessTokenRepository,
    private val session: SessionRepository,
    supportConfig: SupportConfigRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AccessGateUiState())
    val state: StateFlow<AccessGateUiState> = _state.asStateFlow()

    val user: StateFlow<User?> = session.currentUser

    val support: StateFlow<SupportConfig> = supportConfig.observeConfig()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SupportConfig())

    fun onCodeChange(value: String) {
        // Typed freely, normalised at the edge. Somebody pasting
        // "TWT-4H2K-9XQP" out of WhatsApp should not have to strip the dashes,
        // and somebody typing lowercase should not be told they are wrong.
        _state.update { it.copy(code = value.uppercase(), error = null) }
    }

    fun redeem() {
        val current = _state.value
        if (!current.canSubmit) return

        val user = session.currentUser.value ?: return

        _state.update { it.copy(isRedeeming = true, error = null) }

        viewModelScope.launch {
            try {
                accessTokens.redeem(
                    code = current.code,
                    uid = user.id,
                    email = user.email
                )
                // Nothing navigates from here. The profile listener delivers the
                // new membership and `RootViewModel` moves the stack -- which is
                // the same path a member takes when Tanha grants access from the
                // admin panel while they are looking at this screen.
                _state.update { it.copy(isRedeeming = false, redeemed = true) }
            } catch (e: AccessRedemptionException) {
                _state.update { it.copy(isRedeeming = false, error = e.reason.message) }
            }
        }
    }

    fun signOut() = session.signOut()
}
