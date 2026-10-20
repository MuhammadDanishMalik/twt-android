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
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer

/**
 * The feed's glyphs, drawn the way the tab bar's are.
 *
 * Material's set was here first and looked borrowed: a thin outlined heart and
 * a generic speech bubble next to the tab bar's solid, cut-out shapes read as
 * two apps sharing a screen. These are built on the same 26-unit grid as
 * [TabIcons] and from the same two ideas — a solid shape with the detail
 * **knocked out** of it, or, where a glyph has to read as *not yet done*, the
 * same silhouette drawn as a stroke.
 *
 * That pairing is what carries the feed's one piece of state: an unreacted post
 * shows the outline heart, a reacted one shows it filled. Same shape, same
 * weight, no colour needed to tell them apart.
 */
object FeedIcons {

    private const val GRID = 26f

    /** The knocked-out detail's line weight, matching the tab bar exactly. */
    private const val CUT = 2.3f

    /** Outlined glyphs are drawn at the same weight, so the pair looks related. */
    private const val LINE = 2.1f

    // ── Reply ────────────────────────────────────────────────────────────
    //
    // The tab bar's bubble without its smile, outlined rather than solid: a
    // reply is an invitation, and a filled shape here would shout next to the
    // heart beside it.

    @Composable
    fun Reply(modifier: Modifier = Modifier, tint: Color) = Outline(modifier, tint) {
        val bubble = Path().apply { addOval(Rect(Offset(3f, 2.5f), Size(20f, 18f))) }
        val tail = Path().apply {
            moveTo(6.4f, 16f)
            lineTo(4.6f, 23f)
            lineTo(11.6f, 19.6f)
            close()
        }
        Path().apply { op(bubble, tail, PathOperation.Union) }
    }

    // ── Heart ────────────────────────────────────────────────────────────

    @Composable
    fun Heart(modifier: Modifier = Modifier, tint: Color, filled: Boolean) {
        if (filled) Solid(modifier, tint) { heartPath() } else Outline(modifier, tint) { heartPath() }
    }

    /**
     * Two circles and a point, which is how a heart is actually constructed —
     * the cubic-curve version drifts off-centre at this size.
     */
    private fun heartPath(): Path {
        val left = Path().apply { addOval(Rect(Offset(3.2f, 4.2f), Size(10.4f, 10.4f))) }
        val right = Path().apply { addOval(Rect(Offset(12.4f, 4.2f), Size(10.4f, 10.4f))) }
        val body = Path().apply {
            moveTo(3.6f, 11.4f)
            lineTo(13f, 22.6f)
            lineTo(22.4f, 11.4f)
            close()
        }
        return Path().apply {
            op(left, right, PathOperation.Union)
            op(this, body, PathOperation.Union)
        }
    }

    // ── Compose ──────────────────────────────────────────────────────────
    //
    // A solid disc with a cross cut out of it, so the button that writes a post
    // is built from the same idea as the tabs it sits above.

    @Composable
    fun Compose(modifier: Modifier = Modifier, tint: Color) = Glyph(modifier, tint) {
        val disc = Path().apply { addOval(Rect(Offset(1f, 1f), Size(24f, 24f))) }
        val cross = Path().apply {
            moveTo(13f, 7.4f)
            lineTo(13f, 18.6f)
            moveTo(7.4f, 13f)
            lineTo(18.6f, 13f)
        }
        disc to cross
    }

    // ── Play ─────────────────────────────────────────────────────────────
    //
    // A disc with the triangle knocked out rather than drawn on top: over a
    // video thumbnail the hole shows the frame beneath, which is what makes the
    // badge read as sitting on the picture.

    @Composable
    fun Play(modifier: Modifier = Modifier, tint: Color) {
        Canvas(modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            val scale = size.minDimension / GRID
            val disc = Path().apply { addOval(Rect(Offset(1f, 1f), Size(24f, 24f))) }
            val triangle = Path().apply {
                moveTo(10.4f, 8f)
                lineTo(19f, 13f)
                lineTo(10.4f, 18f)
                close()
            }
            scaled(disc, scale)
            scaled(triangle, scale)
            drawPath(disc, tint)
            drawPath(triangle, Color.Transparent, blendMode = BlendMode.Clear)
        }
    }

