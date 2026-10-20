package com.talkswithtanha.twt.features.auth

import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.Layout
import com.talkswithtanha.twt.core.designsystem.readableWidth
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.talkswithtanha.twt.R
import com.talkswithtanha.twt.core.designsystem.Haptics
import com.talkswithtanha.twt.core.designsystem.WebBackdrop
import com.talkswithtanha.twt.core.designsystem.WebButton
import com.talkswithtanha.twt.core.designsystem.WebButtonStyle
import com.talkswithtanha.twt.core.designsystem.WebColors
import com.talkswithtanha.twt.core.designsystem.WebFonts
import com.talkswithtanha.twt.core.designsystem.WebSticker
import com.talkswithtanha.twt.core.designsystem.WebType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private data class OnboardingPage(
    val sticker: String,
    val lead: String,
    val accent: String,
    val body: String,
    /**
     * Where this page's card sits, off-centre and slightly turned. Fixed, not
     * animated: each page just rests somewhere a little different, the way
     * cards dropped on a table do, so three pages in a row do not look stamped
     * from one mould.
     */
    val sceneShiftX: Float,
    val sceneShiftY: Float,
    val sceneTilt: Float,
    val scene: @Composable (playing: Boolean) -> Unit
)

/**
 * Three pages, and deliberately no mention of a price, a plan or a purchase.
 *
 * Access is invite-only by code. That is what lets the app ship without Apple's
 * IAP, and the same reasoning applies to Google Play Billing — anything in this
 * binary that reads as "buy the app" invites the exact review conversation the
 * model exists to avoid.
 *
 * Each page shows the thing rather than an icon of it: a signal running to its
 * target, a chart being marked up, a question answered. The scenes loop only
 * while their page is on screen, so swiping back restarts the story.
 */
private val pages = listOf(
    OnboardingPage(
        sticker = "Live signals",
        lead = "Signals you can",
        accent = "act on.",
        body = "Entry, stop and targets, with Tanha's chart attached. Follow a trade " +
            "and your phone tells you when it moves.",
        sceneShiftX = -10f, sceneShiftY = -6f, sceneTilt = -2f,
        scene = { SignalScene(it) }
    ),
    OnboardingPage(
        sticker = "Academy",
        lead = "Learn the",
        accent = "reasoning.",
        body = "Lessons that explain why a setup is a setup, not just what to press.",
        sceneShiftX = 12f, sceneShiftY = 10f, sceneTilt = 1.8f,
        scene = { LessonScene(it) }
    ),
    OnboardingPage(
        sticker = "Community",
        lead = "Ask a",
        accent = "real person.",
        body = "A community room, and a private thread with Tanha when you need one.",
        sceneShiftX = -6f, sceneShiftY = 16f, sceneTilt = -1.2f,
        scene = { ChatScene(it) }
    )
)

