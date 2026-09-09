package com.talkswithtanha.twt.features.signals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.talkswithtanha.twt.core.designsystem.Radius
import com.talkswithtanha.twt.core.designsystem.Spacing
import com.talkswithtanha.twt.core.designsystem.TABULAR_FIGURES
import com.talkswithtanha.twt.core.designsystem.TwtColors
import com.talkswithtanha.twt.core.designsystem.components.TwtButton
import com.talkswithtanha.twt.core.designsystem.components.TwtCard
import com.talkswithtanha.twt.core.model.FollowOutcome
import com.talkswithtanha.twt.core.model.SignalFollow
import kotlin.math.roundToLong

/**
 * What the member made — in their words, not the signal's.
 *
 * **Never derive a member's result from the signal's.** Two people who took the
 * same call did not get the same fill: one took profit at TP1, the other held to
 * TP3, a third was stopped out on the spike before either. The signal's status
 * says what the trade did; only the member knows what *they* did, so they type
 * it.
 */
@Composable
fun RecordOutcomeSheet(
    onSubmit: (
        outcome: FollowOutcome,
        pips: Double?,
        amountMinor: Long?,
        currency: String?,
        note: String?
    ) -> Unit
) {
    var outcome by remember { mutableStateOf(FollowOutcome.WIN) }
    var pips by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = Spacing.xl)
            .padding(bottom = Spacing.xxl)
    ) {
        Text(
            text = "How did it go for you?",
            style = MaterialTheme.typography.headlineMedium,
            color = TwtColors.TextPrimary
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = "Your own result, not the signal's. This is what your stats are built from.",
            style = MaterialTheme.typography.bodyMedium,
            color = TwtColors.TextSecondary
        )

        Spacer(Modifier.height(Spacing.xl))

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            listOf(FollowOutcome.WIN, FollowOutcome.LOSS, FollowOutcome.BREAKEVEN).forEach {
                OutcomeChip(
                    outcome = it,
                    selected = outcome == it,
                    onClick = { outcome = it },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(Spacing.lg))

        NumberField(
            value = pips,
            onValueChange = { pips = it },
            label = "Pips (optional)",
            // Negative allowed on purpose: a loss recorded as "-30" is more
            // natural than asking somebody to type 30 and rely on the outcome
            // chip to carry the sign.
            allowNegative = true
        )

        Spacer(Modifier.height(Spacing.md))

        NumberField(
            value = amount,
            onValueChange = { amount = it },
            label = "Amount in USD (optional)",
            allowNegative = true
        )

        Spacer(Modifier.height(Spacing.md))

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Note (optional)") },
            minLines = 2,
            shape = RoundedCornerShape(14.dp),
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(Spacing.xl))

        TwtButton(
            text = "Save my result",
            onClick = {
                onSubmit(
                    outcome,
                    pips.toDoubleOrNull(),
                    // Money is an integer in minor units, never a float. The
                    // field is typed in dollars, so it is converted once here
                    // and never seen as a Double again.
                    amount.toDoubleOrNull()?.let { (it * 100).roundToLong() },
                    amount.toDoubleOrNull()?.let { "USD" },
                    note.takeIf { it.isNotBlank() }
                )
            }
        )
    }
}

@Composable
private fun OutcomeChip(
    outcome: FollowOutcome,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = when (outcome) {
        FollowOutcome.WIN -> TwtColors.Buy
        FollowOutcome.LOSS -> TwtColors.Sell
        else -> TwtColors.TextSecondary
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.chip))
            .background(if (selected) color.copy(alpha = 0.15f) else TwtColors.Surface)
            .border(
                width = if (selected) 1.dp else 0.5.dp,
                color = if (selected) color else TwtColors.Hairline,
                shape = RoundedCornerShape(Radius.chip)
            )
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.md),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = outcome.label,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) color else TwtColors.TextSecondary
        )
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    allowNegative: Boolean
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            // Filtered at the edge rather than validated on submit, so the field
            // simply cannot hold something unparseable.
            val cleaned = raw.filterIndexed { index, c ->
                c.isDigit() || c == '.' || (allowNegative && c == '-' && index == 0)
            }
            onValueChange(cleaned)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(14.dp),
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = TwtColors.Gold,
    unfocusedBorderColor = TwtColors.HairlineStrong,
    focusedLabelColor = TwtColors.Gold,
    unfocusedLabelColor = TwtColors.TextTertiary,
    focusedTextColor = TwtColors.TextPrimary,
    unfocusedTextColor = TwtColors.TextPrimary,
    cursorColor = TwtColors.Gold
)

/** What the member wrote down, shown back to them. */
@Composable
fun RecordedOutcomeCard(follow: SignalFollow, modifier: Modifier = Modifier) {
    val color = when (follow.outcome) {
        FollowOutcome.WIN -> TwtColors.Buy
        FollowOutcome.LOSS -> TwtColors.Sell
        else -> TwtColors.TextSecondary
    }

    TwtCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "YOUR RESULT",
            style = MaterialTheme.typography.labelSmall,
            color = TwtColors.TextTertiary
        )
        Spacer(Modifier.height(Spacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = follow.outcome.label,
                style = MaterialTheme.typography.titleLarge,
                color = color
            )
            follow.resultPips?.let {
                Text(
                    text = "${if (it >= 0) "+" else ""}${it.toInt()} pips",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFeatureSettings = TABULAR_FIGURES
                    ),
                    color = color
                )
            }
        }
        follow.resultAmountMinor?.let { minor ->
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = formatMinor(minor, follow.resultCurrency ?: "USD"),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFeatureSettings = TABULAR_FIGURES
                ),
                color = TwtColors.TextSecondary
            )
        }
        follow.note?.let {
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = TwtColors.TextSecondary
            )
        }
    }
}

/**
 * Minor units to something readable, without ever going through a Double.
 *
 * `-1234` in USD is "-$12.34". Division and remainder on the integer, so the
 * cents are exact rather than 12.339999999999999.
 */
fun formatMinor(minor: Long, currency: String): String {
    val negative = minor < 0
    val abs = kotlin.math.abs(minor)
    val symbol = if (currency == "USD") "$" else "$currency "
    return buildString {
        if (negative) append('-')
        append(symbol)
        append(abs / 100)
        append('.')
        append("%02d".format(abs % 100))
    }
}
