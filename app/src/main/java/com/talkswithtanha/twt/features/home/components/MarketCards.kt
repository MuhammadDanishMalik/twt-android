package com.talkswithtanha.twt.features.home.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talkswithtanha.twt.core.data.MarketsApi
import com.talkswithtanha.twt.core.designsystem.Haptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private val Up = Color(0xFF22C55E)
private val Down = Color(0xFFEF4444)
private val CardBg = Color(0xFF141414)
private val Muted = Color.White.copy(alpha = 0.55f)

private fun signed(change: Double) = "%+.2f%%".format(change)
private fun changeColor(change: Double) = if (change >= 0) Up else Down

/** The coin or flag beside an instrument, as the website draws it. */
@Composable
internal fun InstrumentBadge(quote: MarketsApi.Quote, size: Int = 22) {
    val (text, bg) = when (quote.key) {
        "gold" -> "Au" to Color(0xFFB8860B)
        "silver" -> "Ag" to Color(0xFF8E8E93)
        "btc" -> "₿" to Color(0xFFF7931A)
        "eth" -> "Ξ" to Color(0xFF7C83A6)
        else -> null to null
    }
    if (text != null && bg != null) {
        Box(
            Modifier.size(size.dp).clip(CircleShape).background(bg),
            contentAlignment = Alignment.Center
        ) {
            Text(text, color = Color.White, fontSize = (size * 0.45f).sp, fontWeight = FontWeight.Bold)
        }
    } else {
        Text(quote.flag, fontSize = (size * 0.8f).sp)
    }
}

/**
 * True once the element has properly come on screen — most of the way up from
 * the bottom edge. A LazyColumn composes items a little before they are
 * visible, so "composed" is too early: the motion would play out of sight.
 */
@Composable
private fun rememberSeen(): Pair<Boolean, Modifier> {
    var seen by remember { mutableStateOf(false) }
    val windowHeight = LocalView.current.rootView.height
    val modifier = Modifier.onGloballyPositioned { coords ->
        if (seen) return@onGloballyPositioned
        val bounds = coords.boundsInWindow()
        if (bounds.height > 0 && bounds.top < windowHeight * 0.85f && bounds.bottom > 0) seen = true
    }
    return seen to modifier
}

/** Runs 0 → 1 once [start] turns true. */
@Composable
private fun rememberEntrance(start: Boolean, delayMs: Long = 0): Animatable<Float, *> {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(start) {
        if (!start) return@LaunchedEffect
        delay(delayMs)
        progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }
    return progress
}

/**
 * Gold at a glance, as on the website: the price, today's change, where it
 * sits between the day's low and high, and how managed money is positioned.
 */
@Composable
fun GoldPriceCard(
    quote: MarketsApi.Quote,
    longPercent: Int?,
    onOpenNews: () -> Unit,
    onOpenChart: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val (seen, seenModifier) = rememberSeen()
    val entrance = rememberEntrance(seen)
    val p = entrance.value
    val pill = remember { Animatable(0f) }
    LaunchedEffect(seen) {
        if (!seen) return@LaunchedEffect
        delay(550)
        pill.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    }

    // The price counts up from the day's low, so the number lands rather than appears.
    val shownPrice = quote.low + (quote.price - quote.low) * p
    val range = (quote.high - quote.low).takeIf { it > 0 } ?: 1.0
    val position = ((quote.price - quote.low) / range).toFloat().coerceIn(0f, 1f)

    Column(
        modifier
            .then(seenModifier)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(CardBg)
            .border(0.5.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(24.dp))
            .clickable {
                Haptics.sheetOpen(haptics)
                onOpenChart()
            }
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InstrumentBadge(quote, 26)
            Spacer(Modifier.width(10.dp))
            Text("GOLD · XAU/USD", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "%,.${quote.decimals}f".format(shownPrice),
                color = Color.White,
                fontSize = 40.sp,
                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(
                signed(quote.change),
                color = changeColor(quote.change),
                fontSize = 15.sp,
                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = pill.value
                        scaleY = pill.value
                        alpha = pill.value.coerceIn(0f, 1f)
                    }
                    .clip(RoundedCornerShape(18.dp))
                    .background(changeColor(quote.change).copy(alpha = 0.14f))
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            )
        }

        // ── Today's range ─────────────────────────────────────────
        Spacer(Modifier.height(14.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().height(14.dp), contentAlignment = Alignment.CenterStart) {
            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)))
            Box(
                Modifier
                    .offset(x = (maxWidth - 6.dp) * (position * p))
                    .width(6.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White)
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            Text("L %.${quote.decimals}f".format(quote.low), color = Muted, fontSize = 12.sp, style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"))
            Spacer(Modifier.weight(1f))
            Text("H %.${quote.decimals}f".format(quote.high), color = Muted, fontSize = 12.sp, style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"))
        }

        // ── Managed money ─────────────────────────────────────────
        longPercent?.let { long ->
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth()) {
                Text("Managed money", color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                val isLong = long >= 50
                Text(
                    if (isLong) "${(long * p).toInt()}% long" else "${((100 - long) * p).toInt()}% short",
                    color = if (isLong) Up else Down,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f))) {
                // Long fills from the left, short from the right, both as the card arrives.
                Box(Modifier.weight((long / 100f * p).coerceAtLeast(0.001f)).height(6.dp).background(Up))
                Box(Modifier.weight((1f - p).coerceAtLeast(0.001f)).height(6.dp))
                Box(Modifier.weight(((100 - long) / 100f * p).coerceAtLeast(0.001f)).height(6.dp).background(Down))
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Gold news →",
                color = Up,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable {
                    Haptics.tap(haptics)
                    onOpenNews()
                }
            )
            Spacer(Modifier.weight(1f))
            Text("Since the previous close", color = Muted, fontSize = 13.sp)
        }
    }
}

