package com.talkswithtanha.twt.features.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.talkswithtanha.twt.core.data.LiveApi
import com.talkswithtanha.twt.core.data.MarketsApi
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.designsystem.WebFonts
import com.talkswithtanha.twt.core.designsystem.appear
import com.talkswithtanha.twt.core.designsystem.bouncyClick
import com.talkswithtanha.twt.core.designsystem.brand.FeedIcons
import com.talkswithtanha.twt.core.designsystem.brand.PairFlagBadge
import com.talkswithtanha.twt.core.designsystem.brand.TabIcons
import com.talkswithtanha.twt.core.designsystem.components.EmptyState
import com.talkswithtanha.twt.core.designsystem.components.LoadingLabel
import com.talkswithtanha.twt.core.designsystem.components.Shimmer
import com.talkswithtanha.twt.core.designsystem.edgeShine
import com.talkswithtanha.twt.core.model.FeedPost
import com.talkswithtanha.twt.core.model.FilterType
import com.talkswithtanha.twt.core.model.PostType
import com.talkswithtanha.twt.core.model.Signal
import com.talkswithtanha.twt.core.model.SignalType
import com.talkswithtanha.twt.features.home.components.SESSIONS
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The feed, as a trader's desk rather than a timeline — the website's member
 * feed (twt-web/app/(member)/feed) on the phone.
 *
 * Opening it answers three things within seconds: is a trade running and
 * where is it (the setups and the desk), what is coming up (the strip and the
 * calendar), and what is new since the last visit (the divider and the dots).
 * Every post says what to do with it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeedScreen(
    onOpenPost: (String) -> Unit,
    onOpenSignal: (String) -> Unit,
    onOpenChats: () -> Unit,
    onCompose: () -> Unit,
    onAsk: (String) -> Unit,
    viewModel: FeedViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var deskOpen by rememberSaveable { mutableStateOf(false) }
    var pinnedOpen by rememberSaveable { mutableStateOf(true) }
    val filter = state.filter

    // ── What is shown ──
    val running = state.signals.values.filter { !it.isAnalysis && it.status.isOngoing }
    val pairs = (running.map { it.pair } + state.posts.flatMap { it.pairs }).distinct().take(12)
    fun matches(p: FeedPost): Boolean {
        if (filter.pair != null && filter.pair !in p.pairs) return false
        if (filter.unreadOnly && !state.isUnread(p)) return false
        return when (filter.type) {
            FilterType.ALL -> true
            FilterType.SAVED -> state.isSaved(p.id)
            FilterType.ANALYSIS -> p.type == PostType.NOTE || p.type == PostType.CHART
            FilterType.SETUP -> p.type == PostType.SETUP
            FilterType.VIDEO -> p.type == PostType.VIDEO
            FilterType.VOICE -> p.type == PostType.VOICE
        }
    }
    val atTop = filter.type == FilterType.ALL && filter.pair == null
    val pinned = if (atTop) state.pinned.take(2) else emptyList()
    val updated = if (atTop) state.updated.filter { u -> pinned.none { it.id == u.id } } else emptyList()
    val topIds = (pinned + updated).map { it.id }.toSet()
    val shown = state.posts.filter { it.id !in topIds && matches(it) }
    val visit = state.previousVisit
    val newCount = if (visit != null) state.posts.count { it.createdAt.time > visit } else 0
    val dividerAt = if (visit != null) shown.indexOfFirst { it.createdAt.time <= visit } else -1

    // New posts arriving while reading wait behind a pill rather than jump the list.
    var seenTop by remember { mutableStateOf<String?>(null) }
    val scrolledAway by remember { derivedStateOf { list.firstVisibleItemIndex > 3 } }
    LaunchedEffect(state.posts.firstOrNull()?.id, scrolledAway) {
        if (!scrolledAway) seenTop = state.posts.firstOrNull()?.id
    }
    val incoming = seenTop?.let { top -> state.posts.indexOfFirst { it.id == top }.takeIf { it > 0 } } ?: 0

    // Older posts as the reader reaches the end.
    val nearEnd by remember { derivedStateOf { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= list.layoutInfo.totalItemsCount - 3 } == true } }
    LaunchedEffect(nearEnd) { if (nearEnd) viewModel.loadMore() }

    fun card(post: FeedPost, updatedBadge: Boolean = false): @Composable () -> Unit = {
        val signal = post.signalId?.let(state.signals::get)
        LaunchedEffect(post.id) { viewModel.recordView(post.id) }
        FeedCard(
            post = post,
            signal = signal,
            quote = signal?.let { state.quoteFor(it.pair) },
            myReaction = state.myReactions[post.id],
            unread = state.isUnread(post),
            savedIn = state.feedState.saved[post.id],
            collections = state.feedState.collections,
            isFollowingSignal = post.signalId in state.followedSignalIds,
            canModerate = state.canPost,
            updates = signal?.let { remember(it.id) { viewModel.updatesFor(it.id) } } ?: NoUpdates,
            updatedBadge = updatedBadge,
            actions = CardActions(
                onOpen = { onOpenPost(post.id) },
                onReact = { viewModel.react(post, it) },
                onSave = { viewModel.save(post.id, it) },
                onPickPair = { pair ->
                    viewModel.setFilter(filter.copy(pair = pair, type = FilterType.ALL))
                    scope.launch { list.animateScrollToItem(0) }
                },
                onFollow = viewModel::follow,
                onUnfollow = viewModel::unfollow,
                onOpenSignal = { onOpenSignal(it.id) },
                onAsk = { state.user?.let { onAsk(it.id) } },
                onTogglePin = { viewModel.togglePin(post) },
                onDelete = { viewModel.delete(post) }
            ),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }

    Box(Modifier.fillMaxSize().background(IosColors.Background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            FeedTopBar(onOpenChats)

            LazyColumn(state = list, contentPadding = PaddingValues(bottom = 140.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item(key = "strip") { MarketStrip(running.map { it.pair }, state.events, state) }

                item(key = "desk") {
                    DeskFold(
                        open = deskOpen,
                        onToggle = { deskOpen = !deskOpen },
                        running = running,
                        events = state.events,
                        signals = state.signals.values.toList(),
                        state = state,
                        onOpenSignal = onOpenSignal
                    )
                }

                stickyHeader(key = "filters") {
                    FilterBar(
                        filter = filter,
                        pairs = pairs,
                        onChange = viewModel::setFilter
                    )
                }

                state.error?.let { message ->
                    item(key = "error") { Banner(message) }
                }

                if (state.isLoading) {
                    item(key = "loading") { LoadingLabel("Loading the feed", Modifier.padding(start = 20.dp, top = 4.dp)) }
                    items(3, key = { "sk$it" }) { PostSkeleton() }
                } else {
                    if (pinned.isNotEmpty()) {
                        item(key = "pinnedHead") {
                            Text(
                                "📌 Pinned  ${if (pinnedOpen) "−" else "+"}",
                                color = IosColors.TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = WebFonts.Body,
                                modifier = Modifier.padding(horizontal = 20.dp).clickable { pinnedOpen = !pinnedOpen }
                            )
                        }
                        if (pinnedOpen) items(pinned, key = { "pin-${it.id}" }) { card(it)() }
                    }
                    items(updated, key = { "upd-${it.id}" }) { card(it, updatedBadge = true)() }

                    if (shown.isEmpty()) {
                        item(key = "empty") {
                            EmptyState(icon = Icons.AutoMirrored.Outlined.Chat, title = emptyTitle(filter.type, filter.pair, filter.unreadOnly), message = emptyBody(filter.type, filter.unreadOnly))
                        }
                    }
                    itemsIndexed(shown, key = { _, p -> p.id }) { i, post ->
                        Column(Modifier.appear(i % 6), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (i == dividerAt && dividerAt > 0) NewDivider(newCount)
                            card(post)()
                        }
                    }
                    if (state.endReached && state.posts.isNotEmpty()) {
                        item(key = "end") {
                            Text("You are all caught up.", color = IosColors.TextSecondary, fontSize = 13.sp, fontFamily = WebFonts.Body, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            }
        }

        // ↑ N new posts, when they arrive while the member is reading further down.
        AnimatedVisibility(
            visible = scrolledAway && incoming > 0,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp)
        ) {
            Text(
                "↑ $incoming new post${if (incoming == 1) "" else "s"}",
                color = Color.Black,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = WebFonts.Body,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(IosColors.BuyGreen)
                    .bouncyClick {
                        seenTop = state.posts.firstOrNull()?.id
                        scope.launch { list.animateScrollToItem(0) }
                    }
                    .padding(horizontal = 16.dp, vertical = 9.dp)
            )
        }

        // Only Tanha sees it. The rules refuse a post from anybody else.
        if (state.canPost) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 120.dp)
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(IosColors.Accent)
                    .clickable(onClick = onCompose),
                contentAlignment = Alignment.Center
            ) { FeedIcons.Compose(Modifier.size(28.dp), Color.White) }
        }
    }
}

@Composable
private fun FeedTopBar(onOpenChats: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Feed", color = Color.White, style = MaterialTheme.typography.headlineLarge)
            Text("Setups, analysis, breakdowns and voice notes from the desk.", color = IosColors.TextSecondary, fontSize = 12.5.sp, fontFamily = WebFonts.Body)
        }
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(IosColors.SecondaryBackground).clickable(onClick = onOpenChats),
            contentAlignment = Alignment.Center
        ) { TabIcons.Chat(Modifier.size(19.dp), Color.White) }
    }
}

