package com.talkswithtanha.twt.core.designsystem.brand

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.clipPath

/**
 * The TШT logo, drawn as geometry and filled with a holographic gradient.
 *
 * Ported stroke for stroke from `TWTLetterforms.swift`, which the iOS app and
 * its widget both draw from. Drawn rather than shipped as a PNG for the reason
 * that matters on a card: the colours can *move*. The gradient slides with the
 * card's tilt, so pushing the card around makes the wordmark shimmer the way
 * foil does on a real card. A bitmap can only sit there.
 *
 * Coordinates are fractions of the logo's own bounding box, measured off the
 * source artwork at 905 × 506, so the shape scales to any size without drift.
 */
object TwtLetterforms {

    const val ASPECT_RATIO = 905f / 506f

    /**
     * The three letters as one path.
     *
     * Overlapping rounded rectangles unioned under the non-zero fill rule, so
     * each letter reads as one shape rather than a bar laid on a stem. The two
     * T's are a bar and a stem; the middle letter is three stems joined along
     * the bottom, which is what gives it the Ш silhouette rather than a W.
     */
    fun path(width: Float, height: Float): Path {
        val radius = height * 0.05f

        fun stroke(x0: Float, y0: Float, x1: Float, y1: Float) = RoundRect(
            rect = Rect(x0 * width, y0 * height, x1 * width, y1 * height),
            cornerRadius = CornerRadius(radius, radius)
        )

        val strokes = listOf(
            // T
            stroke(0.000f, 0.000f, 0.284f, 0.186f),
            stroke(0.081f, 0.000f, 0.207f, 1.000f),
            // Ш — three stems, one foot
            stroke(0.300f, 0.000f, 0.409f, 1.000f),
            stroke(0.448f, 0.000f, 0.549f, 1.000f),
            stroke(0.591f, 0.000f, 0.697f, 1.000f),
            stroke(0.300f, 0.810f, 0.697f, 1.000f),
            // T
            stroke(0.726f, 0.000f, 1.000f, 0.186f),
            stroke(0.796f, 0.000f, 0.922f, 1.000f)
        )

        return Path().apply {
            fillType = PathFillType.NonZero
            strokes.forEach { addRoundRect(it) }
        }
    }
}

/**
 * Diagonal bands of warm amber and cold blue over slate — the palette of the
 * supplied artwork. [shift] slides the axis, which is what makes the bands
 * travel across the letters.
 */
fun twtHolographicBrush(size: Size, shift: Float = 0f): Brush = Brush.linearGradient(
    colorStops = arrayOf(
        0.00f to Color(0xFF212639),
        0.17f to Color(0xFF8F91B8),
        0.30f to Color(0xFFE3994F),
        0.37f to Color(0xFFF8E3C7),
        0.45f to Color(0xFF9EC2FF),
        0.55f to Color(0xFFEDF2FF),
        0.66f to Color(0xFFD98A45),
        0.76f to Color(0xFFA8C7FF),
        0.88f to Color(0xFF595E8A),
        1.00f to Color(0xFF292E4D)
    ),
    start = Offset((-0.1f + shift) * size.width, 1.05f * size.height),
    end = Offset((1.1f + shift) * size.width, -0.05f * size.height)
)

/**
 * The wordmark.
 *
 * [tiltShift] is the card's tilt, so the foil answers to how the card is being
 * held. On top of that the bands drift on their own — slowly, and only far
 * enough to catch the light, because a logo that visibly animates on a home
 * screen stops being a logo and becomes an advert.
 */
@Composable
fun TwtWordmark(
    modifier: Modifier = Modifier,
    tiltShift: Float = 0f,
    animated: Boolean = true
) {
    val drift by if (animated) {
        rememberInfiniteTransition(label = "foil").animateFloat(
            initialValue = -0.18f,
            targetValue = 0.18f,
            animationSpec = infiniteRepeatable(tween(5200), RepeatMode.Reverse),
            label = "drift"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    Canvas(modifier) {
        val letters = TwtLetterforms.path(size.width, size.height)
        clipPath(letters) {
            drawRect(brush = twtHolographicBrush(size, drift + tiltShift))
            // A soft gloss along the top edge of every stroke. The source
            // letters read as frosted and slightly domed; without this they go
            // flat, like a gradient pasted into a text box.
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.35f), Color.Transparent),
                    endY = size.height * 0.5f
                )
            )
        }
    }
}
