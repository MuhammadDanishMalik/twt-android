package com.talkswithtanha.twt.core.designsystem

import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.interaction.collectIsPressedAsState
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.composed
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.talkswithtanha.twt.R
import kotlinx.coroutines.delay

/**
 * The public website's look, for the screens a member sees before they are in.
 *
 * Onboarding, sign in, verification and the access gate are the same journey as
 * tradewithtanha.io/get-access — somebody often has the page open in a browser
 * while they do it — so they share its palette and type rather than the in-app
 * one. Values are copied from `twt-admin/components/landing/landing.css`; change
 * them there first.
 */
object WebColors {
    /** `--ink`: warm near-black, not the app's neutral black. */
    val Ink = Color(0xFF0B0A09)
    val Ink2 = Color(0xFF13110F)
    val Ink3 = Color(0xFF1C1916)
    /** `--line` / `--line-2`: warm hairlines. */
    val Line = Color(0x17FFF4E6)
    val Line2 = Color(0x29FFF4E6)

    val Text = Color(0xFFF6F1EA)
    val Text2 = Color(0xA8F6F1EA)
    val Text3 = Color(0x6BF6F1EA)

    /** `--brand`: the green from the TWT logo. */
    val Brand = Color(0xFF00FF00)
    val Brand2 = Color(0xFF7DFF7A)
    val BrandSoft = Color(0x1A00FF00)
    /** Text that sits on [Brand]. White on neon green is unreadable. */
    val OnBrand = Color(0xFF041A04)

    val Mint = Color(0xFF34D399)
    val MintSoft = Color(0x1F34D399)

    val WhatsApp = Color(0xFF25D366)
    val OnWhatsApp = Color(0xFF04210F)
    /** `.lp-msg-preview`: the outgoing WhatsApp bubble. */
    val WhatsAppBubble = Color(0xFF0F2E1D)
    val WhatsAppBubbleText = Color(0xFFD7F5E3)

    val Red = Color(0xFFF87171)
    val Amber = Color(0xFFF59E0B)
}

/**
 * The site's three faces, bundled (all SIL OFL):
 *
 * - **Bricolage Grotesque** for everything. Headings use the narrower, larger
 *   optical size the site sets with `font-variation-settings: 'wdth' 88`.
 * - **Instrument Serif italic** for the one emphasised phrase per heading.
 * - **JetBrains Mono** for codes and numbers.
 *
 * Variable fonts. Below API 26 Android ignores the axes and every weight renders
 * as the file's default instance, which is still the right face.
 */
@OptIn(ExperimentalTextApi::class)
object WebFonts {

    private fun bricolage(weight: Int, width: Float, opticalSize: Float) = Font(
        R.font.bricolage_grotesque,
        FontWeight(weight),
        variationSettings = FontVariation.Settings(
            FontVariation.weight(weight),
            FontVariation.width(width),
            FontVariation.Setting("opsz", opticalSize)
        )
    )

    private fun mono(weight: Int) = Font(
        R.font.jetbrains_mono,
        FontWeight(weight),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight))
    )

    /** Body copy and buttons. */
    val Body = FontFamily(
        bricolage(400, 100f, 14f),
        bricolage(500, 100f, 14f),
        bricolage(600, 100f, 14f),
        bricolage(700, 100f, 14f)
    )

    /** Headings: condensed and tight, like `.lp-h1`. */
    val Display = FontFamily(
        bricolage(600, 92f, 72f),
        bricolage(700, 92f, 72f),
        bricolage(800, 92f, 72f)
    )

    val Serif = FontFamily(
        Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic)
    )

    val Mono = FontFamily(mono(400), mono(500), mono(600), mono(700))
}

/** Text styles matching the site's classes. */
object WebType {
    fun h1(size: TextUnit = 44.sp) = TextStyle(
        fontFamily = WebFonts.Display,
        fontWeight = FontWeight.Bold,
        fontSize = size,
        lineHeight = size * 1.02f,
        // The site uses -0.045em at up to 84px. At phone sizes that packs the
        // letters into each other, so this is eased off.
        letterSpacing = (-0.02).em,
        color = WebColors.Text
    )

