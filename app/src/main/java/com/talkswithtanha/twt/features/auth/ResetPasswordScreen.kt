package com.talkswithtanha.twt.features.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.designsystem.Motion

/**
 * Getting back into an account through the inbox.
 *
 * Three steps on one screen rather than three destinations: address, code, new
 * password. The back stack would otherwise let somebody return to a code screen
 * whose code has already been spent, and the stage is a property of the flow
 * rather than a place.
 *
 * The address step always advances, whether or not an account exists. The
 * server answers the same either way on purpose — a screen that said "no such
 * account" would be a tool for discovering who banks here.
 */
@Composable
fun ResetPasswordScreen(
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: ResetPasswordViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(IosColors.Background)
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(AuthSurface)
                .clickable {
                    if (state.stage == ResetStage.EMAIL) onBack() else viewModel.back()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(Modifier.height(28.dp))

        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(IosColors.Accent.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.LockReset,
                contentDescription = null,
                tint = IosColors.Accent,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(Modifier.height(20.dp))

        // Steps slide rather than cut, so it reads as one form advancing
        // instead of three screens that happen to look alike.
        AnimatedContent(
            targetState = state.stage,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                val width = if (forward) 1 else -1
                (slideInHorizontally(Motion.gentle()) { w -> width * w } + fadeIn(Motion.quick()))
                    .togetherWith(
                        slideOutHorizontally(Motion.gentle()) { w -> -width * w } +
                            fadeOut(Motion.quick())
                    )
                    .using(SizeTransform(clip = false))
            },
            label = "stage"
        ) { stage ->
            when (stage) {
                ResetStage.EMAIL -> EmailStep(state, viewModel)
                ResetStage.CODE -> CodeStep(state, viewModel)
                ResetStage.PASSWORD -> PasswordStep(state, viewModel)
                ResetStage.DONE -> DoneStep(onDone)
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun EmailStep(state: ResetUiState, viewModel: ResetPasswordViewModel) {
    Column {
        AuthHeading(
            title = "Reset your password",
            subtitle = "Enter the email you signed up with and we'll send you a code."
        )
        Spacer(Modifier.height(24.dp))
        AuthTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            label = "Email",
            keyboardType = KeyboardType.Email
        )
        AuthError(state.error)
        Spacer(Modifier.height(24.dp))
        AuthPrimaryButton(
            text = "Send code",
            enabled = state.canSendCode,
            loading = state.isSubmitting,
            onClick = viewModel::sendCode
        )
    }
}

@Composable
private fun CodeStep(state: ResetUiState, viewModel: ResetPasswordViewModel) {
    Column {
        AuthHeading(
            title = "Enter the code",
            subtitle = "If ${state.email} has an account, a six-digit code is on its way."
        )
        Spacer(Modifier.height(24.dp))
        CodeInput(
            code = state.code,
            length = CODE_LENGTH,
            enabled = !state.isSubmitting,
            onCodeChange = viewModel::onCodeChange,
            modifier = Modifier.fillMaxWidth()
        )
        AuthError(state.error)
        Spacer(Modifier.height(24.dp))
        AuthPrimaryButton(
            text = "Continue",
            enabled = state.canSubmitCode,
            loading = false,
            onClick = viewModel::continueToPassword
        )
        Spacer(Modifier.height(20.dp))
        ResendRow(
            secondsRemaining = state.secondsUntilResend,
            enabled = state.canResend,
            onResend = viewModel::sendCode
        )
    }
}

@Composable
private fun PasswordStep(state: ResetUiState, viewModel: ResetPasswordViewModel) {
    var visible by remember { mutableStateOf(false) }

    Column {
        AuthHeading(
            title = "Choose a new password",
            subtitle = "At least six characters. You'll be signed out everywhere else."
        )
        Spacer(Modifier.height(24.dp))
        AuthTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = "New password",
            keyboardType = KeyboardType.Password,
            isPassword = true,
            passwordVisible = visible,
            onTogglePasswordVisibility = { visible = !visible }
        )
        Spacer(Modifier.height(12.dp))
        AuthTextField(
            value = state.confirmPassword,
            onValueChange = viewModel::onConfirmPasswordChange,
            label = "Confirm password",
            keyboardType = KeyboardType.Password,
            isPassword = true,
            passwordVisible = visible,
            onTogglePasswordVisibility = { visible = !visible }
        )

        // Only once they have typed enough to have made the mistake.
        if (state.confirmPassword.isNotEmpty() && !state.passwordsMatch) {
            AuthError("Those passwords do not match.")
        } else {
            AuthError(state.error)
        }

        Spacer(Modifier.height(24.dp))
        AuthPrimaryButton(
            text = "Set new password",
            enabled = state.canSetPassword,
            loading = state.isSubmitting,
            onClick = viewModel::setPassword
        )
    }
}

@Composable
private fun DoneStep(onDone: () -> Unit) {
    Column {
        AuthSuccess(
            title = "Password changed",
            message = "You can sign in with your new password now."
        )
        Spacer(Modifier.height(28.dp))
        AuthPrimaryButton(text = "Back to sign in", enabled = true, onClick = onDone)
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Every other device has been signed out.",
            color = Color.White.copy(alpha = 0.3f),
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
