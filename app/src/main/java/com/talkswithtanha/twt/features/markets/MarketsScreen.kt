package com.talkswithtanha.twt.features.markets

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talkswithtanha.twt.core.data.MarketsApi
import com.talkswithtanha.twt.core.designsystem.Haptics
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.features.home.components.InstrumentBadge
import com.talkswithtanha.twt.features.home.components.LiveDeskCard
import com.talkswithtanha.twt.features.home.components.MarketChartSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val Up = Color(0xFF22C55E)
private val Down = Color(0xFFEF4444)
private val Amber = Color(0xFFF59E0B)
private val Muted = Color.White.copy(alpha = 0.55f)
private val CardBg = Color(0xFF141414)
private val Tabular = TextStyle(fontFeatureSettings = "tnum")

/**
 * The website's Markets page as a tab: the live desk, every price with its
 * chart a tap away, the Fed's next move, how the big money is positioned, and
 * the week's economic calendar. All from the website's own feeds, so the app
 * and the site never disagree.
 */
@Composable
fun MarketsScreen() {
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

    LazyColumn(
        Modifier.fillMaxSize().background(IosColors.Background).statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text("Markets", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text("Live prices, the Fed, positioning and this week's calendar", color = Muted, fontSize = 14.sp)
            }
        }
        item { LiveDeskCard(height = 242.dp, corner = 24.dp) }
        quotes?.let { list -> item { PricesCard(list) { charting = it } } }
        fed?.let { item { FedCard(it) } }
        positioning?.let { (asOf, rows) -> item { PositioningCard(asOf, rows) } }
        events?.let { item { CalendarCard(it) } }
    }

    charting?.let { MarketChartSheet(it, onDismiss = { charting = null }) }
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

// ── Prices ───────────────────────────────────────────────────────────────

@Composable
private fun PricesCard(quotes: List<MarketsApi.Quote>, onOpen: (MarketsApi.Quote) -> Unit) {
    val haptics = LocalHapticFeedback.current
    SectionCard("Prices", "Tap for the chart") {
        quotes.forEach { q ->
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
                Text("%,.${q.decimals}f".format(q.price), color = Color.White, fontSize = 15.sp, style = Tabular)
                Spacer(Modifier.width(10.dp))
                Text(
                    "%+.2f%%".format(q.change),
                    color = tint,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    style = Tabular,
                    modifier = Modifier
                        .width(74.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(tint.copy(alpha = 0.12f))
                        .padding(vertical = 4.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

// ── The Fed ──────────────────────────────────────────────────────────────

@Composable
private fun FedCard(fed: MarketsApi.Fed) {
    val next = fed.meetings.firstOrNull { (it.date?.time ?: 0) > System.currentTimeMillis() } ?: fed.meetings.firstOrNull()
    SectionCard("The Fed", "Rate now %.2f–%.2f%%".format(fed.targetFrom, fed.targetTo)) {
        Column(Modifier.padding(horizontal = 18.dp)) {
            if (next == null) {
                Text("No meeting priced yet.", color = Muted, fontSize = 14.sp)
                return@Column
            }
            val top = next.outcomes.maxByOrNull { it.second }
            Text(
                "Next meeting ${next.date?.let { SimpleDateFormat("d MMM", Locale.getDefault()).format(it) } ?: ""}",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Markets expect: ${next.likeliest}" + (top?.let { " · %.0f%% likely".format(it.second) } ?: ""),
                color = if (next.likeliest.contains("Cut")) Up else if (next.likeliest.contains("Hike")) Down else Amber,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            next.outcomes.forEach { (label, p) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, modifier = Modifier.width(78.dp))
                    Box(Modifier.weight(1f).height(8.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f))) {
                        Box(
                            Modifier
                                .fillMaxWidth((p / 100).toFloat().coerceIn(0.01f, 1f))
                                .height(8.dp)
                                .clip(CircleShape)
                                .background(if (label == next.likeliest) Color.White else Color.White.copy(alpha = 0.35f))
                        )
                    }
                    Text("%.0f%%".format(p), color = Muted, fontSize = 12.sp, style = Tabular, modifier = Modifier.width(44.dp).padding(start = 8.dp))
                }
            }
        }
    }
}

// ── Positioning ──────────────────────────────────────────────────────────

@Composable
private fun PositioningCard(asOf: String, rows: List<MarketsApi.Positioning>) {
    SectionCard("Managed money", "CFTC · as of $asOf") {
        Column(Modifier.padding(horizontal = 18.dp)) {
            Text(
                "How the big speculators are positioned — the share of their bets that are long.",
                color = Muted,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(8.dp))
            rows.forEach { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(r.flag, fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(r.label, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(118.dp))
                    Row(Modifier.weight(1f).height(8.dp).clip(CircleShape)) {
                        Box(Modifier.weight(r.longPercent.coerceIn(1, 99).toFloat()).height(8.dp).background(Up))
                        Box(Modifier.weight((100 - r.longPercent).coerceIn(1, 99).toFloat()).height(8.dp).background(Down))
                    }
                    Text(
                        "${r.longPercent}%",
                        color = if (r.longPercent >= 50) Up else Down,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        style = Tabular,
                        modifier = Modifier.width(48.dp).padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

// ── Economic calendar ────────────────────────────────────────────────────

@Composable
private fun CalendarCard(events: List<MarketsApi.Event>) {
    val haptics = LocalHapticFeedback.current
    var importantOnly by rememberSaveable { mutableStateOf(true) }
    val now = System.currentTimeMillis()
    val upcoming = events
        .filter { (it.at?.time ?: 0) >= now - 2 * 60 * 60 * 1000 }
        .filter { !importantOnly || it.impact == "High" || it.impact == "Medium" }
        .sortedBy { it.at?.time ?: 0 }
        .take(40)
    val dayFormat = SimpleDateFormat("EEEE d MMM", Locale.getDefault())
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    SectionCard("This week's calendar") {
        Row(Modifier.padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(true to "High & medium", false to "Everything").forEach { (value, label) ->
                val selected = importantOnly == value
                Text(
                    label,
                    color = if (selected) Color.Black else Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) Color.White else Color.White.copy(alpha = 0.08f))
                        .clickable {
                            Haptics.tap(haptics)
                            importantOnly = value
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        if (upcoming.isEmpty()) {
            Text("Nothing scheduled.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
        }
        var lastDay = ""
        upcoming.forEach { e ->
            val day = e.at?.let { dayFormat.format(it) } ?: ""
            if (day != lastDay) {
                lastDay = day
                Text(
                    if (e.at != null && isToday(e.at)) "Today" else day,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 4.dp)
                )
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(e.at?.let { timeFormat.format(it) } ?: "—", color = Muted, fontSize = 13.sp, style = Tabular, modifier = Modifier.width(46.dp))
                Box(
                    Modifier.size(8.dp).clip(CircleShape).background(
                        when (e.impact) {
                            "High" -> Down
                            "Medium" -> Amber
                            "Holiday" -> Color(0xFF64748B)
                            else -> Color.White.copy(alpha = 0.3f)
                        }
                    )
                )
                Spacer(Modifier.width(8.dp))
                Text(e.currency, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp))
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

private fun isToday(date: Date): Boolean {
    val a = Calendar.getInstance()
    val b = Calendar.getInstance().apply { time = date }
    return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}