    val h3 = TextStyle(
        fontFamily = WebFonts.Display,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.01).em,
        color = WebColors.Text
    )

    val lead = TextStyle(
        fontFamily = WebFonts.Body,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        color = WebColors.Text2
    )

    val small = TextStyle(
        fontFamily = WebFonts.Body,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = WebColors.Text3
    )

    /** The emphasised phrase: `.lp-serif .lp-accent`. */
    fun accent(size: TextUnit) = TextStyle(
        fontFamily = WebFonts.Serif,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.Normal,
        fontSize = size * 1.06f,
        lineHeight = size * 1.02f,
        letterSpacing = (-0.01).em,
        color = WebColors.Brand
    )
}

/**
 * The site's hero ground: warm ink, a grid that fades out from the middle, and
 * a slow green glow drifting behind it so the screen never sits perfectly still.
 */
@Composable
fun WebBackdrop(
    modifier: Modifier = Modifier,
    gridSpacing: Dp = 44.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val drift = rememberInfiniteTransition(label = "backdrop")
    val t by drift.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift"
    )

    Box(modifier.fillMaxSize().background(WebColors.Ink)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Glow first, so the grid reads on top of it.
            val glowCenter = Offset(w * (0.25f + 0.5f * t), h * (0.18f + 0.1f * t))
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(WebColors.Brand.copy(alpha = 0.10f), Color.Transparent),
                    center = glowCenter,
                    radius = w * 0.8f
                ),
                radius = w * 0.8f,
                center = glowCenter
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.05f), Color.Transparent),
                    center = Offset(w * 0.72f, h * 0.3f),
                    radius = w * 0.7f
                ),
                radius = w * 0.7f,
                center = Offset(w * 0.72f, h * 0.3f)
            )

            // The grid, faded by distance from the upper middle — the site does
            // this with a CSS mask.
            val step = gridSpacing.toPx()
            val focus = Offset(w / 2f, h * 0.32f)
            val reach = maxOf(w, h) * 0.45f
            fun fade(p: Offset): Float =
                // Scales the hairline's own alpha, never replaces it: the site
                // draws these at `--line` and then masks them down.
                (1f - ((p - focus).getDistance() / reach)).coerceIn(0f, 1f) * 0.9f

            var x = (w / 2f) % step
            while (x < w) {
                val a = fade(Offset(x, focus.y))
                if (a > 0.01f) drawLine(
                    brush = Brush.verticalGradient(
                        listOf(Color.Transparent, WebColors.Line.copy(alpha = WebColors.Line.alpha * a), Color.Transparent),
                        startY = focus.y - reach,
                        endY = focus.y + reach
                    ),
                    start = Offset(x, 0f),
                    end = Offset(x, h),
                    strokeWidth = 1f
                )
                x += step
            }
            var y = focus.y % step
            while (y < h) {
                val a = fade(Offset(focus.x, y))
                if (a > 0.01f) drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, WebColors.Line.copy(alpha = WebColors.Line.alpha * a), Color.Transparent),
                        startX = 0f,
                        endX = w
                    ),
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1f
                )
                y += step
            }
        }
        content()
    }
}

/** `.lp-eyebrow`: the pill above a heading, with a breathing mint dot. */
@Composable
fun WebEyebrow(text: String, modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "eyebrow")
    val ring by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ring"
    )
    Row(
        modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.03f))
            .border(1.dp, WebColors.Line2, CircleShape)
            .padding(start = 10.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(8.dp + 8.dp * ring)
                    .clip(CircleShape)
                    .background(WebColors.Mint.copy(alpha = 0.22f * (1f - ring * 0.6f)))
            )
            Box(Modifier.size(8.dp).clip(CircleShape).background(WebColors.Mint))
        }
        Text(
            text = text,
            style = TextStyle(
                fontFamily = WebFonts.Body,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                letterSpacing = 0.02.em,
                color = WebColors.Text2
            )
        )
    }
}

/**
 * A two-line heading: plain [lead], then the serif green [accent] beneath it,
 * each rising into place — the site's `SplitWords` reveal, by line.
 */
