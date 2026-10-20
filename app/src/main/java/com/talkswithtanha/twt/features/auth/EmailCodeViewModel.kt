package com.talkswithtanha.twt.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.talkswithtanha.twt.core.session.ApiResult
import com.talkswithtanha.twt.core.session.AuthApi
import com.talkswithtanha.twt.core.session.AuthError
import com.talkswithtanha.twt.core.session.AuthException
import com.talkswithtanha.twt.core.session.AuthService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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

/**
 * The wait between Firebase verification links. Matches iOS; Firebase throttles
 * repeated sends on its own, so a longer wait here only slows honest members.
 */
private const val LINK_RESEND_SECONDS = 30

/**
 * How an address gets confirmed in this build.
 *
 * [CODE] needs the admin app deployed with a Resend key, and its URL compiled
 * in as `adminApiBase`. Without that every send fails, and a new member is held
 * at the verification gate with no way through. [LINK] is Firebase's own
 * verification email, the same one iOS sends, and needs no server of ours.
 */
enum class VerifyMethod { CODE, LINK }

data class EmailCodeUiState(
    val method: VerifyMethod = VerifyMethod.CODE,
    val code: String = "",
    val isSubmitting: Boolean = false,
    val isSending: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val secondsUntilResend: Int = 0,
    val isDone: Boolean = false
) {
    val canSubmit: Boolean
        get() = !isSubmitting && (method == VerifyMethod.LINK || code.length == CODE_LENGTH)
    val canResend: Boolean get() = secondsUntilResend == 0 && !isSending && !isSubmitting
}

/**
 * Confirming an email address, by code or by link.
 *
 * The account already exists by the time this screen appears — it was created
 * on the phone, so Firebase Auth holds the password and the server never sees
 * it. All that is settled here is whether the member can read the inbox they
 * signed up with. See [VerifyMethod] for which way that happens.
 */
