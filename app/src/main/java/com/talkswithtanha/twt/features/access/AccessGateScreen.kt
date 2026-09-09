package com.talkswithtanha.twt.features.access

import android.content.Intent
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
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.talkswithtanha.twt.core.designsystem.Spacing
import com.talkswithtanha.twt.core.designsystem.TwtColors
import com.talkswithtanha.twt.core.designsystem.components.TwtButton
import com.talkswithtanha.twt.core.designsystem.components.TwtScreen
import com.talkswithtanha.twt.core.designsystem.components.TwtSecondaryButton

/**
 * The front door.
 *
 * A signed-in account with no live access code sees this and nothing else. There
 * is no price on it, no plan, and no way to buy anything — a code comes from
 * Tanha over WhatsApp, and the two buttons here are how a member reaches him.
 */
@Composable
fun AccessGateScreen(viewModel: AccessGateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val support by viewModel.support.collectAsStateWithLifecycle()
    val user by viewModel.user.collectAsStateWithLifecycle()
    val context = LocalContext.current

    fun open(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        }
    }

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
                text = "One more step",
                style = MaterialTheme.typography.displayMedium,
                color = TwtColors.TextPrimary
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = "TWT is invite only. Enter the access code Tanha sent you.",
                style = MaterialTheme.typography.bodyLarge,
                color = TwtColors.TextSecondary
            )

            Spacer(Modifier.height(Spacing.xxl))

            OutlinedTextField(
                value = state.code,
                onValueChange = viewModel::onCodeChange,
                label = { Text("Access code") },
                placeholder = { Text("TWT-4H2K-9XQP", color = TwtColors.TextTertiary) },
                singleLine = true,
                isError = state.error != null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Done
                ),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TwtColors.Gold,
                    unfocusedBorderColor = TwtColors.HairlineStrong,
                    focusedLabelColor = TwtColors.Gold,
                    unfocusedLabelColor = TwtColors.TextTertiary,
                    focusedTextColor = TwtColors.TextPrimary,
                    unfocusedTextColor = TwtColors.TextPrimary,
                    cursorColor = TwtColors.Gold,
                    errorBorderColor = TwtColors.Sell
                ),
                modifier = Modifier.fillMaxWidth()
            )

            state.error?.let {
                Spacer(Modifier.height(Spacing.md))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TwtColors.Sell
                )
            }

            Spacer(Modifier.height(Spacing.xl))

            TwtButton(
                text = "Unlock TWT",
                onClick = viewModel::redeem,
                enabled = state.canSubmit,
                loading = state.isRedeeming
            )

            Spacer(Modifier.height(Spacing.xxl))

            Text(
                text = "Do not have a code?",
                style = MaterialTheme.typography.titleMedium,
                color = TwtColors.TextPrimary
            )
            Spacer(Modifier.height(Spacing.md))

            TwtSecondaryButton(
                text = "Message Tanha on WhatsApp",
                icon = Icons.Outlined.Chat,
                onClick = {
                    val name = user?.fullName.orEmpty()
                    val email = user?.email.orEmpty()
                    open(
                        support.whatsAppUrl(
                            "Hi Tanha, I would like an access code for the TWT app. " +
                                "My name is $name and I signed up with $email."
                        )
                    )
                }
            )

            Spacer(Modifier.height(Spacing.md))

            TwtSecondaryButton(
                text = "How to get access",
                icon = Icons.Outlined.OpenInNew,
                onClick = { open(support.accessPageUrl) }
            )

            Spacer(Modifier.height(Spacing.xl))

            TextButton(
                onClick = viewModel::signOut,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Sign out",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TwtColors.TextTertiary,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(Spacing.xxl))
        }
    }
}
