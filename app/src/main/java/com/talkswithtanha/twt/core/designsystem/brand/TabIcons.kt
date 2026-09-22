package com.talkswithtanha.twt.core.designsystem.brand

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer

/**
 * The three tab glyphs, drawn rather than taken from the Material set.
 *
 * A port of `TabIcons.swift`. Each is a solid shape with a detail **knocked
 * out** of it — a smile cut into the house and the speech bubble, a price line
 * cut into the disc — rather than drawn on top in a second colour. That is what
 * makes them feel cut from the bar rather than stuck onto it, and it is the
 * whole character of this app's tab bar. Material's outline icons are the wrong
 * shape and the wrong weight, which is what the generic house and squiggle
 * looked like before.
 *
 * Geometry is on a 26-unit grid, exactly as in the Swift source, so both apps
 * draw the same glyph.
 */
object TabIcons {

    private const val GRID = 26f

    /** The knocked-out detail's line weight, on the same 26-unit grid. */
    private const val CUT = 2.3f

    @Composable
    fun Home(modifier: Modifier = Modifier, tint: Color) = Glyph(modifier, tint) {
        val shape = roundedPolygon(
            listOf(
                Offset(13f, 2.4f),
                Offset(24f, 11.6f),
                Offset(24f, 24f),
                Offset(2f, 24f),
                Offset(2f, 11.6f)
            ),
            radius = 3.4f
        )
        shape to smile(Offset(13f, 15.6f), 4.3f)
    }

    @Composable
    fun Signals(modifier: Modifier = Modifier, tint: Color) = Glyph(modifier, tint) {
        val shape = Path().apply {
            addOval(Rect(Offset(2f, 2f), Size(22f, 22f)))
        }
        val line = Path().apply {
            moveTo(7.2f, 16.8f)
            lineTo(11.4f, 12.4f)
            lineTo(14.2f, 15.2f)
            lineTo(18.8f, 9.4f)
        }
        shape to line
    }

    @Composable
    fun Chat(modifier: Modifier = Modifier, tint: Color) = Glyph(modifier, tint) {
        val bubble = Path().apply {
            addOval(Rect(Offset(2.5f, 1.5f), Size(22f, 21f)))
        }
        val tail = Path().apply {
            moveTo(5.2f, 16.5f)
            lineTo(3.2f, 24.4f)
            lineTo(11.5f, 20.6f)
            close()
        }
        val shape = Path().apply { op(bubble, tail, PathOperation.Union) }
        shape to smile(Offset(13.5f, 11.2f), 4.2f)
    }

    /**
     * Fills the shape, then erases the detail out of it.
     *
     * The erase needs its own compositing layer: `BlendMode.Clear` punches a
     * hole in whatever it is drawn onto, and without an offscreen layer that is
     * the window behind the bar rather than the glyph.
     */
    @Composable
    private fun Glyph(
        modifier: Modifier,
        tint: Color,
        build: () -> Pair<Path, Path>
    ) {
        Canvas(
            modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        ) {
            val (shape, detail) = build()
            val scale = size.minDimension / GRID
            scalePath(shape, scale)
            scalePath(detail, scale)

            drawPath(shape, tint)
            drawPath(
                path = detail,
                color = Color.Transparent,
                style = Stroke(
                    width = CUT * scale,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                ),
                blendMode = BlendMode.Clear
            )
        }
    }

    private fun DrawScope.scalePath(path: Path, scale: Float) {
        path.transform(
            androidx.compose.ui.graphics.Matrix().apply { scale(scale, scale) }
        )
    }

    /** The mouth: an arc across the lower half, 25° to 155°. */
    private fun smile(centre: Offset, radius: Float): Path = Path().apply {
        arcTo(
            rect = Rect(
                centre.x - radius,
                centre.y - radius,
                centre.x + radius,
                centre.y + radius
            ),
            startAngleDegrees = 25f,
            sweepAngleDegrees = 130f,
            forceMoveTo = true
        )
    }

    /**
     * A polygon with rounded corners.
     *
     * `addArc(tangent1End:tangent2End:radius:)` has no Compose equivalent, so
     * each corner is a quadratic curve pulled back along both edges by the
     * radius — visually identical at glyph size and considerably less code than
     * solving the tangent circle.
     */
    private fun roundedPolygon(points: List<Offset>, radius: Float): Path = Path().apply {
        val count = points.size
        points.forEachIndexed { index, current ->
            val previous = points[(index - 1 + count) % count]
            val next = points[(index + 1) % count]

            val toPrevious = (previous - current).normalised() * radius
            val toNext = (next - current).normalised() * radius

            val entry = current + toPrevious
            val exit = current + toNext

            if (index == 0) moveTo(entry.x, entry.y) else lineTo(entry.x, entry.y)
            quadraticTo(current.x, current.y, exit.x, exit.y)
        }
        close()
    }

    private fun Offset.normalised(): Offset {
        val length = getDistance()
        return if (length == 0f) this else this / length
    }
}
