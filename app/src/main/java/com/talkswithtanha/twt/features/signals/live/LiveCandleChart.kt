package com.talkswithtanha.twt.features.signals.live

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.talkswithtanha.twt.core.data.LiveApi
import com.talkswithtanha.twt.core.data.LiveDrawing
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.designsystem.WebFonts
import com.talkswithtanha.twt.core.designsystem.components.LoadingLabel
import com.talkswithtanha.twt.core.designsystem.components.Shimmer
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** A line on the chart that is not one of Tanha's drawings: a trade's entry, stop or target. */
data class ChartLine(val price: Double, val label: String, val color: Color)

/** The colours Tanha picks from in the panel (`DRAW_COLORS` on the website). */
fun drawColor(name: String): Color = when (name) {
    "red" -> Color(0xFFDC2626)
    "gold" -> Color(0xFFD4A017)
    "blue" -> Color(0xFF2563EB)
    else -> Color(0xFF16A34A)
}

/**
 * A live candle chart with Tanha's levels on it.
 *
 * Candles come from the website and the last one moves with the price every
 * two seconds while the screen is in front. Drawn on one canvas: the levels
 * are placed from prices and times on every frame, so they sit on the same
 * candle at any zoom and on any timeframe.
 *
 * One finger pans sideways (and lets a vertical swipe scroll the page); two
 * fingers zoom.
 */
@Composable
fun LiveCandleChart(
    instrument: LiveApi.Instrument,
    timeframe: LiveApi.Timeframe,
    modifier: Modifier = Modifier,
    height: Dp = 340.dp,
    drawings: List<LiveDrawing> = emptyList(),
    lines: List<ChartLine> = emptyList(),
    onQuote: (LiveApi.Quote) -> Unit = {}
) {
    // Where each video note was drawn on the last frame, for taps to find.
    val videoTargets = remember { mutableListOf<Pair<Offset, LiveDrawing>>() }
    var playing by remember { mutableStateOf<LiveDrawing?>(null) }
    val tapRadius = with(androidx.compose.ui.platform.LocalDensity.current) { 30.dp.toPx() }
    var bars by remember(instrument.key, timeframe) { mutableStateOf<List<LiveApi.Bar>>(emptyList()) }
    var decimals by remember(instrument.key) { mutableStateOf(instrument.decimals) }
    var failed by remember(instrument.key, timeframe) { mutableStateOf(false) }
    var closed by remember(instrument.key) { mutableStateOf(false) }
    var retry by remember { mutableStateOf(0) }

    // How many bars fit across, and how far back from the newest bar the
    // right edge sits (negative leaves room on the right, as TradingView does).
    var visible by remember(instrument.key, timeframe) { mutableFloatStateOf(60f) }
    var offset by remember(instrument.key, timeframe) { mutableFloatStateOf(-5f) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val quoteCallback by rememberUpdatedState(onQuote)

    LaunchedEffect(instrument.key, timeframe, retry) {
        failed = false
        val first = LiveApi.candles(instrument.key, timeframe)
        if (first == null) {
            failed = true
            return@LaunchedEffect
        }
        bars = first.bars
        decimals = first.decimals
        var ticks = 0
        while (true) {
            delay(2_000)
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) continue
            ticks++
            // The whole history again once a minute: a candle closes, and the
            // feed's own view of the last few may have been corrected.
            if (ticks % 30 == 0) LiveApi.candles(instrument.key, timeframe)?.let { bars = it.bars }
            val q = LiveApi.quote(instrument.key) ?: continue
            quoteCallback(q)
            closed = q.closed
            if (q.closed || bars.isEmpty()) continue
            bars = withPrice(bars, q, timeframe.seconds, decimals)
        }
    }

    val measurer = rememberTextMeasurer()
    val slop = LocalViewConfiguration.current.touchSlop

    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0B0B0B))
    ) {
        when {
            failed -> Text(
                "The chart could not load. Tap to try again.",
                color = IosColors.TextSecondary,
                fontSize = 13.sp,
                fontFamily = WebFonts.Body,
                modifier = Modifier
                    .align(Alignment.Center)
                    .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(); retry++ } }
            )

            bars.isEmpty() -> {
                Shimmer(Modifier.fillMaxSize(), height = height, cornerRadius = 16.dp)
                LoadingLabel("Loading chart", Modifier.align(Alignment.Center))
            }

            else -> Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(bars.size) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var mode = 0 // 0 undecided, 1 pan, 2 zoom, -1 leave it to the page
                            var travelled = Offset.Zero
                            var lastSpan = 0f
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.size >= 2) {
                                    val span = (pressed[0].position - pressed[1].position).getDistance()
                                    if (mode != 2) {
                                        mode = 2
                                        lastSpan = span
                                    } else if (lastSpan > 0f && span > 0f) {
                                        visible = (visible * lastSpan / span).coerceIn(15f, 400f)
                                        lastSpan = span
                                    }
                                    event.changes.forEach { it.consume() }
                                } else if (pressed.size == 1 && mode != 2 && mode != -1) {
                                    val change = pressed[0]
                                    travelled += change.positionChange()
                                    if (mode == 0 && travelled.getDistance() > slop) {
                                        mode = if (abs(travelled.x) > abs(travelled.y)) 1 else -1
                                    }
                                    if (mode == 1) {
                                        val barW = (size.width - AXIS_W.toPx()) / visible
                                        offset = (offset + change.positionChange().x / barW).coerceIn(-visible * 0.6f, max(0f, bars.size - 10f))
                                        change.consume()
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                            // A tap, not a pan: did it land on a video note?
                            if (mode == 0) {
                                videoTargets
                                    .minByOrNull { (it.first - down.position).getDistance() }
                                    ?.takeIf { (it.first - down.position).getDistance() < tapRadius }
                                    ?.let { playing = it.second }
                            }
                        }
                    }
            ) {
                videoTargets.clear()
                drawChart(bars, decimals, visible, offset, timeframe, drawings, lines, measurer, videoTargets)
            }
        }

        playing?.let { note -> VideoNotePlayer(note) { playing = null } }

        if (closed && bars.isNotEmpty()) {
            Text(
                "Market closed",
                color = Color(0xFFF59E0B),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = WebFonts.Body,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x29F59E0B))
                    .padding(horizontal = 9.dp, vertical = 3.dp)
            )
        }
    }
}

