package com.talkswithtanha.twt.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.talkswithtanha.twt.core.session.AuthException
import com.talkswithtanha.twt.core.session.AuthService
import com.talkswithtanha.twt.core.session.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val fullName: String = "",
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val notice: String? = null
) {
    val canSignIn: Boolean
        get() = email.isNotBlank() && password.length >= 6 && !isSubmitting

    val canSignUp: Boolean
        get() = canSignIn && fullName.isNotBlank()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authService: AuthService,
    private val session: SessionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) =
        _state.update { it.copy(email = value, error = null) }

    fun onPasswordChange(value: String) =
        _state.update { it.copy(password = value, error = null) }

    fun onFullNameChange(value: String) =
        _state.update { it.copy(fullName = value, error = null) }

    fun signIn() = submit {
        val result = authService.signIn(_state.value.email, _state.value.password)
        session.establishSession(result)
    }

    fun signUp(country: String?) = submit {
        val result = authService.signUp(
            email = _state.value.email,
            password = _state.value.password,
            fullName = _state.value.fullName.trim()
        )
        session.establishSession(result, country)
    }

    /** [idToken] comes from Credential Manager — see [GoogleSignIn]. */
    fun signInWithGoogle(idToken: String, country: String?) = submit {
        val result = authService.signInWithGoogle(idToken)
        session.establishSession(result, country)
    }

    fun sendPasswordReset() {
        val email = _state.value.email
        if (email.isBlank()) {
            _state.update { it.copy(error = "Enter your email address first.") }
            return
        }
        viewModelScope.launch {
            try {
                authService.sendPasswordReset(email)
                _state.update {
                    it.copy(notice = "If that address has an account, a reset link is on its way.")
                }
            } catch (e: AuthException) {
                _state.update { it.copy(error = e.reason.message) }
            }
        }
    }

    fun onGoogleSignInFailed(message: String) =
        _state.update { it.copy(isSubmitting = false, error = message) }

    fun setSubmitting(value: Boolean) = _state.update { it.copy(isSubmitting = value) }

    fun dismissNotice() = _state.update { it.copy(notice = null, error = null) }

    private fun submit(block: suspend () -> Unit) {
        if (_state.value.isSubmitting) return
        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            try {
                block()
                // No navigation from here. `RootViewModel` is watching the
                // session and moves the whole stack once the profile arrives --
                // which is also what routes a member with no code to the access
                // gate rather than the home screen.
                _state.update { it.copy(isSubmitting = false) }
            } catch (e: AuthException) {
                _state.update { it.copy(isSubmitting = false, error = e.reason.message) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        error = e.localizedMessage ?: "Something went wrong."
                    )
                }
            }
        }
    }
}
