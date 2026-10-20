package com.talkswithtanha.twt.features.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.designsystem.components.TwtButton
import com.talkswithtanha.twt.core.model.SignalAsset
import com.talkswithtanha.twt.core.model.SignalType
import com.talkswithtanha.twt.core.model.TradeStyle
import com.talkswithtanha.twt.features.settings.SettingsMetrics
import com.talkswithtanha.twt.features.settings.SheetHeader

/**
 * Calling a trade from a phone.
 *
 * The admin panel's form, minus everything that is not the trade: no chart
 * upload, no screenshots, no edit history, no push composer. Those exist on the
 * panel because there is room for them; here the question is whether a signal
 * can be posted correctly in the thirty seconds between seeing the setup and
 * price moving away from it.
 */
@Composable
fun PostSignalScreen(
    onClose: () -> Unit,
    viewModel: AdminSignalsViewModel = hiltViewModel()
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(IosColors.Background)
            .statusBarsPadding()
            .imePadding()
    ) {
        SheetHeader("Post a signal", onClose)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SettingsMetrics.pageInset)
        ) {
            // Two big buttons rather than a dropdown. It is the one field that
            // must not be got wrong, and the one most likely to be tapped in a
            // hurry.
            Row(
                Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf(SignalType.BUY, SignalType.SELL).forEach { type ->
                    val selected = draft.type == type
                    val tint = if (type == SignalType.BUY) IosColors.BuyGreen else IosColors.SellRed
                    Row(
                        Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) tint else tint.copy(alpha = 0.12f))
                            .clickable { viewModel.edit { it.copy(type = type) } },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (type == SignalType.BUY) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                            null,
                            tint = if (selected) Color.Black else tint,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = type.stored,
                            color = if (selected) Color.Black else tint,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            GroupLabel("Instrument")
            LabelledField("Pair", draft.pair, "XAU/USD") { value -> viewModel.edit { it.copy(pair = value) } }
            ChipRow(
                options = SignalAsset.entries.map { it.stored to it },
                selected = draft.asset,
                onSelect = { asset -> viewModel.edit { it.copy(asset = asset) } }
            )

            GroupLabel("Levels")
            LabelledField("Entry", draft.entryPrice, "2,318.40", numeric = true) { value ->
                viewModel.edit { it.copy(entryPrice = value) }
            }
            LabelledField("Stop loss", draft.stopLoss, "2,309.00", numeric = true) { value ->
                viewModel.edit { it.copy(stopLoss = value) }
            }
            draft.takeProfits.forEachIndexed { index, price ->
                LabelledField(
                    label = "Take profit ${index + 1}" + if (index == 0) "" else " (optional)",
                    value = price,
                    hint = if (index == 0) "2,324.00" else "",
                    numeric = true
                ) { value ->
                    viewModel.edit { current ->
                        current.copy(
                            takeProfits = current.takeProfits.toMutableList().also { it[index] = value }
                        )
                    }
                }
            }

            GroupLabel("Terms")
            LabelledField("Timeframe", draft.timeframe, "H1") { value -> viewModel.edit { it.copy(timeframe = value) } }
            LabelledField("Risk / reward", draft.riskReward, "1:2") { value ->
                viewModel.edit { it.copy(riskReward = value) }
            }
            ChipRow(
                options = TradeStyle.entries.map { it.stored to it },
                selected = draft.tradeStyle,
                onSelect = { style -> viewModel.edit { it.copy(tradeStyle = style) } }
            )

            GroupLabel("Note (optional)")
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF171717))
                    .padding(13.dp)
            ) {
                if (draft.notes.isEmpty()) {
                    Text("What the setup is, in a line.", color = IosColors.TextSecondary, fontSize = 15.sp)
                }
                BasicTextField(
                    value = draft.notes,
                    onValueChange = { value -> viewModel.edit { it.copy(notes = value) } },
                    textStyle = LocalTextStyle.current.copy(color = IosColors.TextPrimary, fontSize = 15.sp),
                    cursorBrush = SolidColor(IosColors.TextPrimary),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Publish now", color = IosColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = if (draft.isPublished) {
                            "Members see it as soon as you post."
                        } else {
                            "Saved as a draft — publish it from the panel."
                        },
                        color = IosColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = draft.isPublished,
                    onCheckedChange = { value -> viewModel.edit { it.copy(isPublished = value) } }
                )
            }

            state.error?.let {
                Text(it, color = IosColors.SellRed, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
            }

            Text(
                text = "Posting does not send a push on its own — the panel's notify button still does that.",
                color = IosColors.TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            TwtButton(
                text = if (state.workingId == "post") "Posting…" else "Post signal",
                enabled = draft.isComplete && state.workingId == null,
                loading = state.workingId == "post",
                onClick = { viewModel.post(onClose) },
                modifier = Modifier.padding(bottom = 40.dp)
            )
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = IosColors.TextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 20.dp, bottom = 10.dp)
    )
}

/**
 * Label on the left, value typed on the right — the shape of a trade ticket,
 * and quicker to scan for a wrong number than a column of boxes.
 */
@Composable
private fun LabelledField(
    label: String,
    value: String,
    hint: String,
    numeric: Boolean = false,
    onChange: (String) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF171717))
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = IosColors.TextSecondary, fontSize = 14.sp, modifier = Modifier.width(128.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty() && hint.isNotEmpty()) {
                Text(
                    text = hint,
                    color = IosColors.TextSecondary.copy(alpha = 0.5f),
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text
                ),
                textStyle = LocalTextStyle.current.copy(
                    color = IosColors.TextPrimary,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End
                ),
                cursorBrush = SolidColor(IosColors.TextPrimary),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun <T> ChipRow(
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (label, value) ->
            val isSelected = value == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) Color.White else Color(0xFF1F1F1F))
                    .clickable { onSelect(value) }
                    .padding(horizontal = 13.dp, vertical = 8.dp)
            ) {
                Text(
                    text = label,
                    color = if (isSelected) Color.Black else IosColors.TextPrimary.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
