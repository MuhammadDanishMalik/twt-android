package com.talkswithtanha.twt.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp

/**
 * Light catching the top edge of a dark card — bright towards the right,
 * fading to nothing either way — with a faint wash down the left edge and a
 * soft glow just under the top. The same edge light the website draws in
 * dark mode, so a card reads as lit from above rather than cut out of the
 * background.
 *
 * Drawn over the content, inside [shape], so it follows rounded corners and
 * never spills outside the card. The brushes are built once per size
 * (drawWithCache), not every frame.
 *
 * Put it straight after the card's border in the modifier chain.
 */
fun Modifier.edgeShine(shape: Shape = RoundedCornerShape(24.dp), strength: Float = 1f): Modifier =
    drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val clip = Path().apply { addOutline(outline) }
        val line = 1.dp.toPx()
        val top = Brush.horizontalGradient(
            0.06f to Color.Transparent,
            0.28f to Color.White.copy(alpha = 0.10f * strength),
            0.66f to Color.White.copy(alpha = 0.62f * strength),
            0.88f to Color.White.copy(alpha = 0.12f * strength),
            0.97f to Color.Transparent
        )
        val left = Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.20f * strength),
            0.55f to Color.Transparent,
            endY = size.height
        )
        val glowRadius = size.width * 0.55f
        val glow = Brush.radialGradient(
            0f to Color.White.copy(alpha = 0.07f * strength),
            1f to Color.Transparent,
            center = Offset(size.width * 0.66f, 0f),
            radius = glowRadius.coerceAtLeast(1f)
        )
        onDrawWithContent {
            drawContent()
            clipPath(clip) {
                drawRect(glow, topLeft = Offset(0f, 0f), size = Size(size.width, minOf(size.height, 46.dp.toPx())))
                drawRect(top, topLeft = Offset(0f, 0f), size = Size(size.width, line))
                drawRect(left, topLeft = Offset(0f, 0f), size = Size(line, size.height))
            }
        }
    }

