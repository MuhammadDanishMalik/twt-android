package com.talkswithtanha.twt.core.designsystem.brand

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path

/**
 * The crown on the VIP capsule.
 *
 * Drawn rather than borrowed, because Material has no crown — the nearest
 * candidates are a rosette and a trophy, and both change what the badge says.
 * iOS uses SF Symbols' `crown.fill`, so this traces the same silhouette: three
 * points, dipped between them, on a band.
 *
 * Geometry is on a 24-unit grid, the way the tab glyphs are, so it scales to
 * whatever size the caller asks for.
 */
@Composable
fun CrownGlyph(modifier: Modifier = Modifier, tint: Color) {
    Canvas(modifier) {
        val unit = size.minDimension / GRID
        fun x(value: Float) = value * unit + (size.width - GRID * unit) / 2f
        fun y(value: Float) = value * unit + (size.height - GRID * unit) / 2f

        val crown = Path().apply {
            // Left point, down into the first dip, up to the centre point, down
            // again, up to the right point, then straight across the band.
            moveTo(x(1.5f), y(6f))
            lineTo(x(6.5f), y(12.5f))
            lineTo(x(12f), y(4.5f))
            lineTo(x(17.5f), y(12.5f))
            lineTo(x(22.5f), y(6f))
            lineTo(x(20.5f), y(18f))
            lineTo(x(3.5f), y(18f))
            close()
        }
        drawPath(crown, tint)
    }
}

private const val GRID = 24f
