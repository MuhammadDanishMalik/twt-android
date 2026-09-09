package com.talkswithtanha.twt.features.signals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.talkswithtanha.twt.core.designsystem.Spacing
import com.talkswithtanha.twt.core.designsystem.TABULAR_FIGURES
import com.talkswithtanha.twt.core.designsystem.TwtColors
import com.talkswithtanha.twt.core.designsystem.components.DirectionBadge
import com.talkswithtanha.twt.core.designsystem.components.EmptyState
import com.talkswithtanha.twt.core.designsystem.components.TwtCard
import com.talkswithtanha.twt.core.designsystem.components.TwtScreen
import com.talkswithtanha.twt.core.model.FollowOutcome
import com.talkswithtanha.twt.core.model.SignalFollow
import com.talkswithtanha.twt.core.model.TradingStats
import com.talkswithtanha.twt.core.signals.FollowedSignalTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class MySignalsViewModel @Inject constructor(
    tracker: FollowedSignalTracker
) : ViewModel() {
    val follows: StateFlow<List<SignalFollow>> = tracker.follows
    val stats: StateFlow<TradingStats> = tracker.stats
    val isLoading: StateFlow<Boolean> = tracker.isLoading
}

/**
 * The member's journal: every trade they took, and what it did for them.
 *
 * Built entirely from `signalFollows`, which is why unfollowing sets a flag
 * rather than deleting the document — the record outlives the following, and it
 * is what the win rate is computed from.
 */
@Composable
fun MySignalsScreen(
    onBack: () -> Unit,
    onOpenSignal: (String) -> Unit,
    viewModel: MySignalsViewModel = hiltViewModel()
) {
    val follows by viewModel.follows.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    val open = follows.filter { it.isActive && it.outcome == FollowOutcome.OPEN }
    val settled = follows.filter { !it.isActive || it.isSettled }

    TwtScreen {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            contentPadding = PaddingValues(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TwtColors.TextPrimary
                        )
                    }
                    Text(
                        text = "My signals",
                        style = MaterialTheme.typography.headlineLarge,
                        color = TwtColors.TextPrimary
                    )
                }
            }

            item { StatsSummary(stats) }

            if (!isLoading && follows.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.Timeline,
                        title = "Nothing followed yet",
                        message = "Follow a trade and it appears here, along with whatever you make on it."
                    )
                }
            }

            if (open.isNotEmpty()) {
                item { SectionHeader("Open", open.size) }
                items(open, key = { it.id }) {
                    FollowRow(it) { onOpenSignal(it.signalId) }
                }
            }

            if (settled.isNotEmpty()) {
                item { SectionHeader("Closed", settled.size) }
                items(settled, key = { it.id }) {
                    FollowRow(it) { onOpenSignal(it.signalId) }
                }
            }
        }
    }
}

@Composable
private fun StatsSummary(stats: TradingStats) {
    TwtCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatCell("Win rate", stats.winRate?.let { "${(it * 100).toInt()}%" } ?: "—")
            StatCell("Wins", stats.wins.toString(), TwtColors.Buy)
            StatCell("Losses", stats.losses.toString(), TwtColors.Sell)
            StatCell("Even", stats.breakEvens.toString())
        }
        Spacer(Modifier.height(Spacing.lg))
        Text(
            text = if (stats.settled == 0) {
                "Record a result on a closed trade and your stats start here."
            } else {
                "${stats.netPips.toInt()} net pips across ${stats.settled} settled trades."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = TwtColors.TextSecondary
        )
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color = TwtColors.TextPrimary
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = TwtColors.TextTertiary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(
                fontFeatureSettings = TABULAR_FIGURES
            ),
            color = color
        )
    }
}

@Composable
private fun FollowRow(follow: SignalFollow, onClick: () -> Unit) {
    TwtCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
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
                    text = follow.pair,
                    style = MaterialTheme.typography.titleMedium,
                    color = TwtColors.TextPrimary
                )
                DirectionBadge(follow.type)
            }
            val color = when (follow.outcome) {
                FollowOutcome.WIN -> TwtColors.Buy
                FollowOutcome.LOSS -> TwtColors.Sell
                FollowOutcome.BREAKEVEN -> TwtColors.TextSecondary
                FollowOutcome.OPEN -> TwtColors.Gold
            }
            Text(
                text = follow.resultPips?.let {
                    "${if (it >= 0) "+" else ""}${it.toInt()} pips"
                } ?: follow.outcome.label,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFeatureSettings = TABULAR_FIGURES
                ),
                color = color
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            // The snapshot, not the signal's current numbers. This is what they
            // acted on, and it does not move when Tanha edits the call.
            text = "Entered ${follow.entryPrice} · stop ${follow.stopLoss} · ${relativeTime(follow.followedAt.time)} ago",
            style = MaterialTheme.typography.labelMedium,
            color = TwtColors.TextTertiary
        )
    }
}
