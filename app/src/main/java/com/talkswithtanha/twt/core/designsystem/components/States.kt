package com.talkswithtanha.twt.core.designsystem.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import com.talkswithtanha.twt.core.designsystem.Motion
import com.talkswithtanha.twt.core.designsystem.rememberShimmerProgress
import com.talkswithtanha.twt.core.designsystem.Radius
import com.talkswithtanha.twt.core.designsystem.Spacing
import com.talkswithtanha.twt.core.designsystem.TwtColors

/**
 * A shimmering block, for the shape of content that has not arrived.
 *
 * Skeletons rather than a spinner because the layout is known before the data
 * is: showing the geometry the real content will occupy means nothing jumps when
 * it lands.
 */
/**
 * A loading placeholder with a highlight sweeping across it.
 *
 * A sweep rather than a pulse. A block that fades in and out reads as something
 * blinking at you; a highlight travelling left to right reads as the direction
 * text is about to arrive from, which is why every platform settled on it.
 */
@Composable
fun Shimmer(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    cornerRadius: Dp = 8.dp
) {
    val progress = rememberShimmerProgress()

    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            // drawBehind, not drawWithCache. The cache variant exists to keep
            // expensive objects across frames, and this gradient changes every
            // frame by definition — caching it meant rebuilding the whole
            // modifier on each recomposition just to invalidate the cache.
            // Reading progress here instead subscribes only the draw phase.
            .drawBehind {
                val (start, end) = shimmerBand(size.width, progress.value)
                drawRect(TwtColors.SurfaceElevated)
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(Color.Transparent, HighlightColor, Color.Transparent),
                        start = Offset(start, 0f),
                        end = Offset(end, 0f)
                    )
                )
            }
    )
}

/**
 * Where the highlight sits at [progress], in pixels along a bar of [width].
 *
 * Pure, and tested, because the whole effect is this arithmetic: the band has
 * to start fully off the left edge and finish fully off the right one. Get the
 * span wrong and the highlight is born in the middle of the bar and dies there,
 * which does not read as travelling — it reads as a block that pulses.
 */
internal fun shimmerBand(width: Float, progress: Float): Pair<Float, Float> {
    val half = width * BAND_HALF_WIDTH
    // Travels from a full band-width left of the bar to a full band-width past
    // its right edge.
    val centre = -half + progress * (width + 2f * half)
    return (centre - half) to (centre + half)
}

/** Half the highlight's width, as a fraction of the bar. */
private const val BAND_HALF_WIDTH = 0.35f

/**
 * The brightness of the travelling band.
 *
 * At 6% it was technically animating and effectively invisible — on a #1C1C1E
 * bar that is a couple of levels of grey, which reads as a static block rather
 * than as something loading.
 */
private val HighlightColor = Color.White.copy(alpha = 0.16f)

/**
 * A line of text that is a shimmering bar until it has something to say.
 *
 * iOS's `.redacted(reason: .placeholder)`, and the same bargain: the placeholder
 * occupies the shape the sentence will occupy, so the line lands in place
 * instead of pushing the rest of the screen down when it arrives. Pass null for
 * [text] while loading; [placeholderWidth] should be roughly the width the real
 * text will take.
 *
 * The arrival is a blur-replace rather than a cut, so the eye follows one thing
 * resolving rather than noticing two things swapped.
 */
@Composable
fun RedactedText(
    text: String?,
    placeholderWidth: Dp,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE
) {
    // The placeholder has to occupy the text's whole line box, not just the bar
    // it draws. Sizing it to the glyph height alone leaves the row a few points
    // short, so everything around it shifts when the real text lands — which is
    // the exact jump a placeholder exists to prevent.
    val density = LocalDensity.current
    val lineBox = with(density) {
        if (style.lineHeight.isSpecified) style.lineHeight.toDp()
        else style.fontSize.toDp() * DEFAULT_LINE_HEIGHT_RATIO
    }
    val barHeight = with(density) { style.fontSize.toDp() * 0.72f }

    AnimatedContent(
        targetState = text,
        transitionSpec = {
            fadeIn(Motion.gentle())
                .togetherWith(fadeOut(Motion.quick()))
                .using(SizeTransform(clip = false))
        },
        modifier = modifier,
        label = "redacted"
    ) { shown ->
        if (shown == null) {
            Box(
                modifier = Modifier.height(lineBox),
                contentAlignment = Alignment.Center
            ) {
                Shimmer(
                    modifier = Modifier.width(placeholderWidth),
                    // The bar is shorter than the line it stands in for: a
                    // full-height block reads as a filled field, not as text.
                    height = barHeight,
                    cornerRadius = barHeight / 2.5f
                )
            }
        } else {
            val blur by transition.animateDp(
                transitionSpec = { Motion.gentle() },
                label = "blur"
            ) { state -> if (state == EnterExitState.Visible) 0.dp else 7.dp }

            Text(
                text = shown,
                style = style,
                color = color,
                maxLines = maxLines,
                modifier = Modifier.blur(blur, BlurredEdgeTreatment.Unbounded)
            )
        }
    }
}

/**
 * Compose's default line height when a style does not set one: roughly 1.35x the
 * font size for the Latin script this app ships in.
 */
private const val DEFAULT_LINE_HEIGHT_RATIO = 1.35f

/** The skeleton for one signal card, sized so nothing reflows when data lands. */
@Composable
fun SignalCardSkeleton(modifier: Modifier = Modifier) {
    TwtCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Shimmer(Modifier.width(110.dp), height = 20.dp)
            Shimmer(Modifier.width(64.dp), height = 20.dp)
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(Spacing.lg))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
            repeat(3) { Shimmer(Modifier.width(64.dp), height = 32.dp) }
        }
    }
}

/**
 * Nothing here, and that is fine.
 *
 * Distinct from [ErrorState] on purpose: "no signals yet" and "we could not
 * reach the server" call for different words and different buttons, and
 * collapsing them makes a working app look broken.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(Radius.chip))
                .background(TwtColors.SurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = TwtColors.TextTertiary)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = TwtColors.TextPrimary,
            textAlign = TextAlign.Center
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = TwtColors.TextSecondary,
            textAlign = TextAlign.Center
        )
        action?.invoke()
    }
}

/** Something failed, and there is a way to try again. */
@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Icon(
            Icons.Outlined.CloudOff,
            contentDescription = null,
            tint = TwtColors.TextTertiary,
            modifier = Modifier.size(40.dp)
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = TwtColors.TextSecondary,
            textAlign = TextAlign.Center
        )
        onRetry?.let {
            TwtSecondaryButton(text = "Try again", onClick = it)
        }
    }
}