// ── The strip: sessions, the next big release, prices of what is running ──

private fun countdown(ms: Long): String {
    if (ms <= 0) return "now"
    val m = ms / 60_000
    val d = m / 1440
    val h = (m % 1440) / 60
    val min = m % 60
    return when {
        d > 0 -> "in ${d}d ${h}h"
        h > 0 -> "in ${h}h ${min}m"
        else -> "in ${min}m"
    }
}

private fun sessionLine(now: Long): String {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = now }
    val hour = cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60.0
    fun isOpen(open: Int, close: Int) = if (open < close) hour >= open && hour < close else hour >= open || hour < close
    val open = SESSIONS.filter { (_, o, c) -> isOpen(o, c) }.map { it.first }
    var soonest: Pair<String, Long>? = null
    SESSIONS.forEach { (city, o, c) ->
        val opened = isOpen(o, c)
        var delta = (if (opened) c else o) - hour
        if (delta <= 0) delta += 24
        val at = now + (delta * 3_600_000).toLong()
        if (soonest == null || at < soonest!!.second) soonest = "$city ${if (opened) "closes" else "opens"}" to at
    }
    val openText = if (open.isNotEmpty()) "${open.joinToString(" & ")} open" else "Markets quiet"
    return soonest?.let { "$openText · ${it.first} ${countdown(it.second - now)}" } ?: openText
}

