package com.talkswithtanha.twt.features.home.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talkswithtanha.twt.core.data.MarketsApi
import com.talkswithtanha.twt.core.designsystem.Haptics
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Up = Color(0xFF22C55E)
private val Down = Color(0xFFEF4444)
private val Muted = Color.White.copy(alpha = 0.55f)
private val Tabular = TextStyle(fontFeatureSettings = "tnum")

/**
 * The market chart for one instrument, opened from a heatmap tile or the gold
 * card: real prices over 1D to 5Y, as a line or as candles, and a finger
 * dragged across it reads the exact price and time under it.
 *
 * Drawn natively from the same Yahoo series the website charts, so it opens
 * fast, uses the app's type, and shares the app's colours.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketChartSheet(quote: MarketsApi.Quote, onDismiss: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var range by rememberSaveable { mutableStateOf("1D") }
    var candlesMode by rememberSaveable { mutableStateOf(false) }
    var candles by remember { mutableStateOf<List<MarketsApi.Candle>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var scrubIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(range) {
        candles = null
        failed = false
        scrubIndex = null
        val loaded = MarketsApi.history(quote.key, range)
        candles = loaded
        failed = loaded == null
    }

    val data = candles
    // The header follows the finger while scrubbing, otherwise shows the latest.
    val shown = scrubIndex?.let { data?.getOrNull(it) }
    // 1D is measured from the previous close, like the card it opened from;
    // longer ranges from where the period began.
    val base = if (range == "1D") quote.previousClose else data?.firstOrNull()?.close
    val shownPrice = shown?.close ?: quote.price
    val periodChange = if (base != null && base != 0.0) ((shownPrice - base) / base) * 100 else quote.change
    val tint = if (periodChange >= 0) Up else Down

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        // The chart takes horizontal drags for scrubbing; the sheet keeps its
        // own drag off so a slightly diagonal scrub does not pull it closed.
        sheetGesturesEnabled = false,
        containerColor = Color(0xFF141414),
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            // ── Header ───────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 18.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f)) {
                    Text(quote.label, color = Muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "%,.${quote.decimals}f".format(shownPrice),
                        color = Color.White,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.SemiBold,
                        style = Tabular
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "%+.2f%%".format(periodChange),
                            color = tint,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            style = Tabular,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(tint.copy(alpha = 0.14f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            shown?.let { timeLabel(it.time, range) } ?: periodLabel(range),
                            color = Muted,
                            fontSize = 13.sp
                        )
                    }
                }
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close chart", tint = Color.White)
                }
            }

            // ── Chart ────────────────────────────────────────────────
            Box(
                Modifier.fillMaxWidth().height(300.dp).padding(top = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    data != null -> PriceChart(
                        candles = data,
                        candlesMode = candlesMode,
                        lineColor = tint,
                        decimals = quote.decimals,
                        scrubIndex = scrubIndex,
                        onScrub = { index ->
                            if (index != scrubIndex && index != null) Haptics.tick(haptics)
                            scrubIndex = index
                        }
                    )
                    failed -> Text("Chart unavailable right now.", color = Muted, fontSize = 14.sp)
                    else -> CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
                }
            }

            // ── Ranges and style ─────────────────────────────────────
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MarketsApi.RANGES.forEach { (label, _, _) ->
                    val selected = label == range
                    Text(
                        label,
                        color = if (selected) Color.Black else Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) Color.White else Color.Transparent)
                            .clickable {
                                Haptics.tap(haptics)
                                range = label
                            }
                            .padding(horizontal = 11.dp, vertical = 6.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    if (candlesMode) "Line" else "Candles",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable {
                            Haptics.tap(haptics)
                            candlesMode = !candlesMode
                        }
                        .padding(horizontal = 11.dp, vertical = 6.dp)
                )
            }

            // ── Today's facts ────────────────────────────────────────
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                Fact("Day low", "%,.${quote.decimals}f".format(quote.low), Modifier.weight(1f))
                Fact("Day high", "%,.${quote.decimals}f".format(quote.high), Modifier.weight(1f))
                Fact("Prev. close", "%,.${quote.decimals}f".format(quote.previousClose), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Fact(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = Muted, fontSize = 12.sp)
        Text(value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, style = Tabular)
    }
}

/**
 * The chart itself. The line draws itself in from the left when the data
 * arrives; candles grow from their mid-points. A drag anywhere shows a
 * crosshair on the nearest point.
 */
