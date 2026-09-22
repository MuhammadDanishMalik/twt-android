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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talkswithtanha.twt.core.designsystem.Haptics
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.model.FollowOutcome
import com.talkswithtanha.twt.core.model.SignalFollow
import com.talkswithtanha.twt.core.model.SignalType
import kotlin.math.roundToLong

/**
 * What the member made — in their words, not the signal's.
 *
 * **Never derive a member's result from the signal's.** Two people who took the
 * same call did not get the same fill: one took profit at TP1, the other held to
 * TP3, a third was stopped out on the spike before either. The signal's status
 * says what the trade did; only the member knows what *they* did.
 *
 * Ported from `RecordResultSheet.swift`.
 */
@Composable
fun RecordResultScreen(
    follow: SignalFollow,
    onClose: () -> Unit,
    onSave: (FollowOutcome, Double?, Long?, String?, String?) -> Unit
) {
    val haptics = LocalHapticFeedback.current

    // Prefilled from whatever is already recorded, so correcting a number is
    // editing rather than retyping.
    var outcome by remember {
        mutableStateOf(if (follow.isSettled) follow.outcome else FollowOutcome.WIN)
    }
    var pips by remember { mutableStateOf(follow.resultPips?.trimmed().orEmpty()) }
    var amount by remember {
        mutableStateOf(follow.resultAmountMinor?.let { (it / 100.0).trimmed() }.orEmpty())
    }
    var note by remember { mutableStateOf(follow.note.orEmpty()) }

    Column(
        Modifier
            .fillMaxSize()
            .background(IosColors.Background)
            .statusBarsPadding()
            .imePadding()
    ) {
        SheetHeader("Record result", onClose)

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SettingsMetrics.pageInset)
                .padding(bottom = 48.dp)
        ) {
            // ── Position summary ─────────────────────────────────────────
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = follow.pair,
                        color = IosColors.TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(
                                if (follow.type == SignalType.BUY) IosColors.BuyGreen
                                else IosColors.SellRed
                            )
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = follow.type.stored,
                            color = Color.Black,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
                Text(
                    text = "Entry ${follow.entryPrice} · Stop ${follow.stopLoss}",
                    color = IosColors.TextSecondary,
                    fontSize = 13.sp
                )
            }

            SettingsSectionHeader("How did it go?")

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(FollowOutcome.WIN, FollowOutcome.BREAKEVEN, FollowOutcome.LOSS).forEach {
                    OutcomeChip(
                        outcome = it,
                        selected = outcome == it,
                        modifier = Modifier.weight(1f)
                    ) {
                        Haptics.tap(haptics)
                        outcome = it
                    }
                }
            }

            SettingsSectionHeader("Your result")

            SettingsCard {
                NumberRow("Pips", pips, "e.g. 42 or -18", allowNegative = true) { pips = it }
                SettingsDivider(SettingsMetrics.rowPadding)
                NumberRow("Amount", amount, "Optional", allowNegative = true) { amount = it }
            }

            Text(
                text = "Negative numbers for a loss. Both are optional — the outcome above is what counts toward your record.",
                color = IosColors.TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(
                    horizontal = SettingsMetrics.rowPadding,
                    vertical = 4.dp
                )
            )

            SettingsSectionHeader("Note")

            SettingsCard {
                BasicTextField(
                    value = note,
                    onValueChange = { note = it },
                    textStyle = TextStyle(color = IosColors.TextPrimary, fontSize = 16.sp),
                    cursorBrush = SolidColor(IosColors.Accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .padding(horizontal = SettingsMetrics.rowPadding, vertical = 13.dp),
                    decorationBox = { inner ->
                        if (note.isEmpty()) {
                            Text(
                                "What you would do differently",
                                color = IosColors.TextSecondary.copy(alpha = 0.6f),
                                fontSize = 16.sp
                            )
                        }
                        inner()
                    }
                )
            }

            Spacer(Modifier.height(24.dp))

            SheetPrimaryButton("Save to my record") {
                onSave(
                    outcome,
                    pips.replace(',', '.').toDoubleOrNull(),
                    // Money is an integer in minor units. The field is typed in
                    // dollars, so it becomes a Double exactly once — here — and
                    // is an integer from then on.
                    amount.replace(',', '.').toDoubleOrNull()?.let { (it * 100).roundToLong() },
                    amount.toDoubleOrNull()?.let { "USD" },
                    note.trim().ifBlank { null }
                )
            }
        }
    }
}

@Composable
private fun OutcomeChip(
    outcome: FollowOutcome,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val tint = when (outcome) {
        FollowOutcome.WIN -> IosColors.BuyGreen
        FollowOutcome.LOSS -> IosColors.SellRed
        else -> IosColors.Warning
    }
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) tint else IosColors.SecondaryBackground)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = outcome.label,
            color = if (selected) Color.Black else IosColors.TextSecondary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun NumberRow(
    label: String,
    value: String,
    placeholder: String,
    allowNegative: Boolean,
    onValueChange: (String) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = SettingsMetrics.rowPadding, vertical = 13.dp)
            .height(26.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            color = IosColors.TextSecondary,
            fontSize = 16.sp,
            modifier = Modifier.width(72.dp)
        )
        BasicTextField(
            value = value,
            onValueChange = { raw ->
                // Filtered at the edge rather than validated on submit, so the
                // field simply cannot hold something unparseable.
                onValueChange(
                    raw.filterIndexed { index, c ->
                        c.isDigit() || c == '.' || (allowNegative && c == '-' && index == 0)
                    }
                )
            },
            singleLine = true,
            textStyle = TextStyle(
                color = IosColors.TextPrimary,
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace
            ),
            cursorBrush = SolidColor(IosColors.Accent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        color = IosColors.TextSecondary.copy(alpha = 0.6f),
                        fontSize = 16.sp
                    )
                }
                inner()
            }
        )
    }
}

/** `42.0` reads as "42", `41.5` keeps its half. */
private fun Double.trimmed(): String =
    if (this == kotlin.math.floor(this)) toInt().toString() else "%.2f".format(this)