@Composable
private fun MarketStrip(running: List<String>, events: List<MarketsApi.Event>?, state: FeedUiState) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    val next = events.orEmpty().firstOrNull { it.impact == "High" && (it.at?.time ?: 0L) > now }
    val fmt = remember { SimpleDateFormat("EEE HH:mm", Locale.getDefault()) }
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            StripChip {
                Box(Modifier.size(7.dp).clip(CircleShape).background(IosColors.BuyGreen))
                Spacer(Modifier.width(7.dp))
                Text(sessionLine(now), color = Color.White, fontSize = 12.5.sp, fontFamily = WebFonts.Body)
            }
        }
        if (next != null) {
            item {
                StripChip {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(IosColors.SellRed))
                    Spacer(Modifier.width(7.dp))
                    Text("${next.currency} ${next.title} · ${fmt.format(next.at!!)} · ${countdown(next.at.time - now)}", color = Color.White, fontSize = 12.5.sp, fontFamily = WebFonts.Body, maxLines = 1)
                }
            }
        }
        items(running.distinct().take(3)) { pair ->
            val q = state.quoteFor(pair) ?: return@items
            val decimals = LiveApi.find(pair)?.decimals ?: 2
            StripChip {
                PairFlagBadge(pair, size = 14.dp)
                Spacer(Modifier.width(6.dp))
                Text(pair, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, fontFamily = WebFonts.Body)
                Spacer(Modifier.width(6.dp))
                Text(String.format(Locale.US, "%.${decimals}f", q.price), color = Color.White, fontSize = 12.5.sp, fontFamily = WebFonts.Mono)
            }
        }
    }
}

@Composable
private fun StripChip(content: @Composable () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF111111))
            .border(0.8.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) { content() }
}

// ── The desk: active setups, calendar, track record — folded on a phone ──

