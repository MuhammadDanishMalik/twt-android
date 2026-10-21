package com.talkswithtanha.twt.features.markets

import com.talkswithtanha.twt.core.designsystem.WebFonts
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talkswithtanha.twt.core.data.MarketsApi
import com.talkswithtanha.twt.core.designsystem.Haptics
import com.talkswithtanha.twt.core.designsystem.components.rememberChunks
import com.talkswithtanha.twt.core.designsystem.components.Shimmer
import com.talkswithtanha.twt.core.designsystem.components.LoadingLabel
import com.talkswithtanha.twt.core.designsystem.components.LoadMoreButton
import com.talkswithtanha.twt.core.designsystem.bouncyClick
import com.talkswithtanha.twt.core.designsystem.appear
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateColorAsState
import com.talkswithtanha.twt.core.designsystem.IosColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Muted = Color.White.copy(alpha = 0.55f)
private val CardBg = Color(0xFF141414)

/** The filters along the top: label → the tags that count. Empty = everything. */
private val FILTERS = listOf(
    "All" to emptySet(),
    "Gold" to setOf("XAU"),
    "Dollar" to setOf("USD"),
    "Forex" to setOf("EUR", "GBP", "JPY", "AUD", "CAD", "CHF", "NZD"),
    "Oil" to setOf("OIL"),
    "Silver" to setOf("XAG")
)

/** The filters' icons — a flag or symbol for what each one covers. */
private val FILTER_ICONS = mapOf(
    "Gold" to "🥇", "Dollar" to "🇺🇸", "Forex" to "💱", "Oil" to "🛢️", "Silver" to "🥈"
)

/** What each tag is shown as on a story, after its flag. */
private val TAG_LABELS = mapOf(
    "XAU" to "Gold", "XAG" to "Silver", "OIL" to "Oil", "USD" to "Dollar",
    "EUR" to "Euro", "GBP" to "Pound", "JPY" to "Yen", "AUD" to "Aussie",
    "CAD" to "Loonie", "CHF" to "Franc", "NZD" to "Kiwi"
)

/**
 * Market news, the same stream as the website's news page: the latest
 * headlines from the wires, filtered to what moves gold and the majors.
 * A tap opens the full story; pull down to refresh.
 */
@Composable
fun NewsScreen() {
    NewsFeed(showTitle = true, modifier = Modifier.background(IosColors.Background).statusBarsPadding())
}

/**
 * The news list itself — the News tab, and the Live news page inside
 * Markets. [showTitle] adds what the tab has on top: the big "News"
 * heading, the live desk and the next release on the calendar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsFeed(showTitle: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var stories by remember { mutableStateOf<List<MarketsApi.Story>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf("All") }
    var tick by remember { mutableIntStateOf(0) }
    var events by remember { mutableStateOf<List<MarketsApi.Event>?>(null) }
    // Ten stories at a time; changing the filter starts again from the top ten.
    val chunks = rememberChunks(initial = 10, key = filter)

    suspend fun load() {
        val loaded = MarketsApi.news()
        if (loaded != null) stories = loaded
        failed = loaded == null && stories == null
    }

    LaunchedEffect(Unit) {
        if (showTitle) launch { events = MarketsApi.calendar() }
        while (true) {
            load()
            delay(5 * 60_000)
            tick++
        }
    }

    val tags = FILTERS.first { it.first == filter }.second
    val shown = stories?.filter { tags.isEmpty() || it.tags.any { t -> t in tags } }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            scope.launch {
                refreshing = true
                load()
                refreshing = false
            }
        },
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column {
                    if (showTitle) {
                        Text("News", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, fontFamily = WebFonts.Display)
                        Text("What is moving gold and the majors", color = Muted, fontSize = 14.sp)
                        Spacer(Modifier.height(12.dp))
                        SessionHero()
                        Spacer(Modifier.height(10.dp))
                        NextReleaseCard(events)
                        Spacer(Modifier.height(14.dp))
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FILTERS.forEach { (label, _) ->
                            val selected = label == filter
                            val fill by animateColorAsState(if (selected) Color.White else Color.White.copy(alpha = 0.08f), spring(stiffness = 500f), label = "filterFill")
                            val ink by animateColorAsState(if (selected) Color.Black else Color.White, spring(stiffness = 500f), label = "filterInk")
                            Text(
                                FILTER_ICONS[label]?.let { "$it $label" } ?: label,
                                color = ink,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .bouncyClick(pressedScale = 0.93f) { filter = label }
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(fill)
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }

            when {
                shown == null && failed -> item {
                    Text("News could not be loaded. Pull down to try again.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 24.dp))
                }
                shown == null -> {
                    item { LoadingLabel("Loading the latest headlines", Modifier.padding(start = 4.dp, top = 4.dp)) }
                    items(5) { StorySkeleton() }
                }
                shown.isEmpty() -> item {
                    Text("No $filter stories right now.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 24.dp))
                }
                else -> {
                    itemsIndexed(shown.take(chunks.shown), key = { _, story -> story.link }) { index, story ->
                        StoryRow(story, now = tick.let { System.currentTimeMillis() }, order = index % 10) {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(story.link))) }
                        }
                    }
                    if (chunks.loading) items(2) { StorySkeleton() }
                    if (shown.size > chunks.shown) item {
                        LoadMoreButton(
                            loading = chunks.loading,
                            onClick = { scope.launch { chunks.more() } },
                            label = "More headlines",
                            remaining = "${shown.size - chunks.shown} left"
                        )
                    }
                }
            }
        }
    }
}

/** A story card's shape while the headlines load: source line, two lines of title, a summary line. */
@Composable
private fun StorySkeleton() {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardBg)
            .border(0.5.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Shimmer(Modifier.width(120.dp), height = 10.dp, cornerRadius = 5.dp)
        Shimmer(Modifier.fillMaxWidth(0.92f), height = 14.dp, cornerRadius = 7.dp)
        Shimmer(Modifier.fillMaxWidth(0.66f), height = 14.dp, cornerRadius = 7.dp)
        Shimmer(Modifier.fillMaxWidth(0.8f), height = 10.dp, cornerRadius = 5.dp)
    }
}

@Composable
private fun StoryRow(story: MarketsApi.Story, now: Long, order: Int, onOpen: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .appear(order)
            .bouncyClick(pressedScale = 0.98f, onClick = onOpen)
            .clip(RoundedCornerShape(18.dp))
            .background(CardBg)
            .border(0.5.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(story.source, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            story.at?.let {
                Box(Modifier.padding(horizontal = 6.dp).size(3.dp).clip(CircleShape).background(Muted))
                Text(ago(it.time, now), color = Muted, fontSize = 12.sp)
            }
            Spacer(Modifier.weight(1f))
            story.tags.take(2).forEach { tag ->
                Text(
                    "${currencyFlag(tag)} ${TAG_LABELS[tag] ?: tag}",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(story.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (story.summary.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(story.summary, color = Muted, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun ago(time: Long, now: Long): String {
    val mins = ((now - time) / 60_000).coerceAtLeast(0)
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "${mins}m ago"
        mins < 24 * 60 -> "${mins / 60}h ago"
        else -> "${mins / (24 * 60)}d ago"
    }
}
