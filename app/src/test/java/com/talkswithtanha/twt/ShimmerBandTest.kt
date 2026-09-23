package com.talkswithtanha.twt

import com.talkswithtanha.twt.core.designsystem.components.shimmerBand
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shimmer's travel.
 *
 * A loading placeholder that does not visibly move is indistinguishable from a
 * screen that has hung, which is the one impression it exists to prevent.
 */
class ShimmerBandTest {

    private val width = 300f

    @Test
    fun `starts completely off the left edge`() {
        val (start, end) = shimmerBand(width, 0f)
        assertTrue("band should begin left of the bar, was $start", start < 0f)
        assertTrue("band should not yet touch the bar, ended at $end", end <= 0.001f)
    }

    @Test
    fun `finishes completely off the right edge`() {
        val (start, end) = shimmerBand(width, 1f)
        assertTrue("band should have left the bar, started at $start", start >= width - 0.001f)
        assertTrue("band should end past the bar, was $end", end > width)
    }

    @Test
    fun `crosses the centre halfway through`() {
        val (start, end) = shimmerBand(width, 0.5f)
        val centre = (start + end) / 2f
        assertTrue("expected the band centred on the bar, was $centre", kotlin.math.abs(centre - width / 2f) < 0.001f)
    }

    @Test
    fun `sweeps strictly left to right`() {
        var previous = Float.NEGATIVE_INFINITY
        for (step in 0..20) {
            val (start, _) = shimmerBand(width, step / 20f)
            assertTrue("band went backwards at step $step", start > previous)
            previous = start
        }
    }

    @Test
    fun `every point on the bar is lit at some moment`() {
        // Otherwise a column never brightens and that part of the bar looks dead.
        for (x in 0..width.toInt()) {
            val lit = (0..100).any { step ->
                val (start, end) = shimmerBand(width, step / 100f)
                x >= start && x <= end
            }
            assertTrue("x=$x is never covered by the highlight", lit)
        }
    }
}