    // ── Chrome ───────────────────────────────────────────────────────────

    @Composable
    fun Back(modifier: Modifier = Modifier, tint: Color) = Outline(modifier, tint) {
        Path().apply {
            moveTo(16.4f, 5.4f)
            lineTo(8.6f, 13f)
            lineTo(16.4f, 20.6f)
        }
    }

    @Composable
    fun Close(modifier: Modifier = Modifier, tint: Color) = Outline(modifier, tint) {
        Path().apply {
            moveTo(7.4f, 7.4f)
            lineTo(18.6f, 18.6f)
            moveTo(18.6f, 7.4f)
            lineTo(7.4f, 18.6f)
        }
    }

    /** The reply bar's send button: an arrow, pointing the way the reply goes. */
    @Composable
    fun Send(modifier: Modifier = Modifier, tint: Color) = Outline(modifier, tint) {
        Path().apply {
            moveTo(13f, 20.6f)
            lineTo(13f, 5.8f)
            moveTo(6.6f, 12.2f)
            lineTo(13f, 5.6f)
            lineTo(19.4f, 12.2f)
        }
    }

    // ── Save ─────────────────────────────────────────────────────────────
    //
    // X puts a bookmark here. A member saving Tanha's analysis to read again
    // before the next session wants the same thing, so the glyph is the same
    // idea: outlined until they save it, solid once they have.

    @Composable
    fun Save(modifier: Modifier = Modifier, tint: Color, filled: Boolean) {
        if (filled) Solid(modifier, tint) { bookmarkPath() } else Outline(modifier, tint) { bookmarkPath() }
    }

    private fun bookmarkPath(): Path = Path().apply {
        moveTo(7f, 3.4f)
        lineTo(19f, 3.4f)
        lineTo(19f, 22.6f)
        lineTo(13f, 17.4f)
        lineTo(7f, 22.6f)
        close()
    }

    /**
     * The owner's mark.
     *
     * A disc with a tick knocked out of it, like the tab glyphs — not Material's
     * filled check, which sits on the shape rather than being cut from it.
     */
    @Composable
    fun Verified(modifier: Modifier = Modifier, tint: Color) = Glyph(modifier, tint) {
        val disc = Path().apply { addOval(Rect(Offset(2f, 2f), Size(22f, 22f))) }
        val tick = Path().apply {
            moveTo(8f, 13.4f)
            lineTo(11.6f, 17f)
            lineTo(18.4f, 9.6f)
        }
        disc to tick
    }

    /** Three dots, for the menu on a post. */
    @Composable
    fun More(modifier: Modifier = Modifier, tint: Color) {
        Canvas(modifier) {
            val scale = size.minDimension / GRID
            val radius = 1.9f * scale
            listOf(5.6f, 13f, 20.4f).forEach { x ->
                drawCircle(tint, radius, Offset(x * scale, 13f * scale))
            }
        }
    }

    // ── Machinery ────────────────────────────────────────────────────────

    /** Solid shape, detail cleared out of it. The tab bar's own construction. */
    @Composable
    private fun Glyph(modifier: Modifier, tint: Color, build: () -> Pair<Path, Path>) {
        Canvas(modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            val (shape, detail) = build()
            val scale = size.minDimension / GRID
            scaled(shape, scale)
            scaled(detail, scale)

            drawPath(shape, tint)
            drawPath(
                path = detail,
                color = Color.Transparent,
                style = Stroke(CUT * scale, cap = StrokeCap.Round, join = StrokeJoin.Round),
                blendMode = BlendMode.Clear
            )
        }
    }

    @Composable
    private fun Solid(modifier: Modifier, tint: Color, build: () -> Path) {
        Canvas(modifier) {
            val path = build()
            scaled(path, size.minDimension / GRID)
            drawPath(path, tint)
        }
    }

    @Composable
    private fun Outline(modifier: Modifier, tint: Color, build: () -> Path) {
        Canvas(modifier) {
            val scale = size.minDimension / GRID
            val path = build()
            scaled(path, scale)
            drawPath(
                path = path,
                color = tint,
                style = Stroke(LINE * scale, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }

    private fun DrawScope.scaled(path: Path, scale: Float) {
        path.transform(Matrix().apply { scale(scale, scale) })
    }
}
