package com.talkswithtanha.twt.features.markets

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talkswithtanha.twt.core.data.MarketsApi
import com.talkswithtanha.twt.core.designsystem.Haptics
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

private val Buy = Color(0xFF22C55E)
private val Sell = Color(0xFFEF4444)
private val Muted = Color.White.copy(alpha = 0.55f)
private val Faint = Color.White.copy(alpha = 0.38f)
private val CardBg = Color(0xFF141414)
private val Mono = TextStyle(fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum")

/** About three years of weeks, for the sparklines in the list. */
private const val SPARK_WEEKS = 156

private fun signed(n: Long) = (if (n >= 0) "+" else "−") + "%,d".format(kotlin.math.abs(n))
private fun tint(n: Long) = if (n >= 0) Buy else Sell

/** Metals and oil report managed money; currencies, the dollar and bitcoin report leveraged funds. */
private fun specLabel(r: MarketsApi.Positioning) = if (r.group == "metal") "Managed money" else "Leveraged funds"

/**
 * The website's COT report, laid out for a phone: pick a market, read five
 * years of speculators against commercials, the week by trader group, then
 * every market with its three-year line. Tapping a market in the list
 * brings it up top.
 */
@Composable
internal fun CotPage(data: Pair<String, List<MarketsApi.Positioning>>?) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    var focusKey by rememberSaveable { mutableStateOf("gold") }

    if (data == null) {
        Box(Modifier.fillMaxWidth().padding(top = 60.dp), Alignment.Center) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
        }
        return
    }
    val (asOf, rows) = data
    val focus = rows.firstOrNull { it.key == focusKey } ?: rows.firstOrNull() ?: return

    fun choose(key: String) {
        Haptics.tap(haptics)
        focusKey = key
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        state = list,
        contentPadding = PaddingValues(top = 4.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(rows, key = { it.key }) { r ->
                    val selected = r.key == focus.key
                    Text(
                        "${r.flag}  ${r.label}",
                        color = if (selected) Color.Black else Color.White.copy(alpha = 0.85f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (selected) Color.White else Color.White.copy(alpha = 0.07f))
                            .clickable { choose(r.key) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
        item(key = "chart-${focus.key}") { Box(Modifier.padding(horizontal = 16.dp)) { PositionCard(focus) } }
        item(key = "groups-${focus.key}") { Box(Modifier.padding(horizontal = 16.dp)) { GroupsCard(focus) } }
        item {
            Box(Modifier.padding(horizontal = 16.dp)) {
                MarketsCard(rows, focus.key) { key ->
                    choose(key)
                    scope.launch { list.animateScrollToItem(0) }
                }
            }
        }
        item {
            Text(
                "CFTC Commitments of Traders, as of $asOf. Managed money for metals and oil, leveraged funds for currencies. " +
                    "COT index: this week's net within its three-year range, 0 the most short and 100 the most long.",
                color = Faint,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(CardBg)
            .border(0.5.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(24.dp))
    ) { content() }
}

@Composable
private fun CardHeader(title: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title.uppercase(), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp, modifier = Modifier.weight(1f))
        trailing?.let { Text(it, color = Faint, fontSize = 13.sp) }
    }
    Hairline()
}

@Composable
private fun Hairline() {
    Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.08f)))
}

// ── Five years: speculators as bars, commercials as a line ──────────────────

@Composable
private fun PositionCard(r: MarketsApi.Positioning) {
    var hover by remember(r.key) { mutableStateOf<Int?>(null) }
    val point = hover?.let { r.history.getOrNull(it) }

    Card {
        CardHeader("${r.label} · ${specLabel(r)} net, 5 years", r.index?.let { "COT index $it" })
        if (r.history.size < 2) {
            Text("Not enough history.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(18.dp))
            return@Card
        }
        Box(Modifier.padding(start = 10.dp, end = 14.dp, top = 14.dp)) {
            PositionChart(r.history, hover) { hover = it }
        }
        Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 10.dp, height = 10.dp).clip(RoundedCornerShape(2.dp)).background(Buy.copy(alpha = 0.55f)))
            Spacer(Modifier.width(6.dp))
            Text(specLabel(r), color = Muted, fontSize = 12.sp)
            Spacer(Modifier.width(16.dp))
            Box(Modifier.size(width = 14.dp, height = 2.dp).background(BrandLine))
            Spacer(Modifier.width(6.dp))
            Text("Commercials", color = Muted, fontSize = 12.sp)
        }
        // The readout sits under the chart: a finger on the chart would cover a tooltip.
        Column(
            Modifier
                .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 11.dp)
        ) {
            if (point == null) {
                Text("Touch and drag across the chart to read any week.", color = Faint, fontSize = 13.sp)
            } else {
                Text(weekLabel(point.date), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                ReadoutLine(specLabel(r), signed(point.net), tint(point.net))
                ReadoutLine("Commercials", signed(point.commercialNet), tint(point.commercialNet))
                ReadoutLine("Open interest", "%,d".format(point.openInterest), Color.White)
            }
        }
    }
}

