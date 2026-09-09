package com.talkswithtanha.twt.features.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.talkswithtanha.twt.core.designsystem.Spacing
import com.talkswithtanha.twt.core.designsystem.TwtColors
import com.talkswithtanha.twt.core.designsystem.components.TwtButton
import com.talkswithtanha.twt.core.designsystem.components.TwtScreen

@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    viewModel: EditProfileViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    TwtScreen {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TwtColors.TextPrimary
                    )
                }
                Text(
                    text = "Edit profile",
                    style = MaterialTheme.typography.headlineLarge,
                    color = TwtColors.TextPrimary
                )
            }

            Spacer(Modifier.height(Spacing.lg))

            ProfileField(state.fullName, viewModel::onFullNameChange, "Full name")
            Spacer(Modifier.height(Spacing.md))
            ProfileField(
                value = state.phone,
                onValueChange = viewModel::onPhoneChange,
                label = "Phone",
                keyboardType = KeyboardType.Phone
            )
            Spacer(Modifier.height(Spacing.md))
            ProfileField(
                value = state.country,
                onValueChange = viewModel::onCountryChange,
                label = "Country (two letters, e.g. PK)"
            )
            Spacer(Modifier.height(Spacing.md))
            ProfileField(
                value = state.bio,
                onValueChange = viewModel::onBioChange,
                label = "About you",
                singleLine = false
            )

            state.error?.let {
                Spacer(Modifier.height(Spacing.md))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = TwtColors.Sell)
            }
            if (state.saved) {
                Spacer(Modifier.height(Spacing.md))
                Text(
                    text = "Saved.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TwtColors.TextSecondary
                )
            }

            Spacer(Modifier.height(Spacing.xl))

            TwtButton(
                text = "Save changes",
                onClick = viewModel::save,
                enabled = state.fullName.isNotBlank(),
                loading = state.isSaving
            )

            Spacer(Modifier.height(Spacing.huge))
        }
    }
}

@Composable
private fun ProfileField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
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
