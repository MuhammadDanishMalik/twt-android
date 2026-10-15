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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import com.talkswithtanha.twt.core.designsystem.Motion
import com.talkswithtanha.twt.core.designsystem.components.SegmentedControl
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.talkswithtanha.twt.core.model.SignalAsset
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

    // Which market. Held as the enum's name rather than the enum itself so it
    // survives process death, which rememberSaveable cannot do for an enum
    // without a Saver. Empty means every market, and that is the default: a
    // member opening the tab wants today's trades, not a filter to clear.
    var assetName by rememberSaveable { mutableStateOf("") }
    val asset = SignalAsset.entries.firstOrNull { it.name == assetName }

    // Counted off the whole feed, not the filtered one, so the numbers on the
    // chips do not change as the chips are used.
    val markets = remember(state.signals) {
        SignalAsset.entries
            .map { market -> market to state.signals.count { it.market == market } }
            .filter { it.second > 0 }
    }

    // Nothing left in the market they had picked — a Crypto filter with no
    // crypto trades is a screen that reads as broken. Falls back to all.
    LaunchedEffect(markets) {
        if (asset != null && markets.none { it.first == asset }) assetName = ""
    }

    val shown = remember(state.signals, asset) {
        if (asset == null) state.signals else state.signals.filter { it.market == asset }
    }

    // Analysis has no entry, stop or targets, so it is not a trade and does not
    // belong in a list of them — a ticket drawn with empty levels is a number
    // somebody could act on by mistake.
    val trades = shown.filter { !it.isAnalysis }
    val active = trades.filter { it.status.isOngoing }
    val closed = trades.filter { !it.status.isOngoing }
    val today = closed.filter { isToday(it.timestamp.time) }
    val older = closed.filterNot { isToday(it.timestamp.time) }

    // Which half of the screen is showing. Remembered across recomposition but
    // deliberately not across navigation: coming back to Signals should show
    // the trades, which is what the tab in the bar promises.
    var tab by rememberSaveable { mutableIntStateOf(0) }

    // A post Tanha marked as analysis belongs here whether or not it carries a
    // chart; a call belongs here only when he attached one, because a card with
    // an empty frame where the analysis should be is worse than one fewer card.
    val analysed = shown.filter { it.isAnalysis || it.teamImages.isNotEmpty() }

    Column(
        Modifier
            .fillMaxSize()
            .background(IosColors.Background)
            .statusBarsPadding()
    ) {
        SegmentedControl(
            options = listOf("Trades", "Analysis"),
            selectedIndex = tab,
            onSelect = { tab = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )

        // Slides rather than cuts, in the direction of travel, so the two
        // halves read as one screen seen two ways.
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                val forward = targetState > initialState
                val direction = if (forward) 1 else -1
                (slideInHorizontally(Motion.gentle()) { w -> direction * w } + fadeIn(Motion.quick()))
                    .togetherWith(
                        slideOutHorizontally(Motion.gentle()) { w -> -direction * w } +
                            fadeOut(Motion.quick())
                    )
            },
            label = "signalsTab"
        ) { selected ->
            if (selected == 1) {
                AnalysisTab(
                    isLoading = state.isLoading,
                    signals = analysed,
                    onOpenSignal = onOpenSignal
                )
            } else {
        LazyColumn(
            contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Above the first heading rather than under it: the row governs
            // everything below, closed trades included, and a filter sitting
            // inside one section reads as belonging only to that section.
            if (markets.size > 1) {
                item {
                    MarketChips(
                        markets = markets,
                        total = state.signals.size,
                        selected = asset,
                        onSelect = { assetName = it?.name ?: "" }
                    )
                }
            }

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
                            .animateItem()
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
                        modifier = Modifier.animateItem().padding(horizontal = 16.dp),
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
                        modifier = Modifier.animateItem().padding(horizontal = 16.dp),
                        onOpen = { onOpenSignal(signal.id) }
                    )
                }
            }

            // Under every list of signals, however long: the app shares trade
            // ideas, and Google Play's financial-services rules expect the risk
            // to be stated where they are shown.
            item {
                Text(
                    text = "For education only — not financial advice. Trading forex and gold " +
                        "carries a high risk of loss. Past results do not guarantee future results.",
                    color = IosColors.TextSecondary.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 20.dp)
                )
            }
        }
            }
        }
    }
}

/**
 * Gold, forex, crypto, indices — the row above the trades.
 *
 * ### Why the labels are not the enum's own
 *
 * `SignalAsset.stored` is what Firestore holds: "XAU", "FX", "Index". Those are
 * the trader's shorthand and they are right in the database, but a member who
 * joined last week reads "Gold" and "Forex". The stored form stays where it
 * belongs and the words people use are here.
 *
 * ### Why a market with nothing in it is not shown
 *
 * A chip that always leads to "No Active Signals" teaches members that the
 * filter is broken. The caller passes only the markets that have trades, and
 * drops the row entirely when there is just one — a single-choice filter is a
 * decoration.
 */
@Composable
private fun MarketChips(
    markets: List<Pair<SignalAsset, Int>>,
    total: Int,
    selected: SignalAsset?,
    onSelect: (SignalAsset?) -> Unit
) {
    val haptics = LocalHapticFeedback.current

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            MarketChip(
                label = "All",
                count = total,
                dot = null,
                isSelected = selected == null
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                onSelect(null)
            }
        }
        items(markets, key = { it.first.name }) { (market, count) ->
            MarketChip(
                label = market.display,
                count = count,
                dot = market.dot,
                isSelected = selected == market
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                onSelect(if (selected == market) null else market)
            }
        }
    }
}

@Composable
private fun MarketChip(
    label: String,
    count: Int,
    dot: Color?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    // Animated rather than swapped: the row is tapped repeatedly while someone
    // decides what they are looking for, and a hard colour cut each time reads
    // as the screen reloading.
    val fill by animateColorAsState(
        targetValue = if (isSelected) Color.White else Color(0xFF1C1C1E),
        animationSpec = Motion.quick(),
        label = "chipFill"
    )
    val text by animateColorAsState(
        targetValue = if (isSelected) Color.Black else IosColors.TextSecondary,
        animationSpec = Motion.quick(),
        label = "chipText"
    )

    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(fill)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Gold's dot is gold. It is the one market most of these members came
        // for, and it should be findable without reading.
        if (dot != null) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(RoundedCornerShape(50))
                    .background(dot)
            )
            Spacer(Modifier.width(7.dp))
        }
        Text(
            text = label,
            color = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "$count",
            color = text.copy(alpha = 0.6f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/** What a member calls this market, as opposed to what Firestore stores. */
private val SignalAsset.display: String
    get() = when (this) {
        SignalAsset.GOLD -> "Gold"
        SignalAsset.FOREX -> "Forex"
        SignalAsset.CRYPTO -> "Crypto"
        SignalAsset.INDICES -> "Indices"
    }

/** Only gold gets one; the rest would be colour for its own sake. */
private val SignalAsset.dot: Color?
    get() = if (this == SignalAsset.GOLD) IosColors.Gold else null

@Composable
private fun AnalysisTab(
    isLoading: Boolean,
    signals: List<Signal>,
    onOpenSignal: (String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(top = 6.dp, bottom = 120.dp)
    ) {
        when {
            isLoading -> items(2) { index ->
                SignalCardSkeleton(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .staggeredAppear(index)
                )
            }

            signals.isEmpty() -> item { AnalysisEmpty() }

            else -> item {
                AnalysisFeed(
                    signals = signals,
                    onOpenSignal = onOpenSignal
                )
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
