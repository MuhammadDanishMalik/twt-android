package com.talkswithtanha.twt.features.signals

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.talkswithtanha.twt.core.designsystem.Radius
import com.talkswithtanha.twt.core.designsystem.Spacing
import com.talkswithtanha.twt.core.designsystem.TABULAR_FIGURES
import com.talkswithtanha.twt.core.designsystem.TwtColors
import com.talkswithtanha.twt.core.designsystem.components.DirectionBadge
import com.talkswithtanha.twt.core.designsystem.components.ErrorState
import com.talkswithtanha.twt.core.designsystem.components.StatusBadge
import com.talkswithtanha.twt.core.designsystem.components.TwtButton
import com.talkswithtanha.twt.core.designsystem.components.TwtCard
import com.talkswithtanha.twt.core.designsystem.components.TwtScreen
import com.talkswithtanha.twt.core.designsystem.components.TwtSecondaryButton
import com.talkswithtanha.twt.core.model.Signal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignalDetailScreen(
    onBack: () -> Unit,
    viewModel: SignalDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showRecordSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Asked for at the moment a member follows their first trade — the first
    // point at which they have an obvious reason to say yes, having just asked
    // to be told what happens to it. Asking at launch spends the one prompt
    // Android gives an app on somebody who does not yet know what it is for.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Declined is fine: the in-app card and the journal still work. */ }

    TwtScreen {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
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
            }

            val signal = state.signal

            when {
                state.isLoading -> {
                    Spacer(Modifier.height(Spacing.huge))
                    com.talkswithtanha.twt.core.designsystem.components.SignalCardSkeleton()
                }

                signal == null -> ErrorState(message = state.error ?: "Not found.")

                else -> {
                    SignalHeader(signal)
                    Spacer(Modifier.height(Spacing.xl))
                    PriceLadder(signal)

                    signal.chartImageUrl?.let { url ->
                        Spacer(Modifier.height(Spacing.lg))
                        AsyncImage(
                            model = url,
                            contentDescription = "Chart for ${signal.pair}",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 10f)
                                .clip(RoundedCornerShape(Radius.card))
                        )
                    }

                    signal.notes?.let { notes ->
                        Spacer(Modifier.height(Spacing.lg))
                        TwtCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "TANHA'S NOTE",
                                style = MaterialTheme.typography.labelSmall,
                                color = TwtColors.TextTertiary
                            )
                            Spacer(Modifier.height(Spacing.sm))
                            Text(
                                text = notes,
                                style = MaterialTheme.typography.bodyLarge,
                                color = TwtColors.TextPrimary
                            )
                        }
                    }

                    Spacer(Modifier.height(Spacing.lg))

                    TwtCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = signal.tradeStyle.stored.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = TwtColors.Gold
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            text = signal.tradeStyle.explanation,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TwtColors.TextSecondary
                        )
                    }

                    Spacer(Modifier.height(Spacing.xl))

                    if (state.isFollowing) {
                        TwtSecondaryButton(
                            text = "Stop following",
                            onClick = viewModel::toggleFollow
                        )
                    } else {
                        TwtButton(
                            text = "Follow this trade",
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermission.launch(
                                        Manifest.permission.POST_NOTIFICATIONS
                                    )
                                }
                                viewModel.toggleFollow()
                            }
                        )
                    }

                    if (state.canRecordOutcome) {
                        Spacer(Modifier.height(Spacing.md))
                        TwtSecondaryButton(
                            text = "Record what you made",
                            onClick = { showRecordSheet = true }
                        )
                    }

                    state.follow?.takeIf { it.isSettled }?.let { follow ->
                        Spacer(Modifier.height(Spacing.lg))
                        RecordedOutcomeCard(follow)
                    }

                    Spacer(Modifier.height(Spacing.huge))
                }
            }
        }
    }

    if (showRecordSheet) {
        ModalBottomSheet(
            onDismissRequest = { showRecordSheet = false },
            sheetState = sheetState,
            containerColor = TwtColors.BackgroundElevated
        ) {
            RecordOutcomeSheet(
                onSubmit = { outcome, pips, amountMinor, currency, note ->
                    viewModel.recordOutcome(outcome, pips, amountMinor, currency, note)
                    showRecordSheet = false
                }
            )
        }
    }
}

@Composable
private fun SignalHeader(signal: Signal) {
    Column {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = signal.pair,
                style = MaterialTheme.typography.displayMedium,
                color = TwtColors.TextPrimary
            )
            DirectionBadge(signal.type)
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusBadge(signal.status)
            Text(
                text = "${signal.timeframe} · R:R ${signal.riskReward} · ${relativeTime(signal.timestamp.time)}",
                style = MaterialTheme.typography.labelMedium,
                color = TwtColors.TextTertiary
            )
        }
        if (signal.isEdited) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "Tanha has edited this signal since it was posted.",
                style = MaterialTheme.typography.labelMedium,
                color = TwtColors.Warning
            )
        }
    }
}

/** Entry, every target, and the stop — in the order price would reach them. */
@Composable
private fun PriceLadder(signal: Signal) {
    TwtCard(modifier = Modifier.fillMaxWidth()) {
        PriceRow("Entry", signal.entryPrice, TwtColors.TextPrimary, false)
        signal.takeProfits.forEach { tp ->
            Spacer(Modifier.height(Spacing.md))
            PriceRow(tp.label, tp.price, TwtColors.Buy, tp.isHit)
        }
        Spacer(Modifier.height(Spacing.md))
        PriceRow("Stop loss", signal.stopLoss, TwtColors.Sell, signal.status.isLost)

        signal.pipsGained?.let { pips ->
            Spacer(Modifier.height(Spacing.lg))
            Text(
                text = "${if (pips >= 0) "+" else ""}${pips.toInt()} pips",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFeatureSettings = TABULAR_FIGURES
                ),
                color = if (pips >= 0) TwtColors.Buy else TwtColors.Sell
            )
        }
    }
}

@Composable
private fun PriceRow(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    hit: Boolean
) {
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
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = TwtColors.TextSecondary
            )
            if (hit) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(color.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "Hit",
                        tint = color,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFeatureSettings = TABULAR_FIGURES
            ),
            color = color
        )
    }
}