@Composable
private fun PriceChart(
    candles: List<MarketsApi.Candle>,
    candlesMode: Boolean,
    lineColor: Color,
    decimals: Int,
    scrubIndex: Int?,
    onScrub: (Int?) -> Unit
) {
    val reveal = remember(candles) { Animatable(0f) }
    LaunchedEffect(candles) { reveal.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }

    val low = candles.minOf { it.low }
    val high = candles.maxOf { it.high }
    val pad = (high - low).takeIf { it > 0 }?.let { it * 0.08 } ?: (high * 0.001)
    val min = low - pad
    val max = high + pad

    fun indexAt(x: Float, width: Float) =
        ((x / width) * (candles.size - 1)).toInt().coerceIn(0, candles.lastIndex)

    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 64.dp)
                .pointerInput(candles) {
                    detectDragGestures(
                        onDragStart = { onScrub(indexAt(it.x, size.width.toFloat())) },
                        onDragEnd = { onScrub(null) },
                        onDragCancel = { onScrub(null) },
                        onDrag = { change, _ -> onScrub(indexAt(change.position.x, size.width.toFloat())) }
                    )
                }
                .pointerInput(candles) {
                    detectTapGestures(onPress = {
                        onScrub(indexAt(it.x, size.width.toFloat()))
                        tryAwaitRelease()
                        onScrub(null)
                    })
                }
        ) {
            val w = size.width
            val h = size.height
            fun y(v: Double) = (h - ((v - min) / (max - min)) * h).toFloat()
            fun x(i: Int) = if (candles.size == 1) w / 2 else w * i / (candles.size - 1)

            // Faint horizontal guides at the quarters.
            for (q in 1..3) {
                val gy = h * q / 4f
                drawLine(Color.White.copy(alpha = 0.06f), Offset(0f, gy), Offset(w, gy), 1f)
            }

            clipRect(right = w * reveal.value) {
                if (candlesMode) {
                    val body = (w / candles.size * 0.6f).coerceIn(1.5f, 14f)
                    candles.forEachIndexed { i, c ->
                        val color = if (c.close >= c.open) Up else Down
                        val cx = x(i)
                        drawLine(color, Offset(cx, y(c.high)), Offset(cx, y(c.low)), 1.4f)
                        val top = y(maxOf(c.open, c.close))
                        val bottom = y(minOf(c.open, c.close))
                        drawRect(color, Offset(cx - body / 2, top), Size(body, (bottom - top).coerceAtLeast(1.2f)))
                    }
                } else {
                    val line = Path()
                    candles.forEachIndexed { i, c ->
                        if (i == 0) line.moveTo(x(i), y(c.close)) else line.lineTo(x(i), y(c.close))
                    }
                    val fill = Path().apply {
                        addPath(line)
                        lineTo(x(candles.lastIndex), h)
                        lineTo(x(0), h)
                        close()
                    }
                    drawPath(fill, Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.28f), Color.Transparent)))
                    drawPath(line, lineColor, style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }

            // Where the period started, dashed, so up or down reads at a glance.
            val startY = y(candles.first().close)
            drawLine(
                Color.White.copy(alpha = 0.25f),
                Offset(0f, startY),
                Offset(w, startY),
                1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
            )

            scrubIndex?.let { i ->
                val c = candles[i]
                val cx = x(i)
                val cy = y(c.close)
                drawLine(Color.White.copy(alpha = 0.5f), Offset(cx, 0f), Offset(cx, h), 1.2f)
                drawLine(Color.White.copy(alpha = 0.25f), Offset(0f, cy), Offset(w, cy), 1f)
                drawCircle(lineColor.copy(alpha = 0.25f), 12f, Offset(cx, cy))
                drawCircle(Color.White, 6f, Offset(cx, cy))
            }
        }

        // High and low on the right edge.
        Column(
            Modifier.fillMaxSize().padding(end = 12.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text("%,.${decimals}f".format(high), color = Muted, fontSize = 11.sp, style = Tabular)
            Text("%,.${decimals}f".format(low), color = Muted, fontSize = 11.sp, style = Tabular)
        }
    }
}

private fun periodLabel(range: String) = when (range) {
    "1D" -> "Since the previous close"
    "1W" -> "Past week"
    "1M" -> "Past month"
    "3M" -> "Past 3 months"
    "6M" -> "Past 6 months"
    "1Y" -> "Past year"
    else -> "Past 5 years"
}

private fun timeLabel(time: Long, range: String): String {
    val pattern = when (range) {
        "1D" -> "HH:mm"
        "1W" -> "EEE HH:mm"
        "5Y" -> "MMM yyyy"
        else -> "d MMM yyyy"
    }
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(time))
}
