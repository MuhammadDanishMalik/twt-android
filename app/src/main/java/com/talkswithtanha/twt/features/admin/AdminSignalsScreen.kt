package com.talkswithtanha.twt.features.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.model.Signal
import com.talkswithtanha.twt.core.model.SignalStatus
import com.talkswithtanha.twt.core.model.SignalType
import com.talkswithtanha.twt.features.settings.SettingsMetrics

/**
 * Minding the open trades from a phone: tick a target as it is hit, close a
 * trade, or pull a signal that went out wrong.
 *
 * This is the half of signal management that is time-critical. Writing a signal
 * can wait five minutes; marking TP1 hit cannot, because followers are told the
 * moment the document changes — and a member watching price run past a target
 * they were never told about is the whole reason following exists.
 */
@Composable
fun AdminSignalsScreen(
    onBack: () -> Unit,
    viewModel: AdminSignalsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmingDelete by remember { mutableStateOf<Signal?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(IosColors.Background)
            .statusBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = SettingsMetrics.pageInset, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(IosColors.SecondaryBackground)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = IosColors.TextPrimary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.weight(1f))
            Text("Signals", color = IosColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(40.dp))
        }

        LazyColumn(
            contentPadding = PaddingValues(
                start = SettingsMetrics.pageInset,
                end = SettingsMetrics.pageInset,
                bottom = 48.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            state.error?.let { message ->
                item { Text(message, color = IosColors.SellRed, fontSize = 13.sp) }
            }

            if (state.ongoing.isNotEmpty()) {
                item { SectionLabel("Running") }
                items(state.ongoing, key = { it.id }) { signal ->
                    SignalRow(
                        signal = signal,
                        isOngoing = true,
                        isWorking = state.workingId == signal.id,
                        onToggleTarget = { viewModel.toggleTarget(signal, it) },
                        onStatus = { viewModel.setStatus(signal, it) },
                        onDelete = { confirmingDelete = signal }
                    )
                }
            }

            if (state.finished.isNotEmpty()) {
                item { SectionLabel("Recently closed") }
                items(state.finished, key = { it.id }) { signal ->
                    SignalRow(
                        signal = signal,
                        isOngoing = false,
                        isWorking = state.workingId == signal.id,
                        onToggleTarget = {},
                        onStatus = { viewModel.setStatus(signal, it) },
                        onDelete = { confirmingDelete = signal }
                    )
                }
            }
        }
    }

    confirmingDelete?.let { signal ->
        AlertDialog(
            onDismissRequest = { confirmingDelete = null },
            title = { Text("Delete this signal?") },
            text = {
                Text("It disappears from every member's feed. Followers keep their own record of it.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(signal)
                    confirmingDelete = null
                }) { Text("Delete", color = IosColors.SellRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = null }) { Text("Keep it") }
            }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = IosColors.TextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 12.dp)
    )
}

@Composable
private fun SignalRow(
    signal: Signal,
    isOngoing: Boolean,
    isWorking: Boolean,
    onToggleTarget: (Int) -> Unit,
    onStatus: (SignalStatus) -> Unit,
    onDelete: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF141414))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(signal.pair, color = IosColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(8.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (signal.type == SignalType.BUY) IosColors.BuyGreen else IosColors.SellRed)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(signal.type.stored, color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.weight(1f))
            if (isWorking) {
                CircularProgressIndicator(
                    color = IosColors.TextSecondary,
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.size(8.dp))
            }
            Text(signal.status.stored, color = IosColors.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        Text(
            text = "Entry ${signal.entryPrice} · Stop ${signal.stopLoss}",
            color = IosColors.TextSecondary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 6.dp)
        )

        if (signal.takeProfits.isNotEmpty()) {
            Row(
                Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                signal.takeProfits.forEachIndexed { index, target ->
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (target.isHit) IosColors.BuyGreen else Color(0xFF222222))
                            .clickable(enabled = isOngoing && !isWorking) { onToggleTarget(index) }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            if (target.isHit) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                            null,
                            tint = if (target.isHit) Color.Black else IosColors.TextPrimary,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = target.label,
                            color = if (target.isHit) Color.Black else IosColors.TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            if (isOngoing) {
                Pill("Won", IosColors.BuyGreen) { onStatus(SignalStatus.WON) }
                Pill("Lost", IosColors.SellRed) { onStatus(SignalStatus.LOST) }
            } else {
                Pill("Reopen", IosColors.TextPrimary) { onStatus(SignalStatus.ACTIVE) }
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF1F1F1F))
                    .clickable(onClick = onDelete)
                    .padding(horizontal = 11.dp, vertical = 7.dp)
            ) {
                Icon(Icons.Filled.Delete, "Delete", tint = IosColors.TextSecondary, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun Pill(text: String, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp)
    ) {
        Text(text, color = tint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
