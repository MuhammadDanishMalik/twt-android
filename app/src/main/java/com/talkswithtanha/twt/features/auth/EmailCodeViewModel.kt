package com.talkswithtanha.twt.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.talkswithtanha.twt.core.session.ApiResult
import com.talkswithtanha.twt.core.session.AuthApi
import com.talkswithtanha.twt.core.session.AuthService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Digits in a code. Also the point at which the screen submits by itself. */
const val CODE_LENGTH = 6

/** How long a member waits before they may ask for another code. */
private const val RESEND_SECONDS = 60

data class EmailCodeUiState(
    val code: String = "",
    val isSubmitting: Boolean = false,
    val isSending: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val secondsUntilResend: Int = 0,
    val isDone: Boolean = false
) {
    val canSubmit: Boolean get() = code.length == CODE_LENGTH && !isSubmitting
    val canResend: Boolean get() = secondsUntilResend == 0 && !isSending && !isSubmitting
}

/**
 * Confirming an email address with a six-digit code.
 *
 * The account already exists by the time this screen appears — it was created
 * on the phone, so Firebase Auth holds the password and the server never sees
 * it. All that is settled here is whether the member can read the inbox they
 * signed up with.
 */
@HiltViewModel
class VerifyEmailViewModel @Inject constructor(
    private val api: AuthApi,
    private val auth: AuthService
) : ViewModel() {

    private val _state = MutableStateFlow(EmailCodeUiState())
    val state: StateFlow<EmailCodeUiState> = _state.asStateFlow()

    /** The address the code went to, for the "we sent it to…" line. */
    val email: String? get() = auth.currentEmail

    init {
        // Sent on arrival rather than behind a button: the member has just
        // finished a sign-up form and is already waiting for this.
        send()
    }

    fun onCodeChange(value: String) {
        val digits = value.filter { it.isDigit() }.take(CODE_LENGTH)
        _state.update { it.copy(code = digits, error = null) }
        // Six digits can only mean one thing, so asking them to press a button
        // as well is a step that exists for the app's benefit, not theirs.
        if (digits.length == CODE_LENGTH) submit()
    }

    fun send() {
        if (_state.value.isSending) return
        _state.update { it.copy(isSending = true, error = null) }

        viewModelScope.launch {
            val token = auth.idToken()
            if (token == null) {
                _state.update { it.copy(isSending = false, error = "Sign in again.") }
                return@launch
            }
            when (val result = api.sendVerificationCode(token)) {
                is ApiResult.Ok -> {
                    _state.update {
                        it.copy(isSending = false, notice = "Code sent. Check your inbox.")
                    }
                    startResendCountdown(RESEND_SECONDS)
                }
                is ApiResult.Failed -> {
                    _state.update { it.copy(isSending = false, error = result.message) }
                    // The server knows how long is left better than the phone
                    // does, so its number wins when it gives one.
                    result.retryAfterSeconds?.let { startResendCountdown(it) }
                }
            }
        }
    }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(isSubmitting = true, error = null) }

        viewModelScope.launch {
            val token = auth.idToken()
            if (token == null) {
                _state.update { it.copy(isSubmitting = false, error = "Sign in again.") }
                return@launch
            }
            when (val result = api.verifyEmail(token, current.code)) {
                is ApiResult.Ok -> {
                    // The Admin SDK set the flag; this phone's cached user has
                    // no idea until it asks again.
                    auth.reload()
                    _state.update { it.copy(isSubmitting = false, isDone = true) }
                }
                is ApiResult.Failed -> _state.update {
                    // Cleared, because a rejected code is never worth resending
                    // and leaving it there invites exactly that.
                    it.copy(isSubmitting = false, error = result.message, code = "")
                }
            }
        }
    }

    fun dismissNotice() = _state.update { it.copy(notice = null, error = null) }

    /**
     * The way out for somebody who mistyped their address.
     *
     * Signing out rather than going back, because there is nothing to go back
     * to: the account exists, it is signed in, and the address on it is wrong.
     * It has to be abandoned and started again.
     */
    fun abandon() = auth.signOut()

    private fun startResendCountdown(seconds: Int) {
        viewModelScope.launch {
            for (remaining in seconds downTo 0) {
                _state.update { it.copy(secondsUntilResend = remaining) }
                if (remaining > 0) delay(1000)
            }
        }
    }
}