@Composable
fun OnboardingScreen(
    onSignIn: () -> Unit,
    onCreateAccount: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(pagerState.currentPage) { Haptics.tick(haptics) }

    WebBackdrop {
        Column(
            Modifier
                .fillMaxSize()
                .readableWidth()
                .safeDrawingPadding()
        ) {
            // Top bar: the logo, and a way out for people who already have an
            // account and do not need the tour.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painterResource(R.drawable.twt_logo),
                    contentDescription = "TWT",
                    modifier = Modifier.size(34.dp).clip(CircleShape)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "TWT Global",
                        style = TextStyle(
                            fontFamily = WebFonts.Display,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = WebColors.Text
                        )
                    )
                    Text(
                        "TALKS WITH TANHA",
                        style = TextStyle(
                            fontFamily = WebFonts.Mono,
                            fontSize = 9.sp,
                            letterSpacing = 0.14.em,
                            color = WebColors.Text3
                        )
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "Log in",
                    style = WebType.small.copy(
                        color = WebColors.Text,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    ),
                    modifier = Modifier
                        .clip(CircleShape)
                        .border(1.dp, WebColors.Line2, CircleShape)
                        .clickable(onClick = onSignIn)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { index ->
                OnboardingPageContent(
                    page = pages[index],
                    index = index,
                    pagerState = pagerState
                )
            }

            PageDots(pagerState)

            Spacer(Modifier.height(20.dp))

            val isLast = pagerState.currentPage == pages.lastIndex
            Column(Modifier.padding(horizontal = 20.dp)) {
                WebButton(
                    text = if (isLast) "Create account" else "Next",
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    onClick = {
                        if (isLast) onCreateAccount()
                        else scope.launch {
                            pagerState.animateScrollToPage(
                                pagerState.currentPage + 1,
                                animationSpec = spring(dampingRatio = 0.85f, stiffness = 220f)
                            )
                        }
                    }
                )
                Spacer(Modifier.height(10.dp))
                WebButton(
                    text = "I already have an account",
                    onClick = onSignIn,
                    style = WebButtonStyle.Ghost,
                    height = 50.dp
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

/** How far [index] is from being centred: 0 on screen, ±1 one page away. */
private fun PagerState.offsetFor(index: Int): Float =
    (currentPage - index) + currentPageOffsetFraction

@Composable
private fun OnboardingPageContent(page: OnboardingPage, index: Int, pagerState: PagerState) {
    val playing = pagerState.settledPage == index

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        // The scene drifts at a different speed from the copy as the page is
        // dragged, which is what makes the swipe feel layered rather than flat.
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .graphicsLayer {
                    val offset = pagerState.offsetFor(index)
                    translationX = offset * size.width * 0.35f + page.sceneShiftX.dp.toPx()
                    translationY = page.sceneShiftY.dp.toPx()
                    val s = lerp(1f, 0.86f, offset.absoluteValue.coerceIn(0f, 1f))
                    scaleX = s
                    scaleY = s
                    alpha = 1f - offset.absoluteValue.coerceIn(0f, 1f) * 0.7f
                    rotationZ = page.sceneTilt + offset * -4f
                },
            contentAlignment = Alignment.Center
        ) {
            ScaleToFit(Modifier.fillMaxSize()) { page.scene(playing) }
        }

        Column(
            Modifier.graphicsLayer {
                val offset = pagerState.offsetFor(index)
                translationX = offset * size.width * -0.12f
                alpha = 1f - offset.absoluteValue.coerceIn(0f, 1f)
            }
        ) {
            // Keyed on playing so it stamps again each time the page arrives.
            if (playing) {
                WebSticker(page.sticker, rotation = if (index % 2 == 0) -3f else 3f, delayMillis = 150)
            } else {
                Spacer(Modifier.height(28.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(page.lead, style = WebType.h1(40.sp))
            Text(page.accent, style = WebType.accent(40.sp))
            Spacer(Modifier.height(12.dp))
            Text(page.body, style = WebType.lead)
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** The site's progress pills: the current one stretches and fills green. */
@Composable
private fun PageDots(pagerState: PagerState) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(pages.size) { i ->
            val selected = pagerState.currentPage == i
            val width by animateDpAsState(
                if (selected) 26.dp else 8.dp,
                spring(dampingRatio = 0.6f, stiffness = 400f),
                label = "dotWidth"
            )
            val color by animateColorAsState(
                if (pagerState.currentPage >= i) WebColors.Brand else Color.White.copy(alpha = 0.18f),
                label = "dotColor"
            )
            Box(
                Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

// ── Shared scene helpers ───────────────────────────────────────────────────

/**
 * A 0→1 clock that runs for [durationMillis], holds for [holdMillis], and starts
 * again — only while [playing]. Scenes read their choreography off it.
 */
@Composable
private fun rememberSceneClock(playing: Boolean, durationMillis: Int, holdMillis: Long = 1400): Animatable<Float, *> {
    val clock = remember { Animatable(0f) }
    LaunchedEffect(playing) {
        if (!playing) {
            clock.snapTo(0f)
            return@LaunchedEffect
        }
        while (true) {
            clock.snapTo(0f)
            clock.animateTo(1f, tween(durationMillis, easing = LinearEasing))
            delay(holdMillis)
        }
    }
    return clock
}

/** 0 before [start], 1 after [end], eased in between. For staging a timeline. */
private fun phase(t: Float, start: Float, end: Float): Float {
    val x = ((t - start) / (end - start)).coerceIn(0f, 1f)
    return x * x * (3 - 2 * x)
}

@Composable
private fun SceneCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    // Fixed in place on purpose. A card that drifts on its own reads as
    // decoration; the motion belongs to what happens inside it.
    Box(
        modifier
            .widthIn(max = 360.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(listOf(WebColors.Ink3, WebColors.Ink2))
            )
            .border(1.dp, WebColors.Line2, RoundedCornerShape(24.dp))
            .padding(18.dp)
    ) { content() }
}

private val monoSmall = TextStyle(
    fontFamily = WebFonts.Mono,
    fontSize = 11.sp,
    fontWeight = FontWeight.Medium,
    color = WebColors.Text3
)

// ── Page 1: a signal running to its target ─────────────────────────────────

/** Normalised price path, left to right: dips toward the stop, then runs to TP. */
private val pricePoints = listOf(
    0.00f to 0.62f, 0.10f to 0.58f, 0.18f to 0.66f, 0.26f to 0.70f,
    0.34f to 0.60f, 0.44f to 0.52f, 0.52f to 0.55f, 0.62f to 0.40f,
    0.72f to 0.34f, 0.80f to 0.26f, 0.90f to 0.20f, 1.00f to 0.14f
)

@Composable
private fun SignalScene(playing: Boolean) {
    val clock = rememberSceneClock(playing, durationMillis = 4200)
    val t = clock.value
    val draw = phase(t, 0.08f, 0.82f)
    val hit = phase(t, 0.80f, 0.90f)

    Box(contentAlignment = Alignment.TopCenter) {
        SceneCard(Modifier.padding(top = 36.dp)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "XAUUSD",
                        style = TextStyle(
                            fontFamily = WebFonts.Display,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = WebColors.Text
                        )
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(WebColors.BrandSoft)
                            .border(1.dp, WebColors.Brand.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text("BUY", style = monoSmall.copy(color = WebColors.Brand, fontWeight = FontWeight.Bold))
                    }
                    Spacer(Modifier.weight(1f))
                    val pips = (lerp(0f, 120f, draw)).toInt()
                    Text(
                        "+$pips pips",
                        style = TextStyle(
                            fontFamily = WebFonts.Mono,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = if (pips > 0) WebColors.Brand else WebColors.Text3
                        )
                    )
                }
                Spacer(Modifier.height(14.dp))
                PriceChart(progress = draw, hit = hit)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LevelChip("ENTRY", "2,398", WebColors.Text2, Modifier.weight(1f))
                    LevelChip("STOP", "2,384", WebColors.Red, Modifier.weight(1f))
                    LevelChip("TP1", "2,410", WebColors.Brand, Modifier.weight(1f), lit = hit > 0.5f)
                }
            }
        }

        // The push notification, dropping in as the target is reached.
        Row(
            Modifier
                .widthIn(max = 320.dp)
                .fillMaxWidth(0.9f)
                .graphicsLayer {
                    translationY = lerp(-60f, 0f, hit) * density
                    alpha = hit
                }
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xF21C1916))
                .border(1.dp, WebColors.Line2, RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(WebColors.Brand),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.NotificationsActive,
                    contentDescription = null,
                    tint = WebColors.OnBrand,
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    "Gold hit target 1",
                    style = TextStyle(
                        fontFamily = WebFonts.Body,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = WebColors.Text
                    )
                )
                Text("Move your stop to entry.", style = WebType.small.copy(fontSize = 12.sp))
            }
        }
    }
}

@Composable
private fun LevelChip(label: String, value: String, color: Color, modifier: Modifier, lit: Boolean = false) {
    val fill by animateColorAsState(
        if (lit) WebColors.Brand else Color.White.copy(alpha = 0.03f),
        label = "chip"
    )
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(fill)
            .border(1.dp, WebColors.Line, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(label, style = monoSmall.copy(fontSize = 9.5.sp, color = if (lit) WebColors.OnBrand else WebColors.Text3))
        Text(
            value,
            style = TextStyle(
                fontFamily = WebFonts.Mono,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = if (lit) WebColors.OnBrand else color
            )
        )
    }
}

@Composable
private fun PriceChart(progress: Float, hit: Float) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(130.dp)
    ) {
        val w = size.width
        val h = size.height
        fun p(x: Float, y: Float) = Offset(x * w, y * h)

        // Level lines: target, entry, stop.
        val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
        listOf(
            0.14f to WebColors.Brand.copy(alpha = 0.55f),
            0.62f to WebColors.Text3,
            0.86f to WebColors.Red.copy(alpha = 0.6f)
        ).forEach { (y, c) ->
            drawLine(c, p(0f, y), p(1f, y), strokeWidth = 2f, pathEffect = dash)
        }

        // The price, drawn up to [progress].
        val full = Path().apply {
            pricePoints.forEachIndexed { i, (x, y) ->
                if (i == 0) moveTo(x * w, y * h) else {
                    val (px, py) = pricePoints[i - 1]
                    val cx = (px + x) / 2f * w
                    cubicTo(cx, py * h, cx, y * h, x * w, y * h)
                }
            }
        }
        val measure = PathMeasure().apply { setPath(full, false) }
        val drawn = Path()
        measure.getSegment(0f, measure.length * progress, drawn, true)
        val head = measure.getPosition(measure.length * progress)

        // Soft fill under the drawn line.
        if (progress > 0.01f) {
            val area = Path().apply {
                addPath(drawn)
                lineTo(head.x, h)
                lineTo(0f, h)
                close()
            }
            drawPath(
                area,
                Brush.verticalGradient(listOf(WebColors.Brand.copy(alpha = 0.22f), Color.Transparent))
            )
        }
        drawPath(
            drawn,
            WebColors.Brand,
            style = Stroke(width = 5f, cap = StrokeCap.Round)
        )
        if (progress > 0.01f) {
            drawCircle(WebColors.Brand.copy(alpha = 0.25f + 0.25f * hit), radius = 14f + 16f * hit, center = head)
            drawCircle(WebColors.Brand, radius = 7f, center = head)
        }
    }
}

// ── Page 2: a chart being marked up ────────────────────────────────────────

/** open, close, high, low — normalised, 0 at the top. */
private val candles = listOf(
    floatArrayOf(0.55f, 0.62f, 0.50f, 0.68f),
    floatArrayOf(0.62f, 0.70f, 0.58f, 0.76f),
    floatArrayOf(0.70f, 0.66f, 0.63f, 0.80f),
    floatArrayOf(0.66f, 0.78f, 0.64f, 0.90f), // the sweep
    floatArrayOf(0.78f, 0.56f, 0.52f, 0.82f), // the reclaim
    floatArrayOf(0.56f, 0.44f, 0.40f, 0.60f),
    floatArrayOf(0.44f, 0.32f, 0.28f, 0.48f),
    floatArrayOf(0.32f, 0.22f, 0.18f, 0.36f)
)

@Composable
private fun LessonScene(playing: Boolean) {
    val clock = rememberSceneClock(playing, durationMillis = 4200)
    val t = clock.value
    val circle = phase(t, 0.58f, 0.74f)
    val note = phase(t, 0.70f, 0.82f)

    SceneCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(WebColors.Brand),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.PlayArrow, null, tint = WebColors.OnBrand, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Liquidity sweeps",
                        style = TextStyle(
                            fontFamily = WebFonts.Display,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = WebColors.Text
                        )
                    )
                    Text("LESSON 3 · 8 MIN", style = monoSmall.copy(fontSize = 10.sp))
                }
            }
            Spacer(Modifier.height(12.dp))

            BoxWithConstraints(Modifier.fillMaxWidth().height(150.dp)) {
                val chartWidth = maxWidth
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val slot = w / candles.size
                    val body = slot * 0.46f
                    candles.forEachIndexed { i, (open, close, high, low) ->
                        // Each candle grows in, one after another.
                        val grow = phase(t, 0.05f + i * 0.06f, 0.15f + i * 0.06f)
                        if (grow <= 0f) return@forEachIndexed
                        val up = close < open
                        val color = if (up) WebColors.Brand else WebColors.Red
                        val cx = slot * i + slot / 2f
                        val mid = (open + close) / 2f * h
                        fun grown(y: Float) = mid + (y * h - mid) * grow
                        drawLine(color.copy(alpha = 0.7f), Offset(cx, grown(high)), Offset(cx, grown(low)), strokeWidth = 3f)
                        val top = grown(minOf(open, close))
                        val bottom = grown(maxOf(open, close))
                        drawRoundRect(
                            color,
                            topLeft = Offset(cx - body / 2f, top),
                            size = Size(body, (bottom - top).coerceAtLeast(3f)),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f)
                        )
                    }

                    // The mark-up: a ring drawn around the sweep candle.
                    if (circle > 0f) {
                        val c = Offset(slot * 3.5f, 0.80f * h)
                        drawArc(
                            color = WebColors.Brand2,
                            startAngle = -90f,
                            sweepAngle = 360f * circle,
                            useCenter = false,
                            topLeft = Offset(c.x - slot * 0.9f, c.y - h * 0.16f),
                            size = Size(slot * 1.8f, h * 0.32f),
                            style = Stroke(width = 4f, cap = StrokeCap.Round)
                        )
                    }
                }

                // The note that explains the ring.
                Box(
                    Modifier
                        .offset(x = chartWidth * 0.52f, y = 96.dp)
                        .graphicsLayer {
                            alpha = note
                            translationY = (1f - note) * 12f * density
                            rotationZ = 2f
                        }
                        .clip(RoundedCornerShape(10.dp))
                        .background(WebColors.Text)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        "Stops taken → then the move",
                        style = TextStyle(
                            fontFamily = WebFonts.Body,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = WebColors.Ink
                        )
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            // Lesson progress, filling as the story plays.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(lerp(0.2f, 1f, t))
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(WebColors.Brand)
                )
            }
        }
    }
}