@Composable
fun WebHeadline(
    lead: String,
    accent: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 44.sp,
    startDelayMillis: Int = 0
) {
    Column(modifier) {
        RiseIn(delayMillis = startDelayMillis) {
            Text(lead, style = WebType.h1(size))
        }
        RiseIn(delayMillis = startDelayMillis + 140) {
            Text(accent, style = WebType.accent(size))
        }
    }
}

/** Fades and rises into place once, after [delayMillis]. */
@Composable
fun RiseIn(
    modifier: Modifier = Modifier,
    delayMillis: Int = 0,
    distance: Dp = 18.dp,
    content: @Composable () -> Unit
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        progress.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow))
    }
    Box(
        modifier.graphicsLayer {
            alpha = progress.value.coerceIn(0f, 1f)
            translationY = (1f - progress.value) * distance.toPx()
        }
    ) { content() }
}

enum class WebButtonStyle { Primary, Ghost, WhatsApp }

/** `.lp-btn`: a full pill. Green with dark text, a ghost outline, or WhatsApp. */
@Composable
fun WebButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: WebButtonStyle = WebButtonStyle.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    height: Dp = 54.dp
) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    val alpha by animateFloatAsState(
        targetValue = if (enabled || loading) 1f else 0.35f,
        animationSpec = Motion.quick(),
        label = "enabled"
    )
    val (fill, content) = when (style) {
        WebButtonStyle.Primary -> WebColors.Brand to WebColors.OnBrand
        WebButtonStyle.Ghost -> Color.White.copy(alpha = 0.03f) to WebColors.Text
        WebButtonStyle.WhatsApp -> WebColors.WhatsApp to WebColors.OnWhatsApp
    }

    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .pressScale(interaction, pressedScale = 0.96f)
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .background(fill)
            .pressFlood(
                interaction,
                if (style == WebButtonStyle.Ghost) WebColors.Brand.copy(alpha = 0.16f)
                else Color.White.copy(alpha = 0.38f)
            )
            .then(
                if (style == WebButtonStyle.Ghost) Modifier.border(1.dp, WebColors.Line2, CircleShape)
                else Modifier
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled && !loading
            ) {
                Haptics.tap(haptics)
                onClick()
            }
            .padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = content,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp)
            )
        } else {
            if (icon != null) {
                // The icon leans the way it points while the finger is down.
                val pressed by interaction.collectIsPressedAsState()
                val nudge by animateFloatAsState(
                    if (pressed) 1f else 0f,
                    spring(dampingRatio = 0.5f, stiffness = 600f),
                    label = "nudge"
                )
                Icon(
                    icon,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier
                        .size(19.dp)
                        .graphicsLayer { translationX = nudge * 4.dp.toPx() }
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = text,
                style = TextStyle(
                    fontFamily = WebFonts.Body,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    letterSpacing = (-0.01).em,
                    color = content
                ),
                maxLines = 1
            )
        }
    }
}

/**
 * `.lp-sticker`: a flat, tilted mono label that stamps down onto the page —
 * lands big, overshoots, settles.
 */
@Composable
fun WebSticker(
    text: String,
    modifier: Modifier = Modifier,
    rotation: Float = -3f,
    background: Color = WebColors.Brand,
    color: Color = WebColors.OnBrand,
    delayMillis: Int = 0
) {
    val stamp = remember { Animatable(0f) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        // Felt as it lands, not as it starts falling.
        launch {
            delay(110)
            Haptics.tick(haptics)
        }
        stamp.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 420f))
    }
    Box(
        modifier
            .graphicsLayer {
                val s = stamp.value
                alpha = s.coerceIn(0f, 1f)
                val scale = 1.6f - 0.6f * s
                scaleX = scale
                scaleY = scale
                rotationZ = rotation * (2f - s)
            }
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text.uppercase(),
            style = TextStyle(
                fontFamily = WebFonts.Mono,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.5.sp,
                letterSpacing = 0.08.em,
                color = color
            )
        )
    }
}

