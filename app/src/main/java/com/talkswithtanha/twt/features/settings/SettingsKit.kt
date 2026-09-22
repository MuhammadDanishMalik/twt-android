package com.talkswithtanha.twt.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talkswithtanha.twt.core.designsystem.Haptics
import com.talkswithtanha.twt.core.designsystem.IosColors

/**
 * The grouped-settings kit, ported from `SettingsRow.swift`.
 *
 * iOS members expect Settings to look like Settings: rounded cards of rows, a
 * coloured glyph tile per row, hairline dividers inset past the tile, and a
 * chevron or an up-right arrow depending on whether the row goes somewhere in
 * the app or leaves it. Reproducing that exactly is the point — it is the one
 * screen where inventing an Android-native look would make the two apps feel
 * like different products.
 */
object SettingsMetrics {
    val cardRadius = 18.dp
    val pageInset = 20.dp
    val rowPadding = 14.dp
    val iconSize = 29.dp
    val iconRadius = 8.dp
    val iconGap = 12.dp
    /** Dividers start past the glyph tile, so the column of text is unbroken. */
    val textInset = rowPadding + iconSize + iconGap
}

@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SettingsMetrics.cardRadius))
            .background(IosColors.SecondaryBackground),
        content = content
    )
}

@Composable
fun SettingsDivider(inset: androidx.compose.ui.unit.Dp = SettingsMetrics.textInset) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = inset)
            .height(0.5.dp)
            .background(IosColors.TextSecondary.copy(alpha = 0.18f))
    )
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        color = IosColors.TextSecondary,
        fontSize = 13.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 24.dp, bottom = 7.dp)
    )
}

enum class SettingsAccessory { CHEVRON, EXTERNAL, NONE }

@Composable
fun SettingsIconTile(icon: ImageVector, tint: Color) {
    Box(
        Modifier
            .size(SettingsMetrics.iconSize)
            .clip(RoundedCornerShape(SettingsMetrics.iconRadius))
            .background(tint),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(17.dp)
        )
    }
}

@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = Color.Transparent,
    subtitle: String? = null,
    value: String? = null,
    accessory: SettingsAccessory = SettingsAccessory.CHEVRON,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                Haptics.tap(haptics)
                onClick()
            }
            .padding(horizontal = SettingsMetrics.rowPadding, vertical = 12.dp)
            .defaultMinSize(minHeight = 52.dp),
        horizontalArrangement = Arrangement.spacedBy(SettingsMetrics.iconGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon?.let { SettingsIconTile(it, tint) }

        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (isDestructive) IosColors.SellRed else IosColors.TextPrimary,
                fontSize = 17.sp
            )
            subtitle?.let {
                Text(text = it, color = IosColors.TextSecondary, fontSize = 13.sp)
            }
        }

        value?.let {
            Text(text = it, color = IosColors.TextSecondary, fontSize = 15.sp)
        }

        when (accessory) {
            SettingsAccessory.CHEVRON -> Icon(
                Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = IosColors.TextSecondary.copy(alpha = 0.65f),
                modifier = Modifier.size(14.dp)
            )
            SettingsAccessory.EXTERNAL -> Icon(
                Icons.Filled.NorthEast,
                contentDescription = null,
                tint = IosColors.TextSecondary.copy(alpha = 0.65f),
                modifier = Modifier.size(14.dp)
            )
            SettingsAccessory.NONE -> Unit
        }
    }
}

/**
 * The header every sheet in this family wears: a close button on the left and
 * the title centred, matching `SheetHeader` on iOS.
 */
@Composable
fun SheetHeader(title: String, onClose: () -> Unit) {
    val haptics = LocalHapticFeedback.current

    Box(
        Modifier
            .fillMaxWidth()
            .background(IosColors.Background)
            .padding(horizontal = SettingsMetrics.pageInset, vertical = 8.dp)
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(IosColors.SecondaryBackground)
                .clickable {
                    Haptics.tap(haptics)
                    onClose()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Close",
                tint = IosColors.TextPrimary,
                modifier = Modifier.size(17.dp)
            )
        }
        Text(
            text = title,
            color = IosColors.TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

/** The pill button at the bottom of a sheet. `AppButton(.primary, .large)`. */
@Composable
fun SheetPrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(CircleShape)
            .background(if (enabled) Color.White else Color(0xFF2C2C2E))
            .clickable(enabled = enabled) {
                Haptics.tap(haptics)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) Color.Black else IosColors.TextSecondary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