// ── Page 3: a question answered ────────────────────────────────────────────

@Composable
private fun ChatScene(playing: Boolean) {
    val clock = rememberSceneClock(playing, durationMillis = 4200)
    val t = clock.value
    val ask = phase(t, 0.04f, 0.16f)
    val typing = if (t in 0.22f..0.48f) 1f else 0f
    val answer = phase(t, 0.48f, 0.60f)
    val second = phase(t, 0.66f, 0.78f)
    val heart = phase(t, 0.84f, 0.92f)

    SceneCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painterResource(R.drawable.twt_logo),
                    contentDescription = null,
                    modifier = Modifier.size(30.dp).clip(CircleShape)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Tanha",
                        style = TextStyle(
                            fontFamily = WebFonts.Display,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = WebColors.Text
                        )
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(WebColors.Mint))
                        Spacer(Modifier.width(5.dp))
                        Text("online", style = monoSmall.copy(fontSize = 10.sp, color = WebColors.Mint))
                    }
                }
            }

            Bubble("Is gold a buy here?", mine = true, progress = ask)

            if (typing > 0f) TypingDots()

            Box {
                Bubble("Wait for the retest at 2,410.", mine = false, progress = answer)
                // The reaction, popping onto the answer.
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = 8.dp, y = 12.dp)
                        .graphicsLayer {
                            val s = if (heart > 0f) 0.4f + 0.6f * heart + 0.3f * (1f - heart) * heart * 4f else 0f
                            scaleX = s
                            scaleY = s
                        }
                        .clip(CircleShape)
                        .background(WebColors.Ink)
                        .border(1.dp, WebColors.Line2, CircleShape)
                        .padding(4.dp)
                ) {
                    Icon(Icons.Filled.Favorite, null, tint = WebColors.Red, modifier = Modifier.size(12.dp))
                }
            }
            Bubble("Then yes. Stop under 2,396.", mine = false, progress = second)
        }
    }
}

