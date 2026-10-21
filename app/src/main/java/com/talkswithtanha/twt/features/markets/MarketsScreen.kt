package com.talkswithtanha.twt.features.markets

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.SpaceDashboard
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talkswithtanha.twt.core.data.MarketsApi
import com.talkswithtanha.twt.core.designsystem.Haptics
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.designsystem.components.RollingNumber
import com.talkswithtanha.twt.features.home.components.InstrumentBadge
import com.talkswithtanha.twt.features.home.components.LiveDeskCard
import com.talkswithtanha.twt.features.home.components.MarketChartSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private val Up = Color(0xFF22C55E)
private val Down = Color(0xFFEF4444)
private val Amber = Color(0xFFF59E0B)
private val Muted = Color.White.copy(alpha = 0.55f)
private val CardBg = Color(0xFF141414)
private val Tabular = TextStyle(fontFeatureSettings = "tnum")

/** The website's Markets sections, in its order. */
private val PAGES = listOf("Dashboard", "Calendar", "Live news", "COT report", "Fed watch")
private val PAGE_ICONS = listOf(
    Icons.Rounded.SpaceDashboard,
    Icons.Rounded.CalendarMonth,
    Icons.Rounded.Newspaper,
    Icons.Rounded.BarChart,
    Icons.Rounded.AccountBalance
)

/**
 * The website's Markets area as a tab: the same five sections as its side
 * menu, as pages along the top. Tap a heading or swipe between them; a pill
 * slides under the one you are on. Everything comes from the website's own
 * feeds, loaded once here and shared by the pages.
 */
@Composable
fun MarketsScreen() {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(pageCount = { PAGES.size })

    var quotes by remember { mutableStateOf<List<MarketsApi.Quote>?>(null) }
    var fed by remember { mutableStateOf<MarketsApi.Fed?>(null) }
    var positioning by remember { mutableStateOf<Pair<String, List<MarketsApi.Positioning>>?>(null) }
    var events by remember { mutableStateOf<List<MarketsApi.Event>?>(null) }
    var charting by remember { mutableStateOf<MarketsApi.Quote?>(null) }

    LaunchedEffect(Unit) {
        launch { fed = MarketsApi.fed() }
        launch { positioning = MarketsApi.positioning() }
        launch { events = MarketsApi.calendar() }
        while (true) {
            MarketsApi.quotes()?.let { quotes = it }
            delay(60_000)
        }
    }

    Column(Modifier.fillMaxSize().background(IosColors.Background).statusBarsPadding()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
            Text("Markets", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        }

        PageTabs(
            selected = pager.currentPage,
            onSelect = { index ->
                Haptics.tap(haptics)
                scope.launch { pager.animateScrollToPage(index) }
            }
        )

        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
            when (PAGES[page]) {
                "Dashboard" -> DashboardPage(
                    quotes = quotes,
                    events = events,
                    onCalendar = { scope.launch { pager.animateScrollToPage(PAGES.indexOf("Calendar")) } },
                    onOpen = { charting = it }
                )
                "Calendar" -> CalendarPage(events)
                "Live news" -> NewsFeed(showTitle = false)
                "COT report" -> CotPage(positioning)
                else -> FedPage(fed)
            }
        }
    }

    charting?.let { MarketChartSheet(it, onDismiss = { charting = null }) }
}

