package com.talkswithtanha.twt.features.signals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.talkswithtanha.twt.core.designsystem.Spacing
import com.talkswithtanha.twt.core.designsystem.TABULAR_FIGURES
import com.talkswithtanha.twt.core.designsystem.TwtColors
import com.talkswithtanha.twt.core.designsystem.components.DirectionBadge
import com.talkswithtanha.twt.core.designsystem.components.StatusBadge
import com.talkswithtanha.twt.core.designsystem.components.TwtCard
import com.talkswithtanha.twt.core.model.Signal
import java.util.concurrent.TimeUnit

/**
 * One trade, as it appears in a list.
 *
 * The three prices are the point of the card, so they are the largest thing on
 * it after the pair, and they are set in tabular figures so a column of cards
 * lines up digit for digit.
 */
@Composable
fun SignalCard(
    signal: Signal,
    isFollowing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TwtCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = signal.pair,
                    style = MaterialTheme.typography.titleLarge,
                    color = TwtColors.TextPrimary
                )
                DirectionBadge(signal.type)
            }
            StatusBadge(signal.status)
        }

        Spacer(Modifier.height(Spacing.lg))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            PriceColumn("Entry", signal.entryPrice, TwtColors.TextPrimary)
            PriceColumn(
                label = signal.takeProfits.firstOrNull()?.label ?: "Target",
                value = signal.takeProfits.firstOrNull()?.price ?: "—",
                color = TwtColors.Buy
            )
            PriceColumn("Stop", signal.stopLoss, TwtColors.Sell)
        }

        Spacer(Modifier.height(Spacing.md))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = buildString {
                    append(signal.timeframe)
                    append("  ·  ")
                    append(signal.tradeStyle.stored)
                    append("  ·  ")
                    append(relativeTime(signal.timestamp.time))
                    // Someone who acted on the original numbers is entitled to
                    // know they moved.
                    if (signal.isEdited) append("  ·  edited")
                },
                style = MaterialTheme.typography.labelMedium,
                color = TwtColors.TextTertiary
            )

            if (isFollowing) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.BookmarkAdded,
                        contentDescription = null,
                        tint = TwtColors.Gold,
                        modifier = Modifier.width(14.dp)
                    )
                    Text(
                        text = "Following",
                        style = MaterialTheme.typography.labelMedium,
                        color = TwtColors.Gold
                    )
                }
            }
        }
    }
}

@Composable
private fun PriceColumn(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = TwtColors.TextTertiary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFeatureSettings = TABULAR_FIGURES
            ),
            color = color
        )
    }
}

/**
 * "12m", "3h", "2d".
 *
 * Short because it sits in a row of metadata that must not wrap, and because the
 * precise minute a signal was posted stops mattering within the hour.
 */
fun relativeTime(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
    val elapsed = (now - epochMillis).coerceAtLeast(0)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsed)
    val hours = TimeUnit.MILLISECONDS.toHours(elapsed)
    val days = TimeUnit.MILLISECONDS.toDays(elapsed)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m"
        hours < 24 -> "${hours}h"
        days < 7 -> "${days}d"
        else -> "${days / 7}w"
    }
}