@Composable
private fun Bubble(text: String, mine: Boolean, progress: Float) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Box(
            Modifier
                .graphicsLayer {
                    alpha = progress
                    val s = lerp(0.7f, 1f, progress)
                    scaleX = s
                    scaleY = s
                    translationY = (1f - progress) * 14f * density
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (mine) 1f else 0f, 1f)
                }
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (mine) 16.dp else 4.dp,
                        bottomEnd = if (mine) 4.dp else 16.dp
                    )
                )
                .background(if (mine) WebColors.Brand else WebColors.WhatsAppBubble)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text,
                style = TextStyle(
                    fontFamily = WebFonts.Body,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = if (mine) WebColors.OnBrand else WebColors.WhatsAppBubbleText
                )
            )
        }
    }
}

@Composable
private fun TypingDots() {
    val dots = rememberInfiniteTransition(label = "typing")
    Row(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(WebColors.WhatsAppBubble)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        repeat(3) { i ->
            val lift by dots.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(420, delayMillis = i * 120),
                    RepeatMode.Reverse
                ),
                label = "dot$i"
            )
            Box(
                Modifier
                    .size(7.dp)
                    .graphicsLayer {
                        translationY = -lift * 4f * density
                        alpha = 0.4f + 0.6f * lift
                    }
                    .clip(CircleShape)
                    .background(WebColors.WhatsAppBubbleText)
            )
        }
    }
}

/**
 * Lays the scene out at its natural size and, when the space is shorter than
 * that — a small phone, or large system text eating into it — shrinks the
 * whole thing uniformly rather than letting it be squashed. Never enlarges.
 */
@Composable
private fun ScaleToFit(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val placeable = measurables.first().measure(
            constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity)
        )
        val scale = if (placeable.height > constraints.maxHeight && placeable.height > 0) {
            constraints.maxHeight.toFloat() / placeable.height
        } else 1f
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.placeWithLayer(
                (constraints.maxWidth - placeable.width) / 2,
                (constraints.maxHeight - placeable.height) / 2
            ) {
                scaleX = scale
                scaleY = scale
            }
        }
    }
}
