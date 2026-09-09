package com.talkswithtanha.twt.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.talkswithtanha.twt.core.designsystem.Radius
import com.talkswithtanha.twt.core.designsystem.Spacing
import com.talkswithtanha.twt.core.designsystem.TABULAR_FIGURES
import com.talkswithtanha.twt.core.designsystem.TwtColors
import com.talkswithtanha.twt.core.designsystem.components.PremiumBadge
import com.talkswithtanha.twt.core.designsystem.components.SignalCardSkeleton
import com.talkswithtanha.twt.core.designsystem.components.TwtCard
import com.talkswithtanha.twt.core.designsystem.components.TwtScreen
import com.talkswithtanha.twt.core.model.ExchangeRate
import com.talkswithtanha.twt.core.model.TradingStats
import com.talkswithtanha.twt.features.signals.SignalCard

/**
 * Home.
 *
 * The entry cards are **deliberately monochrome**. Giving Academy a blue and the
 * Marketplace a green would make the screen a colour wheel and would spend the
 * two colours that mean buy and sell on navigation. Gold appears twice at most:
 * the member badge, and the selected tab.
 */
@Composable
fun HomeScreen(
    onOpenSignal: (String) -> Unit,
    onOpenSignals: () -> Unit,
    onOpenAcademy: () -> Unit,
    onOpenMarketplace: () -> Unit,
    onOpenMySignals: () -> Unit,
    onOpenProfile: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val rate by viewModel.rate.collectAsStateWithLifecycle()

    TwtScreen {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                top = Spacing.huge,
                bottom = 120.dp
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Welcome back",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TwtColors.TextSecondary
                        )
                        Text(
                            text = user?.firstName ?: "Trader",
                            style = MaterialTheme.typography.displayMedium,
                            color = TwtColors.TextPrimary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(TwtColors.SurfaceElevated)
                            .clickableNoRipple(onOpenProfile),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.Person,
                            contentDescription = "Profile",
                            tint = TwtColors.TextSecondary
                        )
                    }
                }
            }

            if (user?.hasAppAccess == true) {
                item {
                    Row(modifier = Modifier.padding(top = Spacing.xs)) { PremiumBadge() }
                }
            }

            item { StatsCard(stats, onClick = onOpenMySignals) }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    EntryCard(
                        icon = Icons.Outlined.School,
                        title = "Academy",
                        subtitle = "Lessons",
                        onClick = onOpenAcademy,
                        modifier = Modifier.weight(1f)
                    )
                    EntryCard(
                        icon = Icons.Outlined.CurrencyExchange,
                        title = "Exchange",
                        subtitle = rate?.let {
                            "1 USD = ${ExchangeRate.formatPaisa(it.buyPaisa)}"
                        } ?: "USD / PKR",
                        onClick = onOpenMarketplace,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.md)
                        .clickableNoRipple(onOpenSignals),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LATEST SIGNALS",
                        style = MaterialTheme.typography.labelSmall,
                        color = TwtColors.TextTertiary
                    )
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = "All signals",
                        tint = TwtColors.TextTertiary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (state.isLoading) {
                items(2) { SignalCardSkeleton() }
            } else {
                items(state.latestSignals, key = { it.id }) { signal ->
                    SignalCard(
                        signal = signal,
                        isFollowing = false,
                        onClick = { onOpenSignal(signal.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsCard(stats: TradingStats, onClick: () -> Unit) {
    TwtCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "YOUR RECORD",
                style = MaterialTheme.typography.labelSmall,
                color = TwtColors.TextTertiary
            )
            Icon(
                Icons.Outlined.Timeline,
                contentDescription = null,
                tint = TwtColors.TextTertiary,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.height(Spacing.lg))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Stat(
                label = "Win rate",
                // Null rather than zero when nothing has settled. "0%" on a new
                // account is a true statement that reads as failure.
                value = stats.winRate?.let { "${(it * 100).toInt()}%" } ?: "—"
            )
            Stat(label = "Open", value = stats.open.toString())
            Stat(
                label = "Net pips",
                value = if (stats.settled == 0) "—"
                else "${if (stats.netPips >= 0) "+" else ""}${stats.netPips.toInt()}",
                color = when {
                    stats.settled == 0 -> TwtColors.TextPrimary
                    stats.netPips > 0 -> TwtColors.Buy
                    stats.netPips < 0 -> TwtColors.Sell
                    else -> TwtColors.TextPrimary
                }
            )
        }
    }
}

@Composable
private fun Stat(
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
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFeatureSettings = TABULAR_FIGURES
            ),
            color = color
        )
    }
}

@Composable
private fun EntryCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TwtCard(modifier = modifier, onClick = onClick) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(Radius.chip))
                .background(TwtColors.SurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            // Monochrome on purpose -- see the note on this screen.
            Icon(icon, contentDescription = null, tint = TwtColors.TextSecondary)
        }
        Spacer(Modifier.height(Spacing.md))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = TwtColors.TextPrimary
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelMedium,
            color = TwtColors.TextTertiary
        )
    }
}

/** Taps that move between screens should not ripple over a whole row. */
@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this.clickable(
        interactionSource = interaction,
        indication = null,
        onClick = onClick
    )
}