/** Commercials — the hedgers on the other side — in the brand's gold, so the two read apart. */
private val BrandLine = Color(0xFFE8B931)

@Composable
private fun ReadoutLine(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(label, color = Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, style = Mono)
    }
}

private fun weekLabel(iso: String): String = runCatching {
    val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso)!!
    "Week of " + SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(date)
}.getOrDefault(iso)

private fun shortK(v: Double): String =
    if (kotlin.math.abs(v) >= 1000) "${if (v > 0) "+" else if (v < 0) "−" else ""}${(kotlin.math.abs(v) / 1000).roundToInt()}k" else v.roundToInt().toString()

@Composable
private fun PositionChart(history: List<MarketsApi.CotWeek>, hover: Int?, onHover: (Int?) -> Unit) {
    val measurer = rememberTextMeasurer()
    val labelStyle = Mono.copy(color = Faint, fontSize = 9.sp)
    val grow = remember(history) { Animatable(0f) }
    LaunchedEffect(history) { grow.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }

    val max = maxOf(history.maxOf { maxOf(it.net, it.commercialNet) }, 0L).toDouble()
    val min = minOf(history.minOf { minOf(it.net, it.commercialNet) }, 0L).toDouble()
    val span = (max - min).takeIf { it > 0 } ?: 1.0

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(220.dp)
            .pointerInput(history) {
                val left = 40.dp.toPx()
                fun at(x: Float) = (((x - left) / (size.width - left)) * (history.size - 1)).roundToInt().coerceIn(0, history.lastIndex)
                detectTapGestures(onPress = { onHover(at(it.x)) })
            }
            .pointerInput(history) {
                val left = 40.dp.toPx()
                fun at(x: Float) = (((x - left) / (size.width - left)) * (history.size - 1)).roundToInt().coerceIn(0, history.lastIndex)
                detectHorizontalDragGestures(onDragStart = { onHover(at(it.x)) }) { change, _ ->
                    change.consume()
                    onHover(at(change.position.x))
                }
            }
    ) {
        val padLeft = 40.dp.toPx()
        val padBottom = 18.dp.toPx()
        val plotW = size.width - padLeft
        val plotH = size.height - padBottom - 4.dp.toPx()
        fun y(v: Double) = (4.dp.toPx() + plotH - ((v - min) / span) * plotH).toFloat()
        fun x(i: Int) = padLeft + plotW * i / (history.size - 1)
        val zero = y(0.0)

        listOf(max, max / 2, 0.0, min / 2, min).distinct().forEach { t ->
            val ty = y(t)
            drawLine(Color.White.copy(alpha = 0.06f), Offset(padLeft, ty), Offset(size.width, ty), 1f)
            val text = measurer.measure(shortK(t), labelStyle)
            drawText(text, topLeft = Offset(padLeft - 6.dp.toPx() - text.size.width, ty - text.size.height / 2))
        }

        val barW = (plotW / history.size - 0.6f).coerceAtLeast(1f)
        history.forEachIndexed { i, w ->
            val top = zero + (y(w.net.toDouble()) - zero) * grow.value
            val color = if (w.net >= 0) Buy else Sell
            drawRect(
                color.copy(alpha = if (hover == i) 0.95f else 0.5f),
                Offset(x(i) - barW / 2, minOf(top, zero)),
                Size(barW, kotlin.math.abs(top - zero).coerceAtLeast(0.5f))
            )
        }
        drawLine(Color.White.copy(alpha = 0.3f), Offset(padLeft, zero), Offset(size.width, zero), 1f)

        clipRect(right = padLeft + plotW * grow.value) {
            val line = Path()
            history.forEachIndexed { i, w ->
                if (i == 0) line.moveTo(x(i), y(w.commercialNet.toDouble())) else line.lineTo(x(i), y(w.commercialNet.toDouble()))
            }
            drawPath(line, BrandLine, style = Stroke(1.6.dp.toPx(), join = StrokeJoin.Round))
        }

        // One label per year, at its first week, skipped when it would crowd the last.
        var lastLabelEnd = -1f
        history.forEachIndexed { i, w ->
            if (i > 0 && history[i - 1].date.take(4) == w.date.take(4)) return@forEachIndexed
            val text = measurer.measure(w.date.take(4), labelStyle)
            val lx = x(i)
            if (lx < lastLabelEnd + 6.dp.toPx() || lx + text.size.width > size.width) return@forEachIndexed
            drawText(text, topLeft = Offset(lx, size.height - text.size.height))
            lastLabelEnd = lx + text.size.width
        }

        hover?.let { i ->
            val w = history[i]
            drawLine(
                Color.White.copy(alpha = 0.35f), Offset(x(i), 4.dp.toPx()), Offset(x(i), 4.dp.toPx() + plotH), 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
            )
            drawCircle(CardBg, 5.dp.toPx(), Offset(x(i), y(w.net.toDouble())))
            drawCircle(if (w.net >= 0) Buy else Sell, 3.5.dp.toPx(), Offset(x(i), y(w.net.toDouble())))
            drawCircle(CardBg, 5.dp.toPx(), Offset(x(i), y(w.commercialNet.toDouble())))
            drawCircle(BrandLine, 3.5.dp.toPx(), Offset(x(i), y(w.commercialNet.toDouble())))
        }
    }
}

