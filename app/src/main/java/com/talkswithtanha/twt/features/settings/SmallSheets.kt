package com.talkswithtanha.twt.features.settings

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.features.home.HomeViewModel
import com.talkswithtanha.twt.features.home.components.HomeShortcutsLayout
import kotlinx.coroutines.launch

/**
 * Appearance.
 *
 * Two settings, and one of them is honest about not being much of a choice: the
 * app is drawn dark, and the note says so rather than pretending light mode is
 * an equal option. The other picks how the three home entries are laid out.
 */
@Composable
fun AppearanceScreen(
    onClose: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val layout by viewModel.shortcutsLayout.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .background(IosColors.Background)
            .statusBarsPadding()
    ) {
        SheetHeader("Appearance", onClose)

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SettingsMetrics.pageInset)
        ) {
            SettingsSectionHeader("Theme")

            SettingsCard {
                ChoiceRow(
                    icon = Icons.Filled.DarkMode,
                    tint = IosColors.SettingsIcon.Appearance,
                    title = "Dark",
                    selected = true
                ) {}
                SettingsDivider()
                ChoiceRow(
                    icon = Icons.Filled.LightMode,
                    tint = IosColors.SettingsIcon.Appearance,
                    title = "Light",
                    selected = false
                ) {}
            }

            Text(
                text = "The app is designed dark — the signal cards, the access card and the home screen photograph are all built for it. Light mode is not drawn yet.",
                color = IosColors.TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            )

            SettingsSectionHeader("Home shortcuts")

            SettingsCard {
                HomeShortcutsLayout.entries.forEachIndexed { index, option ->
                    ChoiceRow(
                        icon = if (option == HomeShortcutsLayout.LIST) {
                            Icons.AutoMirrored.Filled.List
                        } else {
                            Icons.Filled.Layers
                        },
                        tint = IosColors.SettingsIcon.Account,
                        title = option.title,
                        subtitle = option.subtitle,
                        selected = layout == option
                    ) {
                        scope.launch { HomeViewModel.setLayout(context, option) }
                    }
                    if (index < HomeShortcutsLayout.entries.lastIndex) SettingsDivider()
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    selected: Boolean,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = SettingsMetrics.rowPadding, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(SettingsMetrics.iconGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIconTile(icon, tint)
        Column(Modifier.weight(1f)) {
            Text(title, color = IosColors.TextPrimary, fontSize = 17.sp)
            subtitle?.let {
                Text(it, color = IosColors.TextSecondary, fontSize = 13.sp)
            }
        }
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Selected",
                tint = IosColors.Accent,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Contact Support.
 *
 * Writes into the member's own `support_{uid}` thread rather than opening a mail
 * client — the reply arrives in the app's Chat tab, where they will look for it.
 */
@Composable
fun ContactSupportScreen(
    onClose: () -> Unit,
    onSend: (String) -> Unit
) {
    var message by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .background(IosColors.Background)
            .statusBarsPadding()
            .imePadding()
    ) {
        Box(Modifier.fillMaxWidth()) {
            SheetHeader("Contact Support", onClose)
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = SettingsMetrics.pageInset)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (message.isBlank()) IosColors.SecondaryBackground
                        else IosColors.Accent
                    )
                    .clickable(enabled = message.isNotBlank()) {
                        onSend(message.trim())
                        message = ""
                        onClose()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Send",
                    tint = if (message.isBlank()) IosColors.TextSecondary else Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        BasicTextField(
            value = message,
            onValueChange = { message = it },
            textStyle = TextStyle(color = IosColors.TextPrimary, fontSize = 17.sp),
            cursorBrush = SolidColor(IosColors.Accent),
            modifier = Modifier
                .fillMaxSize()
                .padding(SettingsMetrics.pageInset),
            decorationBox = { inner ->
                if (message.isEmpty()) {
                    Text(
                        "Please share any feedback that you have.",
                        color = IosColors.TextSecondary,
                        fontSize = 17.sp
                    )
                }
                inner()
            }
        )
    }
}
