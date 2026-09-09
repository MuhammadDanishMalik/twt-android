package com.talkswithtanha.twt.features.signals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.talkswithtanha.twt.core.designsystem.Spacing
import com.talkswithtanha.twt.core.designsystem.TwtColors
import com.talkswithtanha.twt.core.designsystem.components.EmptyState
import com.talkswithtanha.twt.core.designsystem.components.ErrorState
import com.talkswithtanha.twt.core.designsystem.components.SignalCardSkeleton
import com.talkswithtanha.twt.core.designsystem.components.TwtScreen
import com.talkswithtanha.twt.core.model.SignalStatus

/**
 * The feed. Live, ordered newest first, drafts excluded.
 *
 * Split into running and finished rather than one flat list: a member opening
 * this wants to know what is on right now, and yesterday's closed trades are
 * history they scroll to deliberately.
 */
@Composable
fun SignalsScreen(
    onOpenSignal: (String) -> Unit,
    viewModel: SignalsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val ongoing = state.signals.filter { it.status.isOngoing }
    val finished = state.signals.filter { !it.status.isOngoing }

    TwtScreen {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                top = Spacing.huge,
                // Clears the floating bottom bar. Without this the last card is
                // permanently half-hidden behind the pill.
                bottom = 120.dp
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            item {
                Column {
                    Text(
                        text = "Signals",
                        style = MaterialTheme.typography.displayMedium,
                        color = TwtColors.TextPrimary
                    )
                    Text(
                        text = "Every call Tanha has published.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TwtColors.TextSecondary
                    )
                    Spacer(Modifier.height(Spacing.sm))
                }
            }

            when {
                state.isLoading -> items(3) { SignalCardSkeleton() }

                state.error != null -> item {
                    ErrorState(message = state.error!!)
                }

                state.signals.isEmpty() -> item {
                    EmptyState(
                        icon = Icons.Outlined.ShowChart,
                        title = "No signals yet",
                        message = "When Tanha publishes a trade it appears here straight away."
                    )
                }

                else -> {
                    if (ongoing.isNotEmpty()) {
                        item { SectionHeader("Running", ongoing.size) }
                        items(ongoing, key = { it.id }) { signal ->
                            SignalCard(
                                signal = signal,
                                isFollowing = signal.id in state.followedSignalIds,
                                onClick = { onOpenSignal(signal.id) }
                            )
                        }
                    }
                    if (finished.isNotEmpty()) {
                        item { SectionHeader("Finished", finished.size) }
                        items(finished, key = { it.id }) { signal ->
                            SignalCard(
                                signal = signal,
                                isFollowing = signal.id in state.followedSignalIds,
                                onClick = { onOpenSignal(signal.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SectionHeader(title: String, count: Int? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.md, bottom = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = TwtColors.TextTertiary
        )
        count?.let {
            Text(
                text = it.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = TwtColors.TextTertiary
            )
        }
    }
}

/** Kept next to the feed so the two agree on what "running" means. */
internal val SignalStatus.sectionTitle: String
    get() = if (isOngoing) "Running" else "Finished"
