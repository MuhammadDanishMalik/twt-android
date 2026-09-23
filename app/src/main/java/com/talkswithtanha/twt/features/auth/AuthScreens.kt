package com.talkswithtanha.twt.features.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.designsystem.brand.TwtWordmark
import java.util.Locale

enum class AuthMode { SignIn, SignUp }

/**
 * Sign in, or open an account.
 *
 * Email and password only. Google sign-in was removed because it produced
 * accounts whose address nobody had ever proved they could read, and this app
 * now mails a code to that address — two ways in, one of which skips the check,
 * is not two features. It is a hole.
 *
 * A new account is created here and verified on the next screen. The password
 * goes from this field to Firebase Auth and nowhere else; the code flow that
 * follows never sees it.
 */
@Composable
fun AuthScreen(
    mode: AuthMode,
    onSwitchMode: () -> Unit,
    onVerifyEmail: () -> Unit,
    onForgotPassword: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var passwordVisible by remember { mutableStateOf(false) }

    // Best guess at the member's country, used only as the initial value on a
    // new account. It decides which marketplace receiving accounts they are
    // offered, and they can change it in their profile afterwards.
    val country = remember { Locale.getDefault().country.takeIf { it.isNotBlank() } }

    Column(
        Modifier
            .fillMaxSize()
            .background(IosColors.Background)
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(36.dp))

        TwtWordmark(Modifier.height(26.dp))

        Spacer(Modifier.height(32.dp))

        AuthHeading(
            title = when (mode) {
                AuthMode.SignIn -> "Welcome back"
                AuthMode.SignUp -> "Create your account"
            },
            subtitle = when (mode) {
                AuthMode.SignIn -> "Sign in to reach your signals."
                AuthMode.SignUp -> "We'll email you a code to confirm it's you."
            }
        )

        Spacer(Modifier.height(28.dp))

        if (mode == AuthMode.SignUp) {
            AuthTextField(
                value = state.fullName,
                onValueChange = viewModel::onFullNameChange,
                label = "Full name",
                enabled = !state.isSubmitting
            )
            Spacer(Modifier.height(14.dp))
        }

        AuthTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            label = "Email",
            keyboardType = KeyboardType.Email,
            enabled = !state.isSubmitting
        )

        Spacer(Modifier.height(14.dp))

        AuthTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = "Password",
            keyboardType = KeyboardType.Password,
            isPassword = true,
            passwordVisible = passwordVisible,
            enabled = !state.isSubmitting,
            onTogglePasswordVisibility = { passwordVisible = !passwordVisible }
        )

        if (mode == AuthMode.SignIn) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Forgot your password?",
                color = IosColors.Accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.End)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onForgotPassword)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }

        AuthError(state.error)

        Spacer(Modifier.height(24.dp))

        AuthPrimaryButton(
            text = when (mode) {
                AuthMode.SignIn -> "Sign in"
                AuthMode.SignUp -> "Create account"
            },
            enabled = when (mode) {
                AuthMode.SignIn -> state.canSignIn
                AuthMode.SignUp -> state.canSignUp
            },
            loading = state.isSubmitting,
            onClick = {
                when (mode) {
                    AuthMode.SignIn -> viewModel.signIn()
                    // The account exists the moment this returns; the next
                    // screen is where the address gets proved.
                    AuthMode.SignUp -> viewModel.signUp(country, onCreated = onVerifyEmail)
                }
            }
        )

        Spacer(Modifier.height(24.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = when (mode) {
                    AuthMode.SignIn -> "New here?"
                    AuthMode.SignUp -> "Already have an account?"
                },
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 14.sp
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text = when (mode) {
                    AuthMode.SignIn -> "Create an account"
                    AuthMode.SignUp -> "Sign in"
                },
                color = IosColors.Accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onSwitchMode)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }

        if (mode == AuthMode.SignUp) {
            Spacer(Modifier.height(28.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                IosColors.Gold.copy(alpha = 0.09f),
                                IosColors.Gold.copy(alpha = 0.03f)
                            )
                        )
                    )
                    .padding(14.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(IosColors.Gold)
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = "Invite only",
                            color = IosColors.Gold,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        // Said before they sign up rather than after, because a
                        // member who reaches the access gate expecting signals
                        // is a member who feels they were sold something.
                        text = "Creating an account is free. You'll need an access " +
                            "code from Tanha to see the signals.",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}