@Composable
private fun DeskFold(
    open: Boolean,
    onToggle: () -> Unit,
    running: List<Signal>,
    events: List<MarketsApi.Event>?,
    signals: List<Signal>,
    state: FeedUiState,
    onOpenSignal: (String) -> Unit
) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF111111))
                .border(0.8.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(14.dp))
                .clickable(onClick = onToggle)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Active setups · calendar · track record", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, fontFamily = WebFonts.Body, modifier = Modifier.weight(1f))
            Text(if (open) "−" else "+", color = IosColors.TextSecondary, fontSize = 18.sp, fontFamily = WebFonts.Body)
        }
        if (open) {
            Widget("Active setups") {
                if (running.isEmpty()) {
                    Muted("No trade running right now. You are told the moment one opens.")
                } else {
                    running.take(6).forEach { x ->
                        val q = state.quoteFor(x.pair)
                        val dist = if (q != null && x.entryPriceValue != 0.0) Math.round((q.price - x.entryPriceValue) * pipScale(x.pair) * (if (x.type == SignalType.BUY) 1 else -1)).takeIf { abs(it) < 100_000 } else null
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onOpenSignal(x.id) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            val buy = x.type == SignalType.BUY
                            Text(if (buy) "▲ BUY" else "▼ SELL", color = if (buy) IosColors.BuyGreen else IosColors.SellRed, fontSize = 11.5.sp, fontWeight = FontWeight.Black, fontFamily = WebFonts.Body)
                            Spacer(Modifier.width(8.dp))
                            Text(x.pair, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = WebFonts.Body, modifier = Modifier.weight(1f))
                            Text(x.status.stored.lowercase().replaceFirstChar { it.uppercase() }, color = IosColors.TextSecondary, fontSize = 12.sp, fontFamily = WebFonts.Body)
                            if (dist != null) {
                                Spacer(Modifier.width(10.dp))
                                Text("${if (dist >= 0) "+" else ""}${dist}p", color = if (dist >= 0) IosColors.BuyGreen else IosColors.SellRed, fontSize = 12.5.sp, fontFamily = WebFonts.Mono)
                            }
                        }
                    }
                }
            }
            Widget("Economic calendar") {
                val now = System.currentTimeMillis()
                val next = events.orEmpty().filter { (it.impact == "High" || it.impact == "Medium") && (it.at?.time ?: 0L) > now }.take(5)
                val fmt = remember { SimpleDateFormat("EEE HH:mm", Locale.getDefault()) }
                when {
                    events == null -> Shimmer(Modifier.fillMaxWidth(), height = 60.dp, cornerRadius = 10.dp)
                    next.isEmpty() -> Muted("Nothing high-impact in the next two weeks.")
                    else -> next.forEach { e ->
                        Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                            Box(Modifier.padding(top = 6.dp).size(7.dp).clip(CircleShape).background(if (e.impact == "High") IosColors.SellRed else IosColors.Warning))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("${e.currency} · ${e.title}", color = Color.White, fontSize = 13.5.sp, fontFamily = WebFonts.Body)
                                Text(
                                    buildString {
                                        e.at?.let { append("${fmt.format(it)} · ${countdown(it.time - now)}") }
                                        if (e.forecast.isNotBlank()) append(" · F ${e.forecast}")
                                        if (e.previous.isNotBlank()) append(" · P ${e.previous}")
                                    },
                                    color = IosColors.TextSecondary,
                                    fontSize = 11.5.sp,
                                    fontFamily = WebFonts.Body
                                )
                            }
                        }
                    }
                }
            }
            TrackRecord(signals)
        }
    }
}

@Composable
private fun TrackRecord(signals: List<Signal>) {
    val start = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
    }.time
    val month = signals.filter { !it.isAnalysis && !it.timestamp.before(start) }
    val closed = month.filter { !it.status.isOngoing }
    val wins = closed.count { (it.pipsGained ?: 0.0) > 0 }
    val losses = closed.count { (it.pipsGained ?: 0.0) < 0 }
    val pips = closed.sumOf { it.pipsGained ?: 0.0 }
    val rs = closed.mapNotNull(::rMultiple)
    val totalR = if (rs.isEmpty()) null else rs.sum()
    val winRate = if (closed.isEmpty()) null else wins * 100 / closed.size
    val monthName = SimpleDateFormat("MMMM", Locale.getDefault()).format(start)
    Widget("Track record · $monthName") {
        Row(Modifier.fillMaxWidth()) {
            Stat("${closed.size}", "Closed", Color.White, Modifier.weight(1f))
            Stat(winRate?.let { "$it%" } ?: "—", "Win rate", Color.White, Modifier.weight(1f))
            Stat(
                totalR?.let { "${if (it >= 0) "+" else "−"}${"%.1f".format(abs(it))}R" } ?: "—",
                "Net",
                if ((totalR ?: 0.0) >= 0) IosColors.BuyGreen else IosColors.SellRed,
                Modifier.weight(1f)
            )
        }
        Muted("$wins won · $losses lost · ${closed.size - wins - losses} breakeven · ${if (pips >= 0) "+" else ""}${pips.roundToInt()} pips")
    }
}

@Composable
private fun Stat(value: String, label: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = WebFonts.Display)
        Text(label, color = IosColors.TextSecondary, fontSize = 11.5.sp, fontFamily = WebFonts.Body)
    }
}

