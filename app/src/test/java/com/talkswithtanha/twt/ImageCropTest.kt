package com.talkswithtanha.twt

import com.talkswithtanha.twt.core.images.Cloudinary
import com.talkswithtanha.twt.core.images.cropRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The crop arithmetic, which the member never gets to correct.
 *
 * If this is wrong the uploaded square is not the one they framed, and there is
 * nothing on screen to tell them so — the crop screen showed the right thing.
 */
class ImageCropTest {

    @Test
    fun `untouched landscape crops the centre square`() {
        val rect = cropRect(
            sourceWidth = 4000,
            sourceHeight = 3000,
            windowPx = 900f,
            zoom = 1f,
            offsetX = 0f,
            offsetY = 0f
        )
        // Covering a square means the short edge fills it, so the crop is the
        // full height and the horizontal remainder is split evenly.
        assertEquals(3000, rect.side)
        assertEquals(500, rect.left)
        assertEquals(0, rect.top)
    }

    @Test
    fun `untouched portrait crops the centre square`() {
        val rect = cropRect(3000, 4000, 900f, 1f, 0f, 0f)
        assertEquals(3000, rect.side)
        assertEquals(0, rect.left)
        assertEquals(500, rect.top)
    }

    @Test
    fun `a square source untouched is taken whole`() {
        val rect = cropRect(1200, 1200, 900f, 1f, 0f, 0f)
        assertEquals(0, rect.left)
        assertEquals(0, rect.top)
        assertEquals(1200, rect.side)
    }

    @Test
    fun `zooming in halves the square it takes`() {
        val wide = cropRect(2000, 2000, 1000f, 1f, 0f, 0f)
        val close = cropRect(2000, 2000, 1000f, 2f, 0f, 0f)
        assertEquals(2000, wide.side)
        assertEquals(1000, close.side)
        // Still centred: the same amount is trimmed from each side.
        assertEquals(500, close.left)
        assertEquals(500, close.top)
    }

    @Test
    fun `dragging right takes pixels from further left`() {
        val centred = cropRect(2000, 2000, 1000f, 2f, 0f, 0f)
        val dragged = cropRect(2000, 2000, 1000f, 2f, offsetX = 100f, offsetY = 0f)
        // Moving the photo right under a fixed window reveals what was to its
        // left, so the crop origin moves left by the offset in source pixels.
        assertTrue("expected the crop to move left", dragged.left < centred.left)
        assertEquals(centred.left - 100, dragged.left)
        assertEquals(centred.top, dragged.top)
    }

    @Test
    fun `an offset far past the edge still yields a rect inside the image`() {
        // The crop screen clamps offsets, but a rounding error must not be able
        // to hand Bitmap.createBitmap something out of bounds.
        val rect = cropRect(1500, 1000, 800f, 1f, offsetX = 99_999f, offsetY = -99_999f)
        assertTrue(rect.left >= 0)
        assertTrue(rect.top >= 0)
        assertTrue(rect.side >= 1)
        assertTrue(rect.left + rect.side <= 1500)
        assertTrue(rect.top + rect.side <= 1000)
    }

    @Test
    fun `zooming past the source never asks for more pixels than exist`() {
        val rect = cropRect(600, 400, 1200f, 1f, 0f, 0f)
        assertTrue(rect.side <= 400)
        assertTrue(rect.left + rect.side <= 600)
        assertTrue(rect.top + rect.side <= 400)
    }
}

/** The URL rewriting, which decides whether an image renders at all. */
class CloudinaryUrlTest {

    private val delivery =
        "https://res.cloudinary.com/zqncgxxg/image/upload/v1727000000/twt/avatars/abc.jpg"

    @Test
    fun `transformations are injected after the upload marker`() {
        val result = Cloudinary.avatar(delivery, 96)
        assertTrue(
            "expected transformations before the version, got $result",
            result!!.startsWith(
                "https://res.cloudinary.com/zqncgxxg/image/upload/c_fill,g_face,w_96,h_96,"
            )
        )
        assertTrue(result.endsWith("/v1727000000/twt/avatars/abc.jpg"))
    }

    @Test
    fun `a google sign-in photo is left exactly as it is`() {
        // Accounts created with Google arrive with a googleusercontent URL, and
        // rewriting one would break every avatar on those accounts.
        val google = "https://lh3.googleusercontent.com/a/ACg8ocK=s96-c"
        assertEquals(google, Cloudinary.avatar(google, 96))
    }

    @Test
    fun `null and blank stay null and blank`() {
        assertNull(Cloudinary.avatar(null, 96))
        assertEquals("", Cloudinary.avatar("", 96))
    }

    @Test
    fun `passing a url through twice does not stack transformations`() {
        val once = Cloudinary.avatar(delivery, 96)!!
        val twice = Cloudinary.avatar(once, 96)!!
        assertEquals(once, twice)
    }

    @Test
    fun `a non-cloudinary url containing the upload marker is untouched`() {
        val other = "https://example.com/image/upload/photo.jpg"
        assertEquals(other, Cloudinary.wide(other, 800))
    }
}
