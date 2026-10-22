package com.talkswithtanha.twt.features.markets

import com.talkswithtanha.twt.core.designsystem.edgeShine
import com.talkswithtanha.twt.core.designsystem.WebFonts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talkswithtanha.twt.core.data.MarketsApi
import com.talkswithtanha.twt.core.designsystem.Haptics
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateColorAsState
import com.talkswithtanha.twt.core.designsystem.components.SkeletonCard
import com.talkswithtanha.twt.core.designsystem.components.Shimmer
import com.talkswithtanha.twt.core.designsystem.components.ChartSkeleton
import com.talkswithtanha.twt.core.designsystem.bouncyClick
import com.talkswithtanha.twt.core.designsystem.appear
import com.talkswithtanha.twt.core.designsystem.components.RollingNumber
import com.talkswithtanha.twt.features.home.components.SESSIONS
import com.talkswithtanha.twt.features.home.components.headline
import com.talkswithtanha.twt.features.home.components.openSessions
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Green = Color(0xFF22C55E)
private val Red = Color(0xFFEF4444)
private val Muted = Color.White.copy(alpha = 0.55f)
private val Faint = Color.White.copy(alpha = 0.35f)
private val CardBg = Color(0xFF141414)
private val Tabular = TextStyle(fontFamily = WebFonts.Body, fontFeatureSettings = "tnum")

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(CardBg)
            .border(0.5.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(24.dp))
            .edgeShine(RoundedCornerShape(24.dp))
            .appear()
    ) { content() }
}

@Composable
private fun Overline(text: String, color: Color = Muted) {
    Text(text.uppercase(), color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
}

/**
 * The website's live desk header: today's date, which sessions are open in
 * one line, and the four desks as pills with the open ones lit.
 */
@Composable
fun SessionHero() {
    val open = remember { openSessions() }
    val today = remember { SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date()) }

    Card {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Overline(today, Faint)
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(7.dp).clip(CircleShape).background(Green))
                Spacer(Modifier.width(6.dp))
                Overline("Live desk", Green)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                buildAnnotatedString {
                    append(headline(open))
                    append("\nHere’s what moves ")
                    withStyle(SpanStyle(color = Green, fontFamily = WebFonts.Serif, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Normal, fontSize = 32.sp)) {
                        append("gold.")
                    }
                },
                color = Color.White,
                fontSize = 28.sp,
                lineHeight = 30.sp,
                fontFamily = WebFonts.Display,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
            Spacer(Modifier.height(14.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SESSIONS.forEach { (name, _, _) ->
                    val lit = name in open
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (lit) Green.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.06f))
                            .border(0.5.dp, if (lit) Green.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 9.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(if (lit) Green else Faint))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            name.uppercase(),
                            color = if (lit) Green else Muted,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp
                        )
                    }
                }
            }
        }
    }
}

private val CHART_RANGES = listOf("1M", "3M", "6M", "1Y", "5Y")

/**
 * Gold over a range the member picks, as on the website's dashboard: the
 * change over the period, a filled line, prices up the right and dates
 * along the bottom. A tap opens the full chart sheet.
 */
