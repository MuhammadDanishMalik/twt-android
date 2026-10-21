package com.talkswithtanha.twt.features.home.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talkswithtanha.twt.core.data.TechnicalsApi
import com.talkswithtanha.twt.core.designsystem.Haptics
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val SellRed = Color(0xFFEF4444)
private val BuyGreen = Color(0xFF22C55E)
private val NeutralSlate = Color(0xFF94A3B8)
private val Track = Color.White.copy(alpha = 0.12f)

/** What the timeframe menu says, in words; the chip shows the short form. */
private val TIMEFRAME_NAMES = mapOf(
    "1m" to "1 minute", "5m" to "5 minutes", "15m" to "15 minutes", "30m" to "30 minutes",
    "1h" to "1 hour", "4h" to "4 hours", "1d" to "1 day", "1w" to "1 week", "1M" to "1 month"
)

/** A rounded chip that opens a menu, the selected option ticked. */
@Composable
private fun PickerChip(
    label: String,
    description: String,
    expanded: Boolean,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    monospace: Boolean = false
) {
    Box {
        Row(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .clickable(onClick = onOpen)
                .padding(start = 12.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default
            )
            Icon(Icons.Filled.KeyboardArrowDown, description, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
            options.forEach { (key, text) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text,
                            fontWeight = if (key == selected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    trailingIcon = if (key == selected) {
                        { Text("✓", fontWeight = FontWeight.Bold) }
                    } else null,
                    onClick = { onSelect(key) }
                )
            }
        }
    }
}

private fun verdictColor(label: String): Color = when {
    label.contains("sell", ignoreCase = true) -> SellRed
    label.contains("buy", ignoreCase = true) -> BuyGreen
    else -> NeutralSlate
}

/** The four desks, in UTC hours; Sydney wraps midnight. Same as the website. */
private val SESSIONS = listOf(
    Triple("Sydney", 22, 7),
    Triple("Tokyo", 0, 9),
    Triple("London", 8, 17),
    Triple("New York", 13, 22)
)

private fun openSessions(): List<String> {
    val hour = Calendar.getInstance(TimeZone.getTimeZone("UTC")).get(Calendar.HOUR_OF_DAY)
    return SESSIONS.filter { (_, open, close) ->
        if (open < close) hour in open until close else hour >= open || hour < close
    }.map { it.first }
}

private fun headline(open: List<String>): String = when (open.size) {
    0 -> "The market is quiet."
    1 -> "${open[0]} is open."
    else -> "${open.dropLast(1).joinToString(", ")} and ${open.last()} are open."
}

/**
 * The website's live desk, as one page of the home carousel: which sessions
 * are open, then the technicals for an instrument and timeframe the member
 * picks — the same 26 indicators and three gauges as the Markets page, from
 * the same feed. Refreshes every minute while it is on screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveDeskCard(height: Dp, corner: Dp, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    var instrumentKey by rememberSaveable { mutableStateOf("gold") }
    var timeframe by rememberSaveable { mutableStateOf("4h") }
    var data by remember { mutableStateOf<TechnicalsApi.Technicals?>(null) }
    var failed by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var pickingInstrument by remember { mutableStateOf(false) }
    var pickingTimeframe by remember { mutableStateOf(false) }
    var showingRows by remember { mutableStateOf(false) }
    val instrument = TechnicalsApi.INSTRUMENTS.first { it.key == instrumentKey }
    val open = remember { openSessions() }

    LaunchedEffect(instrumentKey, timeframe) {
        loading = true
        while (true) {
            val result = TechnicalsApi.load(instrumentKey, timeframe)
            if (result != null) data = result
            failed = result == null && data == null
            loading = false
            delay(60_000)
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(corner))
            .background(Brush.verticalGradient(listOf(Color(0xFF17201A), Color(0xFF111111))))
            .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(corner))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        // ── Sessions ──────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(BuyGreen))
            Spacer(Modifier.width(6.dp))
            Text("LIVE DESK", color = BuyGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                headline(open),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(10.dp))

        // ── Instrument and timeframe, both as dropdowns ───────────
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PickerChip(
                label = "${instrument.flag}  ${instrument.label}",
                description = "Choose instrument",
                expanded = pickingInstrument,
                onOpen = {
                    Haptics.tap(haptics)
                    pickingInstrument = true
                },
                onDismiss = { pickingInstrument = false },
                options = TechnicalsApi.INSTRUMENTS.map { it.key to "${it.flag}  ${it.label}" },
                selected = instrumentKey,
                onSelect = {
                    Haptics.tap(haptics)
                    instrumentKey = it
                    pickingInstrument = false
                }
            )
            PickerChip(
                label = TechnicalsApi.TIMEFRAMES.first { it.first == timeframe }.second,
                description = "Choose timeframe",
                expanded = pickingTimeframe,
                onOpen = {
                    Haptics.tap(haptics)
                    pickingTimeframe = true
                },
                onDismiss = { pickingTimeframe = false },
                options = TechnicalsApi.TIMEFRAMES.map { (key, label) -> key to (TIMEFRAME_NAMES[key] ?: label) },
                selected = timeframe,
                onSelect = {
                    Haptics.tap(haptics)
                    timeframe = it
                    pickingTimeframe = false
                },
                monospace = true
            )
            Spacer(Modifier.weight(1f))
            data?.let {
                Text(
                    "%,.${if (it.price < 10) 4 else 2}f".format(it.price),
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // ── Gauges ────────────────────────────────────────────────
        val current = data
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            when {
                current != null -> Row(
                    Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    GaugeColumn("Oscillators", current.oscillators, 70.dp, Modifier.weight(1f))
                    GaugeColumn("Summary", current.summary, 96.dp, Modifier.weight(1.3f), emphasised = true)
                    GaugeColumn("Moving Avgs", current.movingAverages, 70.dp, Modifier.weight(1f))
                }
                failed -> Text(
                    "Technicals unavailable right now. Retrying…",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp
                )
                loading -> Text("Reading the market…", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
            }
        }

        // ── Footer: totals, and the way into the 26 ───────────────
        current?.let { t ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        Haptics.sheetOpen(haptics)
                        showingRows = true
                    }
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("26 indicators", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(
                    "${t.summary.sell} sell · ${t.summary.neutral} neutral · ${t.summary.buy} buy",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
                Icon(Icons.Filled.KeyboardArrowDown, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
            }
        }
    }

    if (showingRows) {
        data?.let { t ->
            ModalBottomSheet(onDismissRequest = { showingRows = false }, containerColor = Color(0xFF1C1C1E)) {
                LazyColumn(Modifier.navigationBarsPadding().padding(horizontal = 20.dp)) {
                    item {
                        Text(
                            "${instrument.label} · ${TechnicalsApi.TIMEFRAMES.first { it.first == timeframe }.second}",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${t.summary.label} — ${t.summary.sell} sell, ${t.summary.neutral} neutral, ${t.summary.buy} buy",
                            color = verdictColor(t.summary.label),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                        )
                    }
                    items(t.rows) { row ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(row.name, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Text(
                                row.value?.let { "%.2f".format(it) } ?: "—",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                row.verdict.replaceFirstChar { it.uppercase() },
                                color = verdictColor(row.verdict),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(64.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GaugeColumn(
    title: String,
    gauge: TechnicalsApi.Gauge,
    size: Dp,
    modifier: Modifier,
    emphasised: Boolean = false
) {
    val color = verdictColor(gauge.label)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        GaugeArc(gauge.score, color, size)
        Text(
            gauge.label,
            color = color,
            fontSize = if (emphasised) 16.sp else 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            lineHeight = if (emphasised) 18.sp else 15.sp
        )
        Text(
            "${gauge.sell} · ${gauge.neutral} · ${gauge.buy}",
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

/** Half-circle from strong sell (left) to strong buy (right), as on the site. */
@Composable
private fun GaugeArc(score: Float, color: Color, size: Dp) {
    val animated by animateFloatAsState(score.coerceIn(-1f, 1f), spring(dampingRatio = 0.7f), label = "needle")
    Canvas(Modifier.width(size).height(size / 2 + 6.dp)) {
        val stroke = size.toPx() * 0.09f
        val radius = (this.size.width - stroke) / 2
        val center = Offset(this.size.width / 2, radius + stroke / 2)
        val arcSize = Size(radius * 2, radius * 2)
        val topLeft = Offset(center.x - radius, center.y - radius)
        drawArc(Track, 180f, 180f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        // Coloured from the sell end up to the needle.
        val sweep = (animated + 1f) / 2f * 180f
        drawArc(color, 180f, sweep.coerceAtLeast(1f), false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        val angle = PI * (1 - (animated + 1f) / 2f)
        val needle = Offset(
            center.x + (radius * 0.78f * cos(angle)).toFloat(),
            center.y - (radius * 0.78f * sin(angle)).toFloat()
        )
        drawLine(Color.White, center, needle, strokeWidth = stroke * 0.32f, cap = StrokeCap.Round)
        drawCircle(Color.White, stroke * 0.55f, center)
        drawCircle(Color(0xFF111111), stroke * 0.3f, center)
    }
}
