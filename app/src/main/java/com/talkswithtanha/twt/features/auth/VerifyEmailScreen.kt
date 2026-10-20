package com.talkswithtanha.twt.features.auth

import com.talkswithtanha.twt.core.designsystem.readableWidth
import com.talkswithtanha.twt.core.designsystem.WebColors
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalHapticFeedback
import com.talkswithtanha.twt.core.designsystem.Haptics
import com.talkswithtanha.twt.core.designsystem.IosColors

/**
 * Confirming the address, with the code or link that was just emailed.
 *
 * The account already exists — it was created on the previous screen, and
 * Firebase Auth holds the password. What is missing is proof that the member
 * can read the inbox they typed, which is the only thing standing between a
 * real sign-up and somebody typing a stranger's address.
 *
 * Which of the two depends on the build — see [VerifyMethod]. With a link there
 * is nothing to type: the member taps it in their mail app, and the check runs
 * again by itself when they switch back here.
 *
 * There is a way out. A member who mistyped their address has to be able to go
 * back and fix it, and a screen with no exit is where sign-ups die.
 */
@Composable
fun VerifyEmailScreen(
    onVerified: () -> Unit = {},
    viewModel: VerifyEmailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current

    // The two outcomes that matter on this screen, felt rather than read. A
    // member watching the keyboard rather than the screen still knows.
    LaunchedEffect(state.isDone) {
        if (state.isDone) {
            Haptics.success(haptics)
            onVerified()
        }
    }
    LaunchedEffect(state.error) {
        if (state.error != null) Haptics.failure(haptics)
    }
    // The system back gesture takes the same exit as the arrow, rather than
    // closing the app with a half-finished, signed-in account behind it.
    BackHandler { viewModel.abandon() }
    LifecycleResumeEffect(Unit) {
        viewModel.recheckQuietly()
        onPauseOrDispose {}
    }
    val isLink = state.method == VerifyMethod.LINK

    Column(
        Modifier
            .fillMaxSize()
            .background(WebColors.Ink)
            .readableWidth()
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
                .clickable { viewModel.abandon() },
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
                .background(WebColors.Brand.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.MarkEmailRead,
                contentDescription = null,
                tint = WebColors.Brand,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(Modifier.height(20.dp))

        val sentWhat = if (isLink) "a verification link" else "a six-digit code"
        AuthHeading(
            title = "Check your email",
            subtitle = (viewModel.email?.let { "We sent $sentWhat to $it." }
                ?: "We sent $sentWhat to your email.") +
                if (isLink) " Tap it, then come back here." else ""
        )

        Spacer(Modifier.height(28.dp))

        if (!isLink) {
            CodeInput(
                code = state.code,
                length = CODE_LENGTH,
                enabled = !state.isSubmitting,
                onCodeChange = viewModel::onCodeChange,
                modifier = Modifier.fillMaxWidth()
            )
        }

        AuthError(state.error)

        Spacer(Modifier.height(24.dp))

        AuthPrimaryButton(
            text = if (isLink) "I've verified — continue" else "Verify and continue",
            enabled = state.canSubmit,
            loading = state.isSubmitting,
            onClick = viewModel::submit
        )

        Spacer(Modifier.height(20.dp))

        ResendRow(
            secondsRemaining = state.secondsUntilResend,
            enabled = state.canResend,
            onResend = viewModel::send
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = (if (isLink) "" else "The code expires in 10 minutes. ") +
                "Check your spam folder if it has not arrived after a minute.",
            color = Color.White.copy(alpha = 0.3f),
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Wrong address? Sign out and start again.",
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable { viewModel.abandon() }
        )

        Spacer(Modifier.height(40.dp))
    }
}
