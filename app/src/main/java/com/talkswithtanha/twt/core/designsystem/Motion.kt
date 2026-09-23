package com.talkswithtanha.twt.core.designsystem

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp

/**
 * The app's motion.
 *
 * One file, because a set of screens that each invent their own spring is a set
 * of screens that feel like different apps. Everything here is a *spring* rather
 * than a duration curve — a spring responds to being interrupted, which is what
 * makes a gesture feel attached to a finger instead of played back at it.
 */
object Motion {

    /** Anything a finger is on: presses, drags, the bottom bar indicator. */
    fun <T> snappy(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)

    /** Content arriving or leaving: cards, sheets, sections. */
    fun <T> gentle(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.85f, stiffness = 260f)

    /** Small state flips — a colour, a tint, an alpha. */
    fun <T> quick(): FiniteAnimationSpec<T> = tween(180)

    /** How long each item in a list waits before it appears, in millis. */
    const val STAGGER_STEP = 45L

    /** How far content rises as it fades in, in pixels. */
    const val RISE = 18f
}

/**
 * The press response every tappable surface shares.
 *
 * Scale rather than a ripple: on near-black surfaces a ripple is close to
 * invisible, and the whole design separates things by luminance rather than by
 * colour. Pass the same [interactionSource] to the clickable that owns the tap.
 */
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.97f
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = Motion.snappy(),
        label = "press"
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Rises and fades in once, [index] places down a list.
 *
 * Deliberately capped: past about a dozen rows the last one would be waiting
 * half a second for its turn, which stops reading as choreography and starts
 * reading as the app being slow.
 */
fun Modifier.staggeredAppear(index: Int, enabled: Boolean = true): Modifier = composed {
    if (!enabled) return@composed this

    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(index.coerceAtMost(12) * Motion.STAGGER_STEP)
        appeared = true
    }
    val progress by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = Motion.gentle(),
        label = "appear"
    )
    graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * Motion.RISE * density
    }
}

/**
 * A number that rolls when it changes, rather than blinking to the new value.
 *
 * The counterpart of iOS's `contentTransition(.numericText())`. Digits that
 * increase slide up, digits that decrease slide down, so a price ticking is
 * legible as movement and its direction is readable before the number is.
 */
@Composable
fun AnimatedNumber(
    value: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    increasing: Boolean = true
) {
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            val direction = if (increasing) 1 else -1
            (slideInVertically(Motion.gentle()) { height -> direction * height } + fadeIn(Motion.quick()))
                .togetherWith(
                    slideOutVertically(Motion.gentle()) { height -> -direction * height } +
                        fadeOut(Motion.quick())
                )
                // The box must not resize as the digits swap, or everything
                // beside it twitches on every tick.
                .using(SizeTransform(clip = false))
        },
        modifier = modifier,
        label = "number"
    ) { shown ->
        Text(text = shown, style = style, color = color)
    }
}

/**
 * The sweep that runs across a loading placeholder.
 *
 * Returned as a 0..1 position rather than a colour so each caller can decide
 * how wide the highlight is and which way it travels.
 */
@Composable
fun rememberShimmerProgress(): Float {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep"
    )
    return progress
}