/** Which step of the reset a member is on. */
enum class ResetStage { EMAIL, CODE, PASSWORD, DONE }

data class ResetUiState(
    val stage: ResetStage = ResetStage.EMAIL,
    val email: String = "",
    val code: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val secondsUntilResend: Int = 0
) {
    val canSendCode: Boolean get() = email.contains("@") && email.length > 4 && !isSubmitting
    val canSubmitCode: Boolean get() = code.length == CODE_LENGTH && !isSubmitting
    val passwordsMatch: Boolean get() = password == confirmPassword
    val canSetPassword: Boolean
        get() = password.length >= 6 && passwordsMatch && !isSubmitting
    val canResend: Boolean get() = secondsUntilResend == 0 && !isSubmitting
}

/**
 * Recovering an account through the inbox.
 *
 * The code is checked and the password is set in one request, at the end. The
 * alternative — verify the code, then send the password separately — means the
 * server has to remember that this person is halfway through, which is one more
 * piece of state to get wrong and one more window for somebody else to walk
 * through.
 */
@HiltViewModel
class ResetPasswordViewModel @Inject constructor(
    private val api: AuthApi
) : ViewModel() {

    private val _state = MutableStateFlow(ResetUiState())
    val state: StateFlow<ResetUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) =
        _state.update { it.copy(email = value.trim(), error = null) }

    fun onCodeChange(value: String) =
        _state.update { it.copy(code = value.filter(Char::isDigit).take(CODE_LENGTH), error = null) }

    fun onPasswordChange(value: String) =
        _state.update { it.copy(password = value, error = null) }

    fun onConfirmPasswordChange(value: String) =
        _state.update { it.copy(confirmPassword = value, error = null) }

    /** Steps back one stage, so the member can fix a mistyped address. */
    fun back() = _state.update {
        when (it.stage) {
            ResetStage.CODE -> it.copy(stage = ResetStage.EMAIL, code = "", error = null)
            ResetStage.PASSWORD -> it.copy(stage = ResetStage.CODE, error = null)
            else -> it
        }
    }

    fun sendCode() {
        val current = _state.value
        if (!current.canSendCode) return
        _state.update { it.copy(isSubmitting = true, error = null) }

        viewModelScope.launch {
            when (val result = api.sendResetCode(current.email)) {
                is ApiResult.Ok -> {
                    // Advances even when no account exists. The server answers
                    // the same either way on purpose — a screen that said "no
                    // such account" would be a tool for finding out who has one.
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            stage = ResetStage.CODE,
                            notice = "If that address has an account, a code is on its way."
                        )
                    }
                    startResendCountdown(RESEND_SECONDS)
                }
                is ApiResult.Failed -> {
                    _state.update { it.copy(isSubmitting = false, error = result.message) }
                    result.retryAfterSeconds?.let { startResendCountdown(it) }
                }
            }
        }
    }

    /** The code is not checked here — it is checked when the password is set. */
    fun continueToPassword() {
        if (!_state.value.canSubmitCode) return
        _state.update { it.copy(stage = ResetStage.PASSWORD, error = null) }
    }

    fun setPassword() {
        val current = _state.value
        if (!current.canSetPassword) return
        _state.update { it.copy(isSubmitting = true, error = null) }

        viewModelScope.launch {
            when (val result = api.resetPassword(current.email, current.code, current.password)) {
                is ApiResult.Ok -> _state.update {
                    it.copy(isSubmitting = false, stage = ResetStage.DONE)
                }
                is ApiResult.Failed -> _state.update {
                    // A bad code surfaces here rather than a screen earlier, so
                    // send them back to it rather than leaving them staring at
                    // a password field that is not the problem.
                    val badCode = result.message.contains("code", ignoreCase = true)
                    it.copy(
                        isSubmitting = false,
                        error = result.message,
                        stage = if (badCode) ResetStage.CODE else it.stage,
                        code = if (badCode) "" else it.code
                    )
                }
            }
        }
    }

    fun dismissNotice() = _state.update { it.copy(notice = null, error = null) }

    private fun startResendCountdown(seconds: Int) {
        viewModelScope.launch {
            for (remaining in seconds downTo 0) {
                _state.update { it.copy(secondsUntilResend = remaining) }
                if (remaining > 0) delay(1000)
            }
        }
    }
}