// ── This week by trader group ───────────────────────────────────────────────

@Composable
private fun GroupsCard(r: MarketsApi.Positioning) {
    if (r.groups.isEmpty()) return
    Card {
        CardHeader("${r.label} · by group")
        Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
            listOf("Group" to 1.6f, "Long" to 1f, "Short" to 1f, "Net" to 1.1f, "Week" to 0.9f).forEachIndexed { i, (h, w) ->
                Text(
                    h.uppercase(),
                    color = Faint,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    textAlign = if (i == 0) TextAlign.Start else TextAlign.End,
                    modifier = Modifier.weight(w)
                )
            }
        }
        Hairline()
        r.groups.forEachIndexed { index, g ->
            GroupRow(g.name, "%,d".format(g.long), "%,d".format(g.short), g.net, g.change, bold = index == 0)
            Hairline()
        }
        GroupRow("Open interest", "", "", r.openInterest, r.openInterestChange, bold = true, plainNet = true)
        Text(
            "Futures · contracts",
            color = Faint,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 14.dp)
        )
    }
}

@Composable
private fun GroupRow(name: String, long: String, short: String, net: Long, change: Long, bold: Boolean, plainNet: Boolean = false) {
    val num = Mono.copy(fontSize = 11.5.sp)
    Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            name,
            color = if (bold) Color.White else Color.White.copy(alpha = 0.72f),
            fontSize = 13.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            lineHeight = 16.sp,
            modifier = Modifier.weight(1.6f)
        )
        Text(long, color = Muted, style = num, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Text(short, color = Muted, style = num, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Text(
            if (plainNet) "%,d".format(net) else signed(net),
            color = if (plainNet) Color.White else tint(net),
            style = num,
            fontWeight = if (plainNet) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.1f)
        )
        Text(signed(change), color = tint(change), style = num, textAlign = TextAlign.End, modifier = Modifier.weight(0.9f))
    }
}

// ── Every market, with its three-year line ──────────────────────────────────

@Composable
private fun MarketsCard(rows: List<MarketsApi.Positioning>, focusKey: String, onChoose: (String) -> Unit) {
    Card {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text("ALL MARKETS", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp, modifier = Modifier.weight(1f))
            Text("OPEN INTEREST", color = Faint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.width(14.dp))
            Text("3 YEARS", color = Faint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, textAlign = TextAlign.End, modifier = Modifier.width(84.dp))
        }
        rows.forEach { r ->
            Hairline()
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(if (r.key == focusKey) Color.White.copy(alpha = 0.04f) else Color.Transparent)
                    .clickable { onChoose(r.key) }
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(r.flag, color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.width(22.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(signed(r.net), color = tint(r.net), style = Mono.copy(fontSize = 11.sp))
                        r.index?.let {
                            Spacer(Modifier.width(8.dp))
                            IndexMeter(it)
                        }
                    }
                }
                Text("%,d".format(r.openInterest), color = Muted, style = Mono.copy(fontSize = 12.sp))
                Spacer(Modifier.width(14.dp))
                Sparkline(r.history.takeLast(SPARK_WEEKS).map { it.net }, Modifier.width(84.dp).height(26.dp))
            }
        }
    }
}

/** The COT index as a small track with a dot: 0 the most short in three years, 100 the most long. */
@Composable
private fun IndexMeter(index: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(34.dp).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f))) {
            Box(
                Modifier
                    .padding(start = (30 * index / 100).dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
        Spacer(Modifier.width(5.dp))
        Text("$index", color = Faint, style = Mono.copy(fontSize = 10.sp))
    }
}

/**
 * Net position over the weeks with a faint zero line — crossing from long to
 * short is the event, so the line is coloured by where it ends.
 */
@Composable
private fun Sparkline(values: List<Long>, modifier: Modifier) {
    if (values.size < 2) return
    val last = values.last()
    Canvas(modifier) {
        val min = minOf(values.min(), 0L).toFloat()
        val max = maxOf(values.max(), 0L).toFloat()
        val span = (max - min).takeIf { it > 0 } ?: 1f
        fun y(v: Float) = size.height - (v - min) / span * size.height
        drawLine(Color.White.copy(alpha = 0.16f), Offset(0f, y(0f)), Offset(size.width, y(0f)), 1f)
        val path = Path()
        values.forEachIndexed { i, v ->
            val px = size.width * i / (values.size - 1)
            if (i == 0) path.moveTo(px, y(v.toFloat())) else path.lineTo(px, y(v.toFloat()))
        }
        drawPath(path, if (last >= 0) Buy else Sell, style = Stroke(1.4.dp.toPx(), join = StrokeJoin.Round))
    }
}
