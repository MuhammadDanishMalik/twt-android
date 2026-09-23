package com.talkswithtanha.twt.core.designsystem

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDp
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
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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

/** Holds the last value without making the read observable, so it survives recomposition. */
private class LastValue(var text: String)

/** Everything a number is not: separators, signs, currency marks. */
private fun String.numericOrNull(): Double? =
    filter { it.isDigit() || it == '.' || it == '-' }.toDoubleOrNull()

/**
 * A number whose digits roll when it changes, rather than blinking to the new value.
 *
 * The counterpart of iOS's `contentTransition(.numericText())`. Only the digits
 * that actually changed move — "2,454" ticking to "2,455" rolls the last column
 * and leaves the rest standing, which is what makes it read as a counter rather
 * than as the whole label being replaced. Digits roll up when the value rose and
 * down when it fell, so the direction is readable before the number is.
 *
 * Figures are tabular ([fontFeatureSettings] `tnum`): in a proportional face a
 * '1' is narrower than an '8', so without it every tick would reflow the row and
 * nudge whatever sits beside it.
 */
@Composable
fun AnimatedNumber(
    value: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified
) {
    // Read the previous value during composition, then record the new one once
    // composition has committed — a plain holder rather than snapshot state,
    // because subscribing to it here would recompose us forever.
    val last = remember { LastValue(value) }
    val rollUp = remember(value) {
        val before = last.text.numericOrNull()
        val after = value.numericOrNull()
        if (before == null || after == null) true else after >= before
    }
    SideEffect { last.text = value }

    val figures = style.copy(fontFeatureSettings = "tnum")
    val characters = value.toList()

    Row(
        modifier = modifier
            // One node, not one per digit. Splitting the number into a column
            // per character is what makes the roll possible, but it also puts
            // every character in the accessibility tree on its own — a screen
            // reader would announce "341.00 PKR" as "three, four, one, dot,
            // zero, zero, P, K, R". This collapses the subtree back into the
            // single string a person actually reads.
            .clearAndSetSemantics { contentDescription = value }
    ) {
        characters.forEachIndexed { index, character ->
            // Keyed by distance from the right, because numbers grow leftwards:
            // the units column has to stay the units column when a digit is
            // added, or every column would animate on a change of magnitude.
            key(characters.size - index) {
                AnimatedContent(
                    targetState = character,
                    transitionSpec = {
                        if (initialState.isDigit() && targetState.isDigit()) {
                            val direction = if (rollUp) 1 else -1
                            (slideInVertically(Motion.gentle()) { height -> direction * height } +
                                fadeIn(Motion.quick()))
                                .togetherWith(
                                    slideOutVertically(Motion.gentle()) { height -> -direction * height } +
                                        fadeOut(Motion.quick())
                                )
                                // Clipped, so a digit appears from behind the
                                // edge the way a mechanical roller would.
                                .using(SizeTransform(clip = true))
                        } else {
                            // Separators and signs cross-fade. A comma doing a
                            // barrel roll when a number crosses a thousand is
                            // the kind of detail that reads as a bug.
                            fadeIn(Motion.quick())
                                .togetherWith(fadeOut(Motion.quick()))
                                .using(SizeTransform(clip = false))
                        }
                    },
                    label = "digit"
                ) { shown ->
                    Text(text = shown.toString(), style = figures, color = color, softWrap = false)
                }
            }
        }
    }
}

/**
 * Text that blurs out and back in when it changes.
 *
 * iOS 17's `.transition(.blurReplace)`. A straight cross-fade of two strings
 * leaves both legible at once and the eye tries to read the overlap; blurring
 * the outgoing text destroys its shape first, so only one thing is ever
 * readable. Below API 31 [Modifier.blur] does nothing and this degrades to the
 * fade, which is fine — it is the same motion with less of it.
 */
@Composable
fun AnimatedText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE
) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            fadeIn(Motion.gentle())
                .togetherWith(fadeOut(Motion.quick()))
                .using(SizeTransform(clip = false))
        },
        modifier = modifier,
        label = "text"
    ) { shown ->
        val blur by transition.animateDp(
            transitionSpec = { Motion.gentle() },
            label = "blur"
        ) { state -> if (state == EnterExitState.Visible) 0.dp else BLUR_RADIUS }

        Text(
            text = shown,
            style = style,
            color = color,
            maxLines = maxLines,
            // Unbounded, or the blur would be clipped to the text box and the
            // soft edge would end in a hard one.
            modifier = Modifier.blur(blur, BlurredEdgeTreatment.Unbounded)
        )
    }
}

/** How far text is blurred at the far end of a blur-replace. */
private val BLUR_RADIUS = 7.dp

/**
 * The sweep that runs across a loading placeholder.
 *
 * Returned as the [State] rather than an unwrapped Float on purpose. Reading
 * the value here would subscribe whoever called this, recomposing them sixty
 * times a second to move a gradient; handing back the State lets the caller
 * read it inside `drawBehind`, where the read subscribes only the draw phase.
 * Same animation, no recomposition.
 *
 * A 0..1 position rather than a colour, so each caller decides how wide the
 * highlight is and which way it travels.
 */
@Composable
fun rememberShimmerProgress(): State<Float> =
    rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep"
    )