/** `.lp-card`-ish: a warm raised panel with a hairline rim. */
@Composable
fun WebCard(
    modifier: Modifier = Modifier,
    padding: Dp = 20.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(WebColors.Ink2)
            .border(1.dp, WebColors.Line, RoundedCornerShape(22.dp))
            .padding(padding)
    ) { content() }
}

/**
 * The website's button hover (`useButtonFx`), for touch: a disc of [color]
 * floods out from exactly where the finger landed and fades as it lifts.
 * Place it after the modifier that clips and fills the button, and pass the
 * same [interactionSource] as the clickable.
 */
fun Modifier.pressFlood(interactionSource: InteractionSource, color: Color): Modifier = composed {
    val radius = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }
    var center by remember { mutableStateOf(Offset.Unspecified) }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    center = interaction.pressPosition
                    launch { alpha.snapTo(1f) }
                    launch {
                        radius.snapTo(0f)
                        radius.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
                    }
                }
                is PressInteraction.Release, is PressInteraction.Cancel ->
                    launch { alpha.animateTo(0f, tween(420)) }
            }
        }
    }

    drawBehind {
        if (alpha.value > 0f && center.isSpecified) {
            // Far enough to reach the furthest corner from the touch.
            val reach = listOf(
                Offset.Zero, Offset(size.width, 0f), Offset(0f, size.height), Offset(size.width, size.height)
            ).maxOf { (it - center).getDistance() }
            drawCircle(
                color = color.copy(alpha = color.alpha * alpha.value),
                radius = reach * radius.value,
                center = center
            )
        }
    }
}

/**
 * A one-shot burst of confetti in the brand colours, fired each time
 * [trigger] changes to a new non-zero value. Draw-only: taps pass through.
 */
@Composable
fun ConfettiBurst(trigger: Int, modifier: Modifier = Modifier) {
    if (trigger == 0) return
    val progress = remember(trigger) { Animatable(0f) }
    val pieces = remember(trigger) {
        val palette = listOf(WebColors.Brand, WebColors.Brand2, WebColors.Mint, WebColors.Text, WebColors.Amber)
        List(70) {
            val angle = Math.toRadians(-90.0 + (Math.random() - 0.5) * 110.0)
            val speed = 0.55f + Math.random().toFloat() * 0.75f
            Confetto(
                vx = (kotlin.math.cos(angle) * speed).toFloat(),
                vy = (kotlin.math.sin(angle) * speed).toFloat(),
                spin = (Math.random().toFloat() - 0.5f) * 1440f,
                color = palette[it % palette.size],
                w = 5f + Math.random().toFloat() * 6f,
                h = 9f + Math.random().toFloat() * 10f
            )
        }
    }
    LaunchedEffect(trigger) { progress.animateTo(1f, tween(1600, easing = LinearEasing)) }

    Canvas(modifier.fillMaxSize()) {
        val t = progress.value
        if (t >= 1f) return@Canvas
        val origin = Offset(size.width / 2f, size.height * 0.88f)
        val scale = size.height * 1.1f
        pieces.forEach { p ->
            // Thrown up, pulled down: plain projectile motion.
            val x = origin.x + p.vx * scale * t
            val y = origin.y + p.vy * scale * t + 0.9f * scale * t * t
            val fade = if (t > 0.7f) 1f - (t - 0.7f) / 0.3f else 1f
            rotate(p.spin * t, pivot = Offset(x, y)) {
                drawRect(
                    color = p.color.copy(alpha = fade),
                    topLeft = Offset(x - p.w / 2f * density, y - p.h / 2f * density),
                    size = androidx.compose.ui.geometry.Size(p.w * density, p.h * density)
                )
            }
        }
    }
}

private class Confetto(
    val vx: Float,
    val vy: Float,
    val spin: Float,
    val color: Color,
    val w: Float,
    val h: Float
)

/**
 * Caps content at a comfortable reading width and centres it. A no-op on
 * phones; on a tablet or a phone in landscape it stops text and buttons
 * stretching edge to edge. Place it right after `fillMaxSize()`.
 */
fun Modifier.readableWidth(max: Dp = 560.dp): Modifier =
    this.wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = max)
