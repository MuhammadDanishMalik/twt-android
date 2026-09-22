package com.talkswithtanha.twt.features.signals

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.model.Signal
import com.talkswithtanha.twt.core.model.SignalType

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
        LiveMarketStatusBar(state.signals)

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

            if (active.isEmpty()) {
                item {
                    EmptyNote(
                        title = if (state.error != null) "Couldn't load signals"
                        else "No Active Signals",
                        message = state.error ?: "Tanha is scanning the market."
                    )
                }
            } else {
                items(active, key = { it.id }) { signal ->
                    SignalTicketCard(
                        signal = signal,
                        isFollowing = signal.id in state.followedSignalIds,
                        modifier = Modifier.padding(horizontal = 16.dp),
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

/**
 * The ticker strip.
 *
 * Every pair with a running trade, scrolling continuously when there are more
 * than fit. It is the one part of the app that moves on its own, and it earns
 * that: a signals product with a dead top edge reads as stale.
 */
@Composable
private fun LiveMarketStatusBar(signals: List<Signal>) {
    val pairs = signals.filter { it.status.isOngoing }.map { it.pair }.distinct()
    // 128 on iOS. Android's default font is wider at the same point size, so
    // a three-part cell (pair, price, direction) wraps at that width.
    val itemWidth = 152.dp

    Row(
        Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(Color(0xFF0E0E0E)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (pairs.isNotEmpty()) PulsingDot()
            Text(
                text = if (pairs.isEmpty()) "IDLE" else "LIVE",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp
            )
        }

        Box(
            Modifier
                .width(0.5.dp)
                .height(20.dp)
                .background(Color.White.copy(alpha = 0.06f))
        )

        if (pairs.isEmpty()) {
            Text(
                text = "No signals posted yet",
                color = Color.White.copy(alpha = 0.35f),
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 10.dp)
            )
            return@Row
        }

        val transition = rememberInfiniteTransition(label = "ticker")
        val offset by transition.animateFloat(
            initialValue = 0f,
            targetValue = -1f,
            animationSpec = infiniteRepeatable(
                animation = tween(pairs.size * 4200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "offset"
        )

        Box(
            Modifier
                .weight(1f)
                .height(36.dp)
                .clipToBounds()
        ) {
            Row(
                Modifier.graphicsLayer {
                    translationX = offset * pairs.size * itemWidth.toPx()
                }
            ) {
                // Doubled, so the strip wraps without a visible seam.
                (pairs + pairs).forEach { pair ->
                    val signal = signals.first { it.pair == pair }
                    TickerCell(signal, Modifier.width(itemWidth))
                }
            }
        }
    }
}

@Composable
private fun TickerCell(signal: Signal, modifier: Modifier = Modifier) {
    Row(
        modifier.padding(start = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = signal.pair,
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false
        )
        Text(
            text = signal.entryPrice,
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            softWrap = false
        )
        Text(
            text = signal.type.stored,
            color = if (signal.type == SignalType.BUY) IosColors.BuyGreen else IosColors.SellRed,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun PulsingDot() {
    val alpha by rememberInfiniteTransition(label = "dot").animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "alpha"
    )
    Box(
        Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(IosColors.BuyGreen.copy(alpha = alpha))
    )
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
