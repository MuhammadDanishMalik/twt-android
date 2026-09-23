package com.talkswithtanha.twt.features.settings

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.designsystem.staggeredAppear
import com.talkswithtanha.twt.core.model.FollowOutcome
import com.talkswithtanha.twt.core.model.SignalFollow
import com.talkswithtanha.twt.core.model.SignalType
import com.talkswithtanha.twt.core.model.TradingStats
import com.talkswithtanha.twt.features.signals.MySignalsViewModel
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * The member's own record: what they took, and what it did for them.
 *
 * Ported from `FollowedSignalsView.swift`. Built entirely from `signalFollows`,
 * which is why unfollowing sets a flag rather than deleting — the record
 * outlives the following, and it is what the win rate is computed from.
 */
@Composable
fun MySignalsScreen(
    onClose: () -> Unit,
    onRecord: (SignalFollow) -> Unit,
    viewModel: MySignalsViewModel = hiltViewModel()
) {
    val follows by viewModel.follows.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    val open = follows.filter { it.isActive && it.outcome == FollowOutcome.OPEN }
    val settled = follows.filter { !it.isActive || it.isSettled }

    Column(
        Modifier
            .fillMaxSize()
            .background(IosColors.Background)
            .statusBarsPadding()
    ) {
        SheetHeader("My Signals", onClose)

        LazyColumn(
            contentPadding = PaddingValues(
                start = SettingsMetrics.pageInset,
                end = SettingsMetrics.pageInset,
                bottom = 48.dp
            )
        ) {
            item { StatsCard(stats) }

            if (open.isNotEmpty()) {
                item { SettingsSectionHeader("Open positions") }
                item {
                    SettingsCard {
                        open.forEachIndexed { index, follow ->
                            FollowRow(follow, Modifier.staggeredAppear(index)) { onRecord(follow) }
                            if (index < open.lastIndex) {
                                SettingsDivider(SettingsMetrics.rowPadding)
                            }
                        }
                    }
                }
            }

            if (settled.isNotEmpty()) {
                item { SettingsSectionHeader("Closed") }
                item {
                    SettingsCard {
                        settled.forEachIndexed { index, follow ->
                            FollowRow(follow, Modifier.staggeredAppear(index)) { onRecord(follow) }
                            if (index < settled.lastIndex) {
                                SettingsDivider(SettingsMetrics.rowPadding)
                            }
                        }
                    }
                }
            }

            if (follows.isEmpty() && !isLoading) {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp, start = 16.dp, end = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.ShowChart,
                            contentDescription = null,
                            tint = IosColors.TextSecondary.copy(alpha = 0.5f),
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            "Nothing followed yet",
                            color = IosColors.TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Follow a signal and it appears here — with an alert when it hits, and somewhere to write down what you made.",
                            color = IosColors.TextSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsCard(stats: TradingStats) {
    Column(
        Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(SettingsMetrics.cardRadius))
            .background(IosColors.SecondaryBackground)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            StatColumn(
                // Null rather than zero when nothing has settled. "0%" on a new
                // account is a true statement that reads as failure.
                value = stats.winRate?.let { "${(it * 100).toInt()}%" } ?: "—",
                label = "Win rate",
                modifier = Modifier.weight(1f)
            )
            StatDivider()
            StatColumn(
                value = "${if (stats.netPips >= 0) "+" else ""}${stats.netPips.toInt()}",
                label = "Net pips",
                tint = if (stats.netPips >= 0) IosColors.BuyGreen else IosColors.SellRed,
                modifier = Modifier.weight(1f)
            )
            StatDivider()
            StatColumn(
                value = stats.followed.toString(),
                label = "Followed",
                modifier = Modifier.weight(1f)
            )
        }

        if (stats.settled > 0) {
            Text(
                text = "${stats.wins}W · ${stats.losses}L · ${stats.breakEvens}BE · ${stats.open} open",
                color = IosColors.TextSecondary,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun StatColumn(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    tint: Color = IosColors.TextPrimary
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = tint,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(3.dp))
        Text(text = label, color = IosColors.TextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun StatDivider() {
    Box(
        Modifier
            .width(0.5.dp)
            .height(30.dp)
            .background(IosColors.TextSecondary.copy(alpha = 0.18f))
    )
}

@Composable
private fun FollowRow(
    follow: SignalFollow,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = SettingsMetrics.rowPadding, vertical = 12.dp)
            .height(32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = follow.pair,
                    color = IosColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = follow.type.stored,
                    color = if (follow.type == SignalType.BUY) IosColors.BuyGreen
                    else IosColors.SellRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
            }
            Text(
                // The snapshot, not the signal's current numbers. This is what
                // they acted on, and it does not move when Tanha edits the call.
                text = "Entry ${follow.entryPrice} · ${dayMonth.format(follow.followedAt)}",
                color = IosColors.TextSecondary,
                fontSize = 13.sp
            )
        }

        when {
            follow.isSettled -> Column(horizontalAlignment = Alignment.End) {
                follow.resultPips?.let { pips ->
                    Text(
                        text = "${if (pips >= 0) "+" else ""}${pips.toInt()}",
                        color = if (pips >= 0) IosColors.BuyGreen else IosColors.SellRed,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(follow.outcome.label, color = IosColors.TextSecondary, fontSize = 11.sp)
            }

            follow.isActive -> Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(IosColors.BuyGreen)
                )
                Text(
                    "Following",
                    color = IosColors.BuyGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            else -> Text(
                "Add result",
                color = IosColors.Accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private val dayMonth = SimpleDateFormat("d MMM", Locale.getDefault())