private val AXIS_W = 58.dp
private val TIME_H = 20.dp

/** The live price folded into the newest candle, or a new candle when its time has come. */
private fun withPrice(bars: List<LiveApi.Bar>, q: LiveApi.Quote, sec: Long, decimals: Int): List<LiveApi.Bar> {
    val last = bars.last()
    val now = q.ts / 1000
    val start = last.t + max(0L, (now - last.t) / sec) * sec
    val p = round(q.price, decimals)
    return if (start == last.t) {
        bars.dropLast(1) + last.copy(h = max(last.h, p), l = min(last.l, p), c = p)
    } else {
        bars + LiveApi.Bar(start, last.c, max(last.c, p), min(last.c, p), p)
    }
}

private fun round(v: Double, decimals: Int): Double {
    val f = 10.0.pow(decimals)
    return Math.round(v * f) / f
}

private fun DrawScope.drawChart(
    bars: List<LiveApi.Bar>,
    decimals: Int,
    visible: Float,
    offset: Float,
    tf: LiveApi.Timeframe,
    drawings: List<LiveDrawing>,
    lines: List<ChartLine>,
    measurer: TextMeasurer,
    videoTargets: MutableList<Pair<Offset, LiveDrawing>>
) {
    val up = IosColors.BuyGreen
    val down = IosColors.SellRed
    val grid = Color.White.copy(alpha = 0.05f)
    val axisText = Color.White.copy(alpha = 0.45f)

    val plotW = size.width - AXIS_W.toPx()
    val plotH = size.height - TIME_H.toPx()
    val padTop = 14.dp.toPx()
    val padBottom = 10.dp.toPx()
    val n = bars.size
    val end = (n - 1) - offset // fractional index at the right edge
    val start = end - visible
    val barW = plotW / visible

    fun xOfIndex(i: Float) = (i - start) * barW - barW / 2f + barW

    // Price range: the bars on screen, and any trade lines, so a stop below
    // the candles is still in view.
    val from = max(0, floor(start).toInt())
    val to = min(n - 1, ceil(end).toInt())
    if (from > to) return
    var lo = Double.MAX_VALUE
    var hi = -Double.MAX_VALUE
    for (i in from..to) {
        lo = min(lo, bars[i].l)
        hi = max(hi, bars[i].h)
    }
    lines.forEach { lo = min(lo, it.price); hi = max(hi, it.price) }
    if (hi <= lo) {
        hi += 1.0
        lo -= 1.0
    }
    val pad = (hi - lo) * 0.06
    hi += pad
    lo -= pad
    fun y(p: Double) = (padTop + (hi - p) / (hi - lo) * (plotH - padTop - padBottom)).toFloat()

    // Time → x, between bars and past the last one.
    fun xOfTime(t: Long): Float {
        if (t <= bars[0].t) return xOfIndex(((t - bars[0].t).toFloat() / tf.seconds))
        var a = 0
        var b = n - 1
        while (a < b) {
            val mid = (a + b + 1) / 2
            if (bars[mid].t <= t) a = mid else b = mid - 1
        }
        val step = if (a < n - 1) (bars[a + 1].t - bars[a].t).toFloat() else tf.seconds.toFloat()
        return xOfIndex(a + (t - bars[a].t) / step)
    }

    val small = TextStyle(color = axisText, fontSize = 10.sp, fontFamily = WebFonts.Mono)

    // Grid and price labels.
    val step = niceStep((hi - lo) / 5)
    var gp = ceil(lo / step) * step
    while (gp < hi) {
        val gy = y(gp)
        drawLine(grid, Offset(0f, gy), Offset(plotW, gy), 1f)
        val label = measurer.measure(String.format(Locale.US, "%.${decimals}f", gp), small)
        drawText(label, topLeft = Offset(plotW + 6.dp.toPx(), gy - label.size.height / 2f))
        gp += step
    }

    // Time labels.
    val fmt = SimpleDateFormat(if (tf.seconds >= 14_400) "d MMM" else "HH:mm", Locale.US).apply { timeZone = TimeZone.getDefault() }
    val every = max(1, (visible / 4).toInt())
    var ti = (ceil(start / every) * every).toInt()
    while (ti <= to) {
        if (ti >= 0) {
            val x = xOfIndex(ti.toFloat())
            val label = measurer.measure(fmt.format(Date(bars[ti].t * 1000)), small)
            if (x - label.size.width / 2 > 0 && x + label.size.width / 2 < plotW) {
                drawText(label, topLeft = Offset(x - label.size.width / 2f, plotH + 4.dp.toPx()))
            }
        }
        ti += every
    }

    // The brand, faint, behind everything — a screenshot says where it came from.
    val mark = measurer.measure(
        "Trade With Tanha",
        TextStyle(color = Color.White.copy(alpha = 0.05f), fontSize = (plotW / density / 9f).coerceIn(18f, 40f).sp, fontWeight = FontWeight.Bold, fontFamily = WebFonts.Display)
    )
    val site = measurer.measure("tradewithtanha.io", TextStyle(color = Color.White.copy(alpha = 0.05f), fontSize = 12.sp, fontFamily = WebFonts.Body))
    val markTop = plotH / 2f - (mark.size.height + site.size.height) / 2f
    drawText(mark, topLeft = Offset((plotW - mark.size.width) / 2f, markTop))
    drawText(site, topLeft = Offset((plotW - site.size.width) / 2f, markTop + mark.size.height))

    // Zones behind the candles.
    drawings.filter { it.kind == LiveDrawing.Kind.ZONE }.forEach { d ->
        val c = drawColor(d.color)
        val y1 = y(d.p1)
        val y2 = y(d.p2 ?: d.p1)
        drawRect(c.copy(alpha = 0.14f), Offset(0f, min(y1, y2)), Size(plotW, abs(y2 - y1).coerceAtLeast(1f)))
        drawLine(c.copy(alpha = 0.6f), Offset(0f, y1), Offset(plotW, y1), 1.dp.toPx())
        drawLine(c.copy(alpha = 0.6f), Offset(0f, y2), Offset(plotW, y2), 1.dp.toPx())
    }

    // Candles.
    val body = (barW * 0.7f).coerceAtLeast(1f)
    for (i in from..to) {
        val b = bars[i]
        val x = xOfIndex(i.toFloat())
        if (x < -barW || x > plotW + barW) continue
        val c = if (b.c >= b.o) up else down
        drawLine(c, Offset(x, y(b.h)), Offset(x, y(b.l)), max(1f, barW * 0.12f))
        val top = y(max(b.o, b.c))
        val h = (y(min(b.o, b.c)) - top).coerceAtLeast(1f)
        drawRect(c, Offset(x - body / 2f, top), Size(body, h))
    }

    // Levels, trend lines and notes.
    drawings.forEach { d ->
        val c = drawColor(d.color)
        when (d.kind) {
            LiveDrawing.Kind.LEVEL -> {
                val ly = y(d.p1)
                drawLine(c, Offset(0f, ly), Offset(plotW, ly), 1.5.dp.toPx())
                if (d.label.isNotBlank()) pill(measurer, d.label, Offset(6.dp.toPx(), ly), c, filled = false)
                axisTag(measurer, String.format(Locale.US, "%.${decimals}f", d.p1), ly, plotW, c)
            }
            LiveDrawing.Kind.ZONE -> {
                if (d.label.isNotBlank()) pill(measurer, d.label, Offset(6.dp.toPx(), min(y(d.p1), y(d.p2 ?: d.p1)) + 11.dp.toPx()), c, filled = false)
                axisTag(measurer, String.format(Locale.US, "%.${decimals}f", d.p1), y(d.p1), plotW, c)
                axisTag(measurer, String.format(Locale.US, "%.${decimals}f", d.p2 ?: d.p1), y(d.p2 ?: d.p1), plotW, c)
            }
            LiveDrawing.Kind.TREND -> {
                val a = Offset(xOfTime(d.t1 ?: 0), y(d.p1))
                val b = Offset(xOfTime(d.t2 ?: 0), y(d.p2 ?: d.p1))
                drawLine(c, a, b, 2.dp.toPx(), StrokeCap.Round)
                if (d.label.isNotBlank()) pill(measurer, d.label, Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f - 12.dp.toPx()), c, filled = false)
            }
            LiveDrawing.Kind.VIDEO -> {
                // A squircle to the left of the candle on a short stem, so the
                // candles after it stay in view; tapped, it plays.
                val p = Offset(xOfTime(d.t1 ?: 0), y(d.p1))
                val half = 19.dp.toPx()
                val gap = 12.dp.toPx()
                val left = p.x - gap - half * 2 >= 0f
                val center = Offset(
                    if (left) p.x - gap - half else p.x + gap + half,
                    p.y.coerceIn(half + 2f, plotH - half - 2f)
                )
                drawLine(c, p, Offset(if (left) center.x + half else center.x - half, center.y), 2.dp.toPx())
                drawCircle(c.copy(alpha = 0.35f), 7.dp.toPx(), p)
                drawCircle(c, 4.dp.toPx(), p)
                drawPath(squircle(center, half), c)
                drawPath(squircle(center, half - 2.5.dp.toPx()), Color(0xFF111111))
                val tri = androidx.compose.ui.graphics.Path().apply {
                    moveTo(center.x - 4.dp.toPx(), center.y - 6.dp.toPx())
                    lineTo(center.x + 6.dp.toPx(), center.y)
                    lineTo(center.x - 4.dp.toPx(), center.y + 6.dp.toPx())
                    close()
                }
                drawPath(tri, Color.White)
                if (p.x in 0f..plotW) videoTargets += center to d
            }
            LiveDrawing.Kind.NOTE -> {
                val p = Offset(xOfTime(d.t1 ?: 0), y(d.p1))
                drawCircle(c, 4.dp.toPx(), p)
                if (d.label.isNotBlank()) pill(measurer, d.label, Offset(p.x + 9.dp.toPx(), p.y), c, filled = false)
            }
        }
    }

    // A trade's levels: dashed, labelled on the price scale.
    val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
    lines.forEach { l ->
        val ly = y(l.price)
        drawLine(l.color, Offset(0f, ly), Offset(plotW, ly), 1.dp.toPx(), pathEffect = dash)
        pill(measurer, l.label, Offset(plotW - 4.dp.toPx(), ly), l.color, filled = true, alignEnd = true)
        axisTag(measurer, String.format(Locale.US, "%.${decimals}f", l.price), ly, plotW, l.color)
    }

    // The price now.
    val last = bars.last()
    val lc = if (last.c >= last.o) up else down
    val ly = y(last.c)
    drawLine(lc.copy(alpha = 0.7f), Offset(0f, ly), Offset(plotW, ly), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())))
    axisTag(measurer, String.format(Locale.US, "%.${decimals}f", last.c), ly, plotW, lc)

    drawLine(Color.White.copy(alpha = 0.08f), Offset(plotW, 0f), Offset(plotW, size.height), 1f)
}