/** The headings along the top: icon over label, with an underline that slides to the current one. */
@Composable
private fun PageTabs(selected: Int, onSelect: (Int) -> Unit) {
    val density = LocalDensity.current
    val bounds = remember { mutableStateMapOf<Int, Pair<Dp, Dp>>() }
    val target = bounds[selected]
    val lineX by animateDpAsState(target?.first ?: 0.dp, spring(dampingRatio = 0.8f, stiffness = 500f), label = "lineX")
    val lineW by animateDpAsState(target?.second ?: 0.dp, spring(dampingRatio = 0.8f, stiffness = 500f), label = "lineW")
    val scroll = rememberScrollState()

    // Keep the current heading in view, centred where the row allows.
    LaunchedEffect(selected, target) {
        val (x, w) = target ?: return@LaunchedEffect
        val viewport = scroll.viewportSize
        if (viewport == 0) return@LaunchedEffect
        val centre = with(density) { (x + w / 2 + 8.dp).roundToPx() }
        scroll.animateScrollTo((centre - viewport / 2).coerceIn(0, scroll.maxValue))
    }

    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Box(Modifier.fillMaxWidth().horizontalScroll(scroll).padding(horizontal = 8.dp)) {
            Row {
                PAGES.forEachIndexed { index, label ->
                    val isSelected = index == selected
                    val tint by animateColorAsState(
                        if (isSelected) Color.White else Color.White.copy(alpha = 0.45f),
                        label = "tabTint"
                    )
                    Column(
                        Modifier
                            .onGloballyPositioned { coords ->
                                with(density) {
                                    bounds[index] = coords.positionInParent().x.toDp() to coords.size.width.toDp()
                                }
                            }
                            .clickable { onSelect(index) }
                            .padding(horizontal = 16.dp)
                            .padding(top = 6.dp, bottom = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box {
                            Icon(PAGE_ICONS[index], contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                            if (label == "Live news") {
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 3.dp, y = (-2).dp)
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(IosColors.Background)
                                        .padding(1.5.dp)
                                        .clip(CircleShape)
                                        .background(Up)
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            label.uppercase(),
                            color = tint,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.6.sp,
                            maxLines = 1
                        )
                    }
                }
            }
            if (target != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = lineX)
                        .width(lineW)
                        .height(3.dp)
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(Color.White)
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.1f)))
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun PageList(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content
    )
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxWidth().padding(top = 60.dp), Alignment.Center) {
        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun SectionCard(title: String, trailing: String? = null, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(CardBg)
            .border(0.5.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(24.dp))
            .padding(vertical = 16.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title.uppercase(), color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
            Spacer(Modifier.weight(1f))
            trailing?.let { Text(it, color = Muted, fontSize = 12.sp) }
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

/** The day's change as a tinted pill; the number rolls and the tint eases across when it flips. */
@Composable
private fun ChangePill(change: Double, tint: Color) {
    val wash by animateColorAsState(tint.copy(alpha = 0.12f), spring(stiffness = 300f), label = "pillWash")
    Box(
        Modifier
            .width(74.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(wash)
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        RollingNumber("%+.2f%%".format(change), color = tint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ── Dashboard ────────────────────────────────────────────────────────────

@Composable
private fun DashboardPage(
    quotes: List<MarketsApi.Quote>?,
    events: List<MarketsApi.Event>?,
    onCalendar: () -> Unit,
    onOpen: (MarketsApi.Quote) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    PageList {
        item { SessionHero() }
        item { GoldChartCard(onOpen = { quotes?.firstOrNull { it.key == "gold" }?.let(onOpen) }) }
        item { NextReleaseCard(events, onCalendar) }
        item { LiveDeskCard(height = 242.dp, corner = 24.dp) }
        if (quotes == null) item { Loading() }
        quotes?.let { list ->
            item {
                SectionCard("Prices", "Tap for the chart") {
                    list.forEach { q ->
                        val tint = if (q.change >= 0) Up else Down
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    Haptics.sheetOpen(haptics)
                                    onOpen(q)
                                }
                                .padding(horizontal = 18.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            InstrumentBadge(q, 26)
                            Spacer(Modifier.width(12.dp))
                            Text(q.label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            RollingNumber("%,.${q.decimals}f".format(q.price), color = Color.White, fontSize = 15.sp)
                            Spacer(Modifier.width(10.dp))
                            ChangePill(q.change, tint)
                        }
                    }
                }
            }
        }
    }
}

// ── Calendar ─────────────────────────────────────────────────────────────

@Composable
private fun CalendarPage(events: List<MarketsApi.Event>?) {
    val haptics = LocalHapticFeedback.current
    var impact by rememberSaveable { mutableStateOf("High & medium") }
    var currency by rememberSaveable { mutableStateOf("All") }
    val now = System.currentTimeMillis()
    val dayFormat = SimpleDateFormat("EEEE d MMM", Locale.getDefault())
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    val currencies = listOf("All") + (events?.map { it.currency }?.filter { it.length == 3 && !it.equals("All", ignoreCase = true) }?.distinct()?.sorted() ?: emptyList())
    val shown = events.orEmpty()
        .filter { (it.at?.time ?: 0) >= now - 2 * 60 * 60 * 1000 }
        .filter {
            when (impact) {
                "High" -> it.impact == "High"
                "High & medium" -> it.impact == "High" || it.impact == "Medium"
                else -> true
            }
        }
        .filter { currency == "All" || it.currency == currency }
        .sortedBy { it.at?.time ?: 0 }
    val upNext = shown.firstOrNull { (it.at?.time ?: 0) > now }

    PageList {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipRow(listOf("High", "High & medium", "Everything"), impact) {
                    Haptics.tap(haptics); impact = it
                }
                ChipRow(currencies, currency, label = { if (it == "All") it else withFlag(it) }) {
                    Haptics.tap(haptics); currency = it
                }
            }
        }
        when {
            events == null -> item { Loading() }
            shown.isEmpty() -> item { Text("Nothing scheduled for that filter.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 20.dp)) }
            else -> shown.groupBy { it.at?.let { d -> dayFormat.format(d) } ?: "" }.forEach { (day, dayEvents) ->
                item {
                    SectionCard(if (dayEvents.first().at?.let(::isToday) == true) "Today" else day, "${dayEvents.size} events") {
                        dayEvents.forEach { e ->
                            if (e === upNext) UpNextMarker(e, now)
                            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(e.at?.let { timeFormat.format(it) } ?: "—", color = Muted, fontSize = 13.sp, style = Tabular, modifier = Modifier.width(46.dp))
                                Column(Modifier.width(58.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(currencyFlag(e.currency), fontSize = 15.sp)
                                        Spacer(Modifier.width(5.dp))
                                        Text(e.currency, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    ImpactBars(e.impact)
                                }
                                Spacer(Modifier.width(6.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(e.title, color = Color.White, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    val detail = listOfNotNull(
                                        e.forecast.takeIf { it.isNotBlank() }?.let { "Forecast $it" },
                                        e.previous.takeIf { it.isNotBlank() }?.let { "Previous $it" }
                                    ).joinToString(" · ")
                                    if (detail.isNotEmpty()) Text(detail, color = Muted, fontSize = 12.sp, style = Tabular)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A green strip above the next release still to come, with how long until it. */
@Composable
private fun UpNextMarker(e: MarketsApi.Event, now: Long) {
    val mins = (((e.at?.time ?: now) - now) / 60_000).coerceAtLeast(0)
    val inText = when {
        mins >= 24 * 60 -> "in ${mins / (24 * 60)}d ${mins % (24 * 60) / 60}h"
        mins >= 60 -> "in ${mins / 60}h ${mins % 60}m"
        else -> "in ${mins}m"
    }
    Row(
        Modifier.fillMaxWidth().background(Up.copy(alpha = 0.08f)).padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "UP NEXT",
            color = Up,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Up.copy(alpha = 0.16f)).padding(horizontal = 8.dp, vertical = 3.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(inText, color = Up, fontSize = 13.sp, style = Tabular)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Up.copy(alpha = 0.3f)))
    }
}

@Composable
private fun ChipRow(
    options: List<String>,
    selected: String,
    label: (String) -> String = { it },
    onSelect: (String) -> Unit
) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val isSelected = option == selected
            Text(
                label(option),
                color = if (isSelected) Color.Black else Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isSelected) Color.White else Color.White.copy(alpha = 0.08f))
                    .clickable { onSelect(option) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

// ── Fed watch ────────────────────────────────────────────────────────────

@Composable
private fun FedPage(fed: MarketsApi.Fed?) {
    val dateFormat = SimpleDateFormat("EEE d MMM yyyy", Locale.getDefault())
    PageList {
        if (fed == null) {
            item { Loading() }
            return@PageList
        }
        item {
            SectionCard("Fed funds rate") {
                Column(Modifier.padding(horizontal = 18.dp)) {
                    Text("%.2f – %.2f%%".format(fed.targetFrom, fed.targetTo), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, style = Tabular)
                    Text("Target range · effective %.2f%%".format(fed.effective), color = Muted, fontSize = 13.sp)
                }
            }
        }
        item {
            Text(
                "What traders are pricing for each upcoming meeting, from prediction-market odds.",
                color = Muted,
                fontSize = 14.sp
            )
        }
        val upcoming = fed.meetings.filter { (it.date?.time ?: Long.MAX_VALUE) > System.currentTimeMillis() - 86_400_000 }
        upcoming.forEachIndexed { index, meeting ->
            item {
                SectionCard(if (index == 0) "Next meeting" else "Meeting", meeting.date?.let { dateFormat.format(it) }) {
                    Column(Modifier.padding(horizontal = 18.dp)) {
                        val top = meeting.outcomes.maxByOrNull { it.second }
                        Text(
                            "${meeting.likeliest}" + (top?.let { " · %.0f%% likely".format(it.second) } ?: ""),
                            color = when {
                                meeting.likeliest.contains("Cut") -> Up
                                meeting.likeliest.contains("Hike") -> Down
                                else -> Amber
                            },
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(10.dp))
                        meeting.outcomes.forEach { (label, p) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, modifier = Modifier.width(78.dp))
                                Box(Modifier.weight(1f).height(8.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f))) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth((p / 100).toFloat().coerceIn(0.01f, 1f))
                                            .height(8.dp)
                                            .clip(CircleShape)
                                            .background(if (label == meeting.likeliest) Color.White else Color.White.copy(alpha = 0.35f))
                                    )
                                }
                                Text("%.0f%%".format(p), color = Muted, fontSize = 12.sp, style = Tabular, modifier = Modifier.width(44.dp).padding(start = 8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun isToday(date: Date): Boolean {
    val a = Calendar.getInstance()
    val b = Calendar.getInstance().apply { time = date }
    return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}