@Composable
private fun Widget(title: String, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Color(0xFF111111)).border(0.8.dp, Color.White.copy(alpha = 0.09f), shape).edgeShine(shape, 0.5f).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(title, color = IosColors.TextSecondary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, fontFamily = WebFonts.Body)
        content()
    }
}

@Composable
private fun Muted(text: String) = Text(text, color = IosColors.TextSecondary, fontSize = 13.sp, fontFamily = WebFonts.Body)

// ── Filters ──

@Composable
private fun FilterBar(filter: com.talkswithtanha.twt.core.model.FeedFilter, pairs: List<String>, onChange: (com.talkswithtanha.twt.core.model.FeedFilter) -> Unit) {
    Column(Modifier.fillMaxWidth().background(IosColors.Background).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(FilterType.entries) { t ->
                val on = filter.type == t
                Text(
                    t.label,
                    color = if (on) Color.Black else Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = WebFonts.Body,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (on) Color.White else Color(0xFF151515))
                        .bouncyClick { onChange(filter.copy(type = t)) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            LazyRow(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { PairFilterChip("All pairs", null, filter.pair == null) { onChange(filter.copy(pair = null)) } }
                items(pairs) { p -> PairFilterChip(p, p, filter.pair == p) { onChange(filter.copy(pair = if (filter.pair == p) null else p)) } }
            }
            Text("Unread", color = IosColors.TextSecondary, fontSize = 12.sp, fontFamily = WebFonts.Body)
            Switch(
                checked = filter.unreadOnly,
                onCheckedChange = { onChange(filter.copy(unreadOnly = it)) },
                colors = SwitchDefaults.colors(checkedTrackColor = IosColors.BuyGreen),
                modifier = Modifier.padding(start = 4.dp, end = 12.dp).height(24.dp)
            )
        }
    }
}

@Composable
private fun PairFilterChip(label: String, pair: String?, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .border(0.8.dp, if (on) IosColors.BuyGreen else Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
            .background(if (on) IosColors.BuyGreen.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (pair != null) {
            PairFlagBadge(pair, size = 13.dp)
            Spacer(Modifier.width(5.dp))
        }
        Text(label, color = if (on) IosColors.BuyGreen else Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = WebFonts.Body)
    }
}

@Composable
private fun NewDivider(count: Int) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(IosColors.BuyGreen.copy(alpha = 0.35f)))
        Text("  $count new since your last visit  ", color = IosColors.BuyGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = WebFonts.Body)
        Box(Modifier.weight(1f).height(1.dp).background(IosColors.BuyGreen.copy(alpha = 0.35f)))
    }
}

@Composable
private fun Banner(text: String) {
    Text(
        text,
        color = IosColors.SellRed,
        fontSize = 13.sp,
        fontFamily = WebFonts.Body,
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(IosColors.SellRed.copy(alpha = 0.1f)).padding(12.dp)
    )
}

private fun emptyTitle(type: FilterType, pair: String?, unreadOnly: Boolean): String = when {
    type == FilterType.SAVED -> "Nothing saved yet"
    unreadOnly -> "You are all caught up"
    type == FilterType.SETUP -> if (pair != null) "No setups on $pair yet" else "No setups yet"
    pair != null -> "Nothing on $pair yet"
    else -> "Nothing here yet"
}

private fun emptyBody(type: FilterType, unreadOnly: Boolean): String = when {
    type == FilterType.SAVED -> "Tap the bookmark on any post to keep it here, sorted into collections."
    unreadOnly -> "Everything posted since your last visit has been read."
    type == FilterType.SETUP -> "Tap “Alert me” on a setup and you are told the moment the next one moves."
    else -> "Tanha’s posts land here as he makes them."
}

/** A card's shape while the feed loads. */
@Composable
private fun PostSkeleton() {
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFF111111)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Shimmer(Modifier.size(40.dp), height = 40.dp, cornerRadius = 20.dp)
            Spacer(Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Shimmer(Modifier.width(120.dp), height = 12.dp, cornerRadius = 6.dp)
                Shimmer(Modifier.width(70.dp), height = 9.dp, cornerRadius = 5.dp)
            }
        }
        Shimmer(Modifier.fillMaxWidth(0.95f), height = 12.dp, cornerRadius = 6.dp)
        Shimmer(Modifier.fillMaxWidth(0.7f), height = 12.dp, cornerRadius = 6.dp)
        Shimmer(Modifier.fillMaxWidth(), height = 160.dp, cornerRadius = 16.dp)
    }
}