/**
 * Today's move for all twelve instruments. Each tile is tinted by how far it
 * moved — a quiet day is a quiet grid — and they pop in one after another.
 */
@Composable
fun HeatmapCard(quotes: List<MarketsApi.Quote>, onOpen: (MarketsApi.Quote) -> Unit, modifier: Modifier = Modifier) {
    val (seen, seenModifier) = rememberSeen()
    Column(
        modifier
            .then(seenModifier)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(CardBg)
            .border(0.5.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(24.dp))
    ) {
        Text(
            "HEATMAP · TODAY",
            color = Muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp,
            modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 14.dp)
        )
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.08f)))
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            quotes.chunked(3).forEachIndexed { row, chunk ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    chunk.forEachIndexed { col, quote ->
                        HeatTile(quote, index = row * 3 + col, start = seen, onOpen = { onOpen(quote) }, modifier = Modifier.weight(1f))
                    }
                    repeat(3 - chunk.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun HeatTile(quote: MarketsApi.Quote, index: Int, start: Boolean, onOpen: () -> Unit, modifier: Modifier) {
    val haptics = LocalHapticFeedback.current
    val pop = remember { Animatable(0f) }
    val count = remember { Animatable(0f) }
    LaunchedEffect(start) {
        if (!start) return@LaunchedEffect
        delay(index * 60L)
        launch { pop.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 380f)) }
        count.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }
    // Strength of the tint: a 3% move is as strong as it gets.
    val strength = (abs(quote.change) / 3.0).coerceIn(0.0, 1.0).toFloat()
    val tint = changeColor(quote.change)

    Column(
        modifier
            .graphicsLayer {
                scaleX = 0.85f + 0.15f * pop.value
                scaleY = 0.85f + 0.15f * pop.value
                alpha = pop.value.coerceIn(0f, 1f)
            }
            .clip(RoundedCornerShape(16.dp))
            .background(tint.copy(alpha = 0.06f + 0.22f * strength))
            .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .clickable {
                Haptics.sheetOpen(haptics)
                onOpen()
            }
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InstrumentBadge(quote, 18)
            Spacer(Modifier.width(6.dp))
            Text(quote.label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            signed(quote.change * count.value),
            color = tint,
            fontSize = 16.sp,
            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * The two market cards under the signals, loading themselves and refreshing
 * every minute. Draws nothing until the first quotes arrive, so a dead feed
 * leaves no empty box on the home screen.
 */
@Composable
fun HomeMarketsSection(onOpenNews: () -> Unit, modifier: Modifier = Modifier) {
    var quotes by remember { mutableStateOf<List<MarketsApi.Quote>?>(null) }
    var longPercent by remember { mutableStateOf<Int?>(null) }
    var charting by remember { mutableStateOf<MarketsApi.Quote?>(null) }
    LaunchedEffect(Unit) {
        launch { longPercent = MarketsApi.goldLongPercent() }
        while (true) {
            MarketsApi.quotes()?.let { quotes = it }
            delay(60_000)
        }
    }
    val list = quotes ?: return
    Column(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        list.firstOrNull { it.key == "gold" }?.let { gold ->
            GoldPriceCard(gold, longPercent, onOpenNews, onOpenChart = { charting = gold })
        }
        HeatmapCard(list, onOpen = { charting = it })
    }
    charting?.let { MarketChartSheet(it, onDismiss = { charting = null }) }
}