@HiltViewModel
class VerifyEmailViewModel @Inject constructor(
    private val api: AuthApi,
    private val auth: AuthService
) : ViewModel() {

    private val method = if (api.isConfigured) VerifyMethod.CODE else VerifyMethod.LINK

    private val _state = MutableStateFlow(EmailCodeUiState(method = method))
    val state: StateFlow<EmailCodeUiState> = _state.asStateFlow()

    /** The address the email went to, for the "we sent it to…" line. */
    val email: String? get() = auth.currentEmail

    private var countdown: Job? = null

    init {
        // Sent on arrival rather than behind a button: the member has just
        // finished a sign-up form and is already waiting for this.
        send(onArrival = true)
    }

    fun onCodeChange(value: String) {
        val digits = value.filter { it.isDigit() }.take(CODE_LENGTH)
        _state.update { it.copy(code = digits, error = null) }
        // Six digits can only mean one thing, so asking them to press a button
        // as well is a step that exists for the app's benefit, not theirs.
        if (digits.length == CODE_LENGTH) submit()
    }

    fun send() = send(onArrival = false)

    private fun send(onArrival: Boolean) {
        if (_state.value.isSending) return
        _state.update { it.copy(isSending = true, error = null) }

        viewModelScope.launch {
            when (method) {
                VerifyMethod.CODE -> sendCode()
                VerifyMethod.LINK -> sendLink(onArrival)
            }
        }
    }

    private suspend fun sendCode() {
        val token = auth.idToken()
        if (token == null) {
            _state.update { it.copy(isSending = false, error = "Sign in again.") }
            return
        }
        when (val result = api.sendVerificationCode(token)) {
            is ApiResult.Ok -> {
                // The server answers OK without mailing anything when the
                // address is already confirmed (on the website, say). Then
                // there is nothing to type: walk straight in.
                if (auth.reload()) {
                    _state.update { it.copy(isSending = false, isDone = true) }
                    return
                }
                _state.update {
                    it.copy(
                        isSending = false,
                        notice = "Code sent to ${auth.currentEmail ?: "your inbox"}. Check Spam if it isn't there in a minute."
                    )
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

    private suspend fun sendLink(onArrival: Boolean) {
        // A member reopening the app after tapping the link should walk
        // straight in, not be mailed a second link they no longer need.
        if (onArrival && auth.reload()) {
            _state.update { it.copy(isSending = false, isDone = true) }
            return
        }
        try {
            auth.sendEmailVerification()
            _state.update { it.copy(isSending = false, notice = "Link sent. Check your inbox.") }
            startResendCountdown(LINK_RESEND_SECONDS)
        } catch (e: AuthException) {
            // Throttled on arrival means a link went out moments ago — at
            // sign-up, or on the last visit to this screen. That link still
            // works, so the member is told nothing and simply waits to resend.
            val throttled = e.reason == AuthError.TooManyRequests
            _state.update {
                it.copy(
                    isSending = false,
                    error = if (throttled && onArrival) null else e.reason.message
                )
            }
            if (throttled) startResendCountdown(RESEND_SECONDS)
        }
    }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(isSubmitting = true, error = null) }

        viewModelScope.launch {
            when (method) {
                VerifyMethod.CODE -> submitCode(current.code)
                VerifyMethod.LINK -> {
                    val verified = auth.reload()
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            isDone = verified,
                            error = if (verified) null
                            else "Not verified yet. Tap the link in your email, then try again."
                        )
                    }
                }
            }
        }
    }

    private suspend fun submitCode(code: String) {
        val token = auth.idToken()
        if (token == null) {
            _state.update { it.copy(isSubmitting = false, error = "Sign in again.") }
            return
        }
        when (val result = api.verifyEmail(token, code)) {
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

    /**
     * A silent check, for when the member comes back to the app.
     *
     * With a link, the natural path is: open the mail app, tap, switch back.
     * Making them also press "I've verified" after that is a step for the app's
     * benefit. Quiet on failure — they may simply not have tapped it yet.
     */
    fun recheckQuietly() {
        if (method != VerifyMethod.LINK) return
        val current = _state.value
        if (current.isSubmitting || current.isSending || current.isDone) return
        viewModelScope.launch {
            if (auth.reload()) _state.update { it.copy(isDone = true, error = null) }
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
        // One countdown at a time. Two running together fight over the number
        // and the member watches it jump.
        countdown?.cancel()
        countdown = viewModelScope.launch {
            for (remaining in seconds downTo 0) {
                _state.update { it.copy(secondsUntilResend = remaining) }
                if (remaining > 0) delay(1000)
            }
        }
    }
}

/**
 * Which step of the reset a member is on.
 *
 * [LINK_SENT] is the whole flow when email codes are not configured: Firebase
 * mails a reset link and the new password is chosen in the browser, as on iOS.
 */
enum class ResetStage { EMAIL, CODE, PASSWORD, DONE, LINK_SENT }

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
        // The same minimum as a new account. The server accepts six, so this
        // is stricter, never looser.
        get() = password.length >= MIN_NEW_PASSWORD_LENGTH && passwordsMatch && !isSubmitting
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
    private val api: AuthApi,
    private val auth: AuthService
) : ViewModel() {

    private var countdown: Job? = null

    /** True when email codes are not configured and Firebase's link is used. */
    val usesLink: Boolean get() = !api.isConfigured

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
            ResetStage.LINK_SENT -> it.copy(stage = ResetStage.EMAIL, error = null)
            else -> it
        }
    }

    fun sendCode() {
        val current = _state.value
        if (!current.canSendCode) return
        _state.update { it.copy(isSubmitting = true, error = null) }

        if (usesLink) {
            sendLink(current.email)
            return
        }

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

    private fun sendLink(email: String) {
        viewModelScope.launch {
            val error = try {
                auth.sendPasswordReset(email)
                null
            } catch (e: AuthException) {
                when (e.reason) {
                    // No such account. Answered exactly as though there were
                    // one, for the same reason the code route does.
                    AuthError.InvalidCredentials -> null
                    else -> e.reason.message
                }
            }
            _state.update {
                if (error == null) it.copy(isSubmitting = false, stage = ResetStage.LINK_SENT)
                else it.copy(isSubmitting = false, error = error)
            }
            if (error == null) startResendCountdown(RESEND_SECONDS)
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
        countdown?.cancel()
        countdown = viewModelScope.launch {
            for (remaining in seconds downTo 0) {
                _state.update { it.copy(secondsUntilResend = remaining) }
                if (remaining > 0) delay(1000)
            }
        }
    }
}
