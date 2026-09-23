package com.talkswithtanha.twt.features.signals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.designsystem.components.SignalCardSkeleton
import com.talkswithtanha.twt.core.designsystem.staggeredAppear
import com.talkswithtanha.twt.core.model.Signal

/**
 * The Signals tab: a live ticker across the top, then the trades in three
 * sections — running, closed today, and everything older.
 *
 * Ported from `SignalsView.swift`.
 */
@Composable
fun SignalsScreen(
    onOpenSignal: (String) -> Unit,
    viewModel: SignalsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val active = state.signals.filter { it.status.isOngoing }
    val closed = state.signals.filter { !it.status.isOngoing }
    val today = closed.filter { isToday(it.timestamp.time) }
    val older = closed.filterNot { isToday(it.timestamp.time) }

    Column(
        Modifier
            .fillMaxSize()
            .background(IosColors.Background)
            .statusBarsPadding()
    ) {
        LazyColumn(
            contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                SectionLabel(
                    title = "Active Trades",
                    subtitle = "${active.size} signals running",
                    icon = Icons.Filled.ShowChart
                )
            }

            when {
                // A skeleton of the real card, so the row is the right size
                // from the first frame and nothing jumps when data lands.
                state.isLoading -> items(2) { index ->
                    SignalCardSkeleton(
                        Modifier
                            .padding(horizontal = 16.dp)
                            .staggeredAppear(index)
                    )
                }

                active.isEmpty() -> item {
                    EmptyNote(
                        title = if (state.error != null) "Couldn't load signals"
                        else "No Active Signals",
                        message = state.error ?: "Tanha is scanning the market."
                    )
                }

                else -> itemsIndexed(active, key = { _, it -> it.id }) { index, signal ->
                    SignalTicketCard(
                        signal = signal,
                        isFollowing = signal.id in state.followedSignalIds,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .staggeredAppear(index),
                        onOpen = { onOpenSignal(signal.id) },
                        onFollow = { viewModel.follow(signal) },
                        onRequestUnfollow = { viewModel.unfollow(signal.id) }
                    )
                }
            }

            if (today.isNotEmpty()) {
                item {
                    SectionLabel(
                        title = "Closed Today",
                        subtitle = winSummary(today),
                        icon = Icons.Filled.CheckCircle
                    )
                }
                items(today, key = { it.id }) { signal ->
                    SignalTicketCard(
                        signal = signal,
                        isFollowing = signal.id in state.followedSignalIds,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        onOpen = { onOpenSignal(signal.id) }
                    )
                }
            }

            if (older.isNotEmpty()) {
                item {
                    SectionLabel(
                        title = "Recent Results",
                        subtitle = winSummary(older),
                        icon = Icons.Filled.Schedule
                    )
                }
                items(older, key = { it.id }) { signal ->
                    SignalTicketCard(
                        signal = signal,
                        isFollowing = signal.id in state.followedSignalIds,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        onOpen = { onOpenSignal(signal.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(title: String, subtitle: String, icon: ImageVector) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.size(15.dp)
        )
        Column {
            Text(
                text = title,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(text = subtitle, color = Color.White.copy(alpha = 0.45f), fontSize = 13.sp)
        }
    }
}

@Composable
private fun EmptyNote(title: String, message: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(
            message,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 14.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

private fun winSummary(signals: List<Signal>): String {
    val wins = signals.count { it.status.isWon }
    return "$wins/${signals.size} wins"
}

private fun isToday(epochMillis: Long): Boolean {
    val now = java.util.Calendar.getInstance()
    val then = java.util.Calendar.getInstance().apply { timeInMillis = epochMillis }
    return now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR) &&
        now.get(java.util.Calendar.DAY_OF_YEAR) == then.get(java.util.Calendar.DAY_OF_YEAR)
}
