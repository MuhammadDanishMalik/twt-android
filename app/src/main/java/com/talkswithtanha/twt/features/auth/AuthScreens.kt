package com.talkswithtanha.twt.features.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.talkswithtanha.twt.core.designsystem.Spacing
import com.talkswithtanha.twt.core.designsystem.TwtColors
import com.talkswithtanha.twt.core.designsystem.components.TwtButton
import com.talkswithtanha.twt.core.designsystem.components.TwtScreen
import com.talkswithtanha.twt.core.designsystem.components.TwtSecondaryButton
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Sign in, and create an account, as one screen in two modes.
 *
 * The build brief describes a five-step email flow on iOS — email, a six-digit
 * code, mobile, a passkey pitch, then profile. Steps two and four are not built
 * on iOS either: Firebase emails a *link*, not a code, and making it a code
 * needs a mail provider plus a server route that mints a custom token. Passkeys
 * are a pitch screen with nothing behind them.
 *
 * Rather than port two screens that do not work, this is the flow that does:
 * email and password, or Google. The step the brief actually needs — the access
 * code — is a separate screen and is fully wired.
 */
@Composable
fun AuthScreen(
    mode: AuthMode,
    onSwitchMode: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var passwordVisible by remember { mutableStateOf(false) }

    // Best guess at the member's country, used only as the initial value on a
    // new account. It decides which marketplace receiving accounts they are
    // offered, and they can change it in their profile afterwards.
    val country = remember { Locale.getDefault().country.takeIf { it.isNotBlank() } }

    TwtScreen {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = when (mode) {
                    AuthMode.SignIn -> "Welcome back"
                    AuthMode.SignUp -> "Create your account"
                },
                style = MaterialTheme.typography.displayMedium,
                color = TwtColors.TextPrimary
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = when (mode) {
                    AuthMode.SignIn -> "Sign in to reach your signals."
                    AuthMode.SignUp -> "You will need an access code from Tanha afterwards."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = TwtColors.TextSecondary
            )

            Spacer(Modifier.height(Spacing.xxl))

            if (mode == AuthMode.SignUp) {
                AuthField(
                    value = state.fullName,
                    onValueChange = viewModel::onFullNameChange,
                    label = "Full name",
                    keyboardType = KeyboardType.Text
                )
                Spacer(Modifier.height(Spacing.md))
            }

            AuthField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                label = "Email",
                keyboardType = KeyboardType.Email
            )
            Spacer(Modifier.height(Spacing.md))

            AuthField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = "Password",
                keyboardType = KeyboardType.Password,
                visualTransformation = if (passwordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailing = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Filled.VisibilityOff
                            else Icons.Filled.Visibility,
                            contentDescription = if (passwordVisible) "Hide password"
                            else "Show password",
                            tint = TwtColors.TextTertiary
                        )
                    }
                }
            )

            state.error?.let {
                Spacer(Modifier.height(Spacing.md))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TwtColors.Sell
                )
            }
            state.notice?.let {
                Spacer(Modifier.height(Spacing.md))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TwtColors.TextSecondary
                )
            }

            Spacer(Modifier.height(Spacing.xl))

            TwtButton(
                text = if (mode == AuthMode.SignIn) "Sign in" else "Create account",
                onClick = {
                    if (mode == AuthMode.SignIn) viewModel.signIn()
                    else viewModel.signUp(country)
                },
                enabled = if (mode == AuthMode.SignIn) state.canSignIn else state.canSignUp,
                loading = state.isSubmitting
            )

            Spacer(Modifier.height(Spacing.md))

            TwtSecondaryButton(
                text = "Continue with Google",
                onClick = {
                    viewModel.setSubmitting(true)
                    scope.launch {
                        when (val result = GoogleSignIn.requestIdToken(context)) {
                            is GoogleSignIn.Result.Success ->
                                viewModel.signInWithGoogle(result.idToken, country)
                            is GoogleSignIn.Result.Cancelled ->
                                viewModel.setSubmitting(false)
                            is GoogleSignIn.Result.Failed ->
                                viewModel.onGoogleSignInFailed(result.message)
                        }
                    }
                }
            )

            Spacer(Modifier.height(Spacing.lg))

            if (mode == AuthMode.SignIn) {
                TextButton(
                    onClick = viewModel::sendPasswordReset,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Forgot your password?",
                        color = TwtColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            TextButton(onClick = onSwitchMode, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = when (mode) {
                        AuthMode.SignIn -> "No account yet? Create one"
                        AuthMode.SignUp -> "Already have an account? Sign in"
                    },
                    color = TwtColors.Gold,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(Spacing.xxl))
        }
    }
}

enum class AuthMode { SignIn, SignUp }

@Composable
private fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = visualTransformation,
        trailingIcon = trailing,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = ImeAction.Next
        ),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = TwtColors.Gold,
            unfocusedBorderColor = TwtColors.HairlineStrong,
            focusedLabelColor = TwtColors.Gold,
            unfocusedLabelColor = TwtColors.TextTertiary,
            focusedTextColor = TwtColors.TextPrimary,
            unfocusedTextColor = TwtColors.TextPrimary,
            cursorColor = TwtColors.Gold
        ),
        modifier = Modifier.fillMaxWidth()
    )
}