/** A superellipse around [c] — the iOS app-icon shape the website's video notes use. */
private fun squircle(c: Offset, half: Float) = androidx.compose.ui.graphics.Path().apply {
    val k = half * 0.76f // how far the corner handles reach; 0.76 gives the soft iOS corner
    moveTo(c.x, c.y - half)
    cubicTo(c.x + k, c.y - half, c.x + half, c.y - k, c.x + half, c.y)
    cubicTo(c.x + half, c.y + k, c.x + k, c.y + half, c.x, c.y + half)
    cubicTo(c.x - k, c.y + half, c.x - half, c.y + k, c.x - half, c.y)
    cubicTo(c.x - half, c.y - k, c.x - k, c.y - half, c.x, c.y - half)
    close()
}

private fun niceStep(raw: Double): Double {
    if (raw <= 0) return 1.0
    val mag = 10.0.pow(floor(log10(raw)))
    val f = raw / mag
    return mag * when {
        f < 1.5 -> 1.0
        f < 3.5 -> 2.0
        f < 7.5 -> 5.0
        else -> 10.0
    }
}

/** A filled price tag on the price scale. */
private fun DrawScope.axisTag(measurer: TextMeasurer, text: String, y: Float, plotW: Float, color: Color) {
    val style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, fontFamily = WebFonts.Mono)
    val layout = measurer.measure(text, style)
    val padX = 4.dp.toPx()
    val h = layout.size.height + 4.dp.toPx()
    drawRoundRect(color, Offset(plotW + 1f, y - h / 2f), Size(AXIS_W.toPx() - 2f, h), CornerRadius(3.dp.toPx()))
    drawText(layout, topLeft = Offset(plotW + padX, y - layout.size.height / 2f))
}

/** A label on the chart: outlined, or filled. */
private fun DrawScope.pill(measurer: TextMeasurer, text: String, at: Offset, color: Color, filled: Boolean, alignEnd: Boolean = false) {
    val style = TextStyle(color = if (filled) Color.White else color, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, fontFamily = WebFonts.Body)
    val layout = measurer.measure(text.take(40), style)
    val padX = 6.dp.toPx()
    val w = layout.size.width + padX * 2
    val h = layout.size.height + 4.dp.toPx()
    val left = if (alignEnd) at.x - w else at.x
    val topLeft = Offset(left, at.y - h / 2f)
    drawRoundRect(if (filled) color else Color(0xFF111111), topLeft, Size(w, h), CornerRadius(5.dp.toPx()))
    if (!filled) drawRoundRect(color, topLeft, Size(w, h), CornerRadius(5.dp.toPx()), style = Stroke(1.dp.toPx()))
    drawText(layout, topLeft = Offset(left + padX, at.y - layout.size.height / 2f))
}