@Composable
fun GoldChartCard(onOpen: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    var range by rememberSaveable { mutableStateOf("1Y") }
    var candles by remember { mutableStateOf<List<MarketsApi.Candle>?>(null) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(range) {
        candles = null
        failed = false
        val loaded = MarketsApi.history("gold", range)
        candles = loaded
        failed = loaded == null
    }

    val data = candles
    val change = data?.let { (it.last().close / it.first().close - 1) * 100 }
    val tint = if ((change ?: 0.0) >= 0) Green else Red

    Card(Modifier.bouncyClick(pressedScale = 0.985f, haptic = false) { Haptics.sheetOpen(haptics); onOpen() }) {
        Column(Modifier.padding(vertical = 16.dp)) {
            Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Overline("Gold · $range")
                Spacer(Modifier.width(10.dp))
                change?.let {
                    RollingNumber("%+.2f%%".format(it), color = tint, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .padding(horizontal = 18.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(3.dp)
            ) {
                CHART_RANGES.forEach { label ->
                    val selected = label == range
                    val fill by animateColorAsState(if (selected) Color.White.copy(alpha = 0.12f) else Color.Transparent, spring(stiffness = 500f), label = "rangeFill")
                    val ink by animateColorAsState(if (selected) Color.White else Muted, spring(stiffness = 500f), label = "rangeInk")
                    Text(
                        label,
                        color = ink,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        style = Tabular,
                        modifier = Modifier
                            .bouncyClick(pressedScale = 0.9f) { range = label }
                            .clip(RoundedCornerShape(15.dp))
                            .background(fill)
                            .padding(horizontal = 13.dp, vertical = 6.dp)
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(190.dp), contentAlignment = Alignment.Center) {
                when {
                    data != null -> AreaChart(data, tint, range)
                    failed -> Text("The chart could not be loaded.", color = Muted, fontSize = 13.sp)
                    else -> ChartSkeleton("Loading the gold chart", Modifier.fillMaxSize().padding(horizontal = 14.dp))
                }
            }
        }
    }
}

@Composable
private fun AreaChart(candles: List<MarketsApi.Candle>, color: Color, range: String) {
    val reveal = remember(candles) { Animatable(0f) }
    LaunchedEffect(candles) { reveal.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }

    val low = candles.minOf { it.close }
    val high = candles.maxOf { it.close }
    val pad = (high - low).takeIf { it > 0 }?.let { it * 0.06 } ?: (high * 0.001)
    val min = low - pad
    val max = high + pad
    val levels = (0..4).map { max - (max - min) * it / 4 }
    val datePattern = if (range == "1M") "d MMM" else "MMM yy"
    val dates = listOf(0, candles.size / 2, candles.lastIndex).map {
        SimpleDateFormat(datePattern, Locale.getDefault()).format(Date(candles[it].time))
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Row(Modifier.weight(1f)) {
            Canvas(Modifier.weight(1f).fillMaxSize().clipToBounds()) {
                val w = size.width
                val h = size.height
                fun y(v: Double) = (h - ((v - min) / (max - min)) * h).toFloat()
                fun x(i: Int) = w * i / (candles.size - 1)

                levels.indices.forEach { i ->
                    val gy = h * i / 4f
                    drawLine(Color.White.copy(alpha = 0.06f), Offset(0f, gy), Offset(w, gy), 1f)
                }
                clipRect(right = w * reveal.value) {
                    val line = Path()
                    candles.forEachIndexed { i, c ->
                        if (i == 0) line.moveTo(x(i), y(c.close)) else line.lineTo(x(i), y(c.close))
                    }
                    val fill = Path().apply {
                        addPath(line)
                        lineTo(w, h)
                        lineTo(0f, h)
                        close()
                    }
                    drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), Color.Transparent)))
                    drawPath(line, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                levels.forEach {
                    Text("%,.0f".format(it), color = Faint, fontSize = 10.sp, style = Tabular)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().padding(end = 44.dp)) {
            Text(dates[0], color = Faint, fontSize = 10.sp)
            Spacer(Modifier.weight(1f))
            Text(dates[1], color = Faint, fontSize = 10.sp)
            Spacer(Modifier.weight(1f))
            Text(dates[2], color = Faint, fontSize = 10.sp)
        }
    }
}

/** Three bars, lit by how much the release tends to move the market. */
@Composable
internal fun ImpactBars(impact: String) {
    val lit = when (impact) { "High" -> 3; "Medium" -> 2; "Low" -> 1; else -> 0 }
    val color = when (impact) { "High" -> Red; "Medium" -> Color(0xFFF59E0B); else -> Muted }
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(6, 9, 12).forEachIndexed { i, h ->
            Box(
                Modifier
                    .width(3.5.dp)
                    .height(h.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (i < lit) color else Color.White.copy(alpha = 0.15f))
            )
        }
    }
}

/**
 * The next release on the economic calendar, counting down to the second,
 * with its forecast and previous figures. [onCalendar], when given,
 * adds a link through to the full calendar. Moves on to the following one
 * when the clock runs out.
 */
@Composable
fun NextReleaseCard(events: List<MarketsApi.Event>?, onCalendar: (() -> Unit)? = null) {
    val haptics = LocalHapticFeedback.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    if (events == null) {
        SkeletonCard(label = "Loading the next release") {
            Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Shimmer(Modifier.width(90.dp), height = 18.dp)
                Shimmer(Modifier.width(210.dp), height = 40.dp, cornerRadius = 10.dp)
                Shimmer(Modifier.fillMaxWidth(0.8f), height = 16.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Shimmer(Modifier.weight(1f), height = 58.dp, cornerRadius = 14.dp)
                    Shimmer(Modifier.weight(1f), height = 58.dp, cornerRadius = 14.dp)
                }
            }
        }
        return
    }
    val next = events
        .filter { it.impact != "Holiday" && (it.at?.time ?: 0) > now }
        .minByOrNull { it.at!!.time } ?: return

    val left = ((next.at!!.time - now) / 1000).coerceAtLeast(0)
    val countdown = if (left >= 86_400) {
        "%dd %02d:%02d:%02d".format(left / 86_400, left % 86_400 / 3600, left % 3600 / 60, left % 60)
    } else {
        "%02d:%02d:%02d".format(left / 3600, left % 3600 / 60, left % 60)
    }
    val whenLabel = SimpleDateFormat("EEE HH:mm", Locale.getDefault()).format(next.at)

    Card {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Overline("Next release")
            Spacer(Modifier.weight(1f))
            Text(whenLabel, color = Faint, fontSize = 13.sp)
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.08f)))
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(currencyFlag(next.currency), fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Text(next.currency, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                ImpactBars(next.impact)
            }
            Spacer(Modifier.height(10.dp))
            RollingNumber(
                countdown,
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.Medium,
                style = TextStyle(fontFamily = WebFonts.Mono, letterSpacing = (-1).sp)
            )
            Spacer(Modifier.height(8.dp))
            Text(next.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Figure("Forecast", next.forecast, Modifier.weight(1f))
                Figure("Previous", next.previous, Modifier.weight(1f))
            }
        }
        if (onCalendar == null) return@Card
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.08f)))
        Text(
            "On the calendar →",
            color = Green,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .fillMaxWidth()
                .bouncyClick(pressedScale = 0.98f) { onCalendar() }
                .padding(horizontal = 18.dp, vertical = 14.dp)
        )
    }
}

@Composable
private fun Figure(label: String, value: String, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Overline(label, Faint)
        Spacer(Modifier.height(6.dp))
        Text(value.ifBlank { "—" }, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, style = Tabular)
    }
}
