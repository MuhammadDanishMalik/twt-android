package com.talkswithtanha.twt.core.images

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A square of source pixels: left, top, and the side length. */
data class CropRect(val left: Int, val top: Int, val side: Int)

/**
 * The source-pixel square behind the crop window.
 *
 * Split out from [cropSquare] and kept free of Android types so the arithmetic
 * can be tested directly. It is the inverse of what the crop screen draws: the
 * image is scaled to cover a [windowPx] square, multiplied by [zoom], then
 * displaced by the offsets, and this undoes all three.
 *
 * Everything is clamped to the source's bounds. A rounding error of one pixel
 * at the edge would otherwise reach `Bitmap.createBitmap` as an out-of-bounds
 * rectangle and throw, on the one code path a member cannot retry differently.
 */
fun cropRect(
    sourceWidth: Int,
    sourceHeight: Int,
    windowPx: Float,
    zoom: Float,
    offsetX: Float,
    offsetY: Float
): CropRect {
    val coverScale = max(windowPx / sourceWidth, windowPx / sourceHeight)
    val scale = coverScale * zoom
    val displayedWidth = sourceWidth * scale
    val displayedHeight = sourceHeight * scale

    // Where the window's top-left sits within the displayed image, then the
    // same point expressed in the source image's own pixels.
    val leftInDisplayed = displayedWidth / 2f - windowPx / 2f - offsetX
    val topInDisplayed = displayedHeight / 2f - windowPx / 2f - offsetY

    val wanted = (windowPx / scale).roundToInt().coerceAtLeast(1)
    val side0 = min(wanted, min(sourceWidth, sourceHeight))
    val left = (leftInDisplayed / scale).roundToInt().coerceIn(0, max(0, sourceWidth - side0))
    val top = (topInDisplayed / scale).roundToInt().coerceIn(0, max(0, sourceHeight - side0))
    val side = min(side0, min(sourceWidth - left, sourceHeight - top))

    return CropRect(left, top, side)
}

/** The longest edge the app decodes a picked photo to before cropping. */
private const val WORKING_EDGE = 2048

/** The side of the square the app uploads. */
const val AVATAR_OUTPUT_EDGE = 1024

/**
 * Decodes a picked image to something an avatar crop can work with.
 *
 * Downsampled on the way in. A recent phone camera produces images upwards of
 * fifty megapixels, and decoding one of those at full size to crop a small
 * circle out of it is how an image picker ends up killed for running out of
 * memory. [WORKING_EDGE] is comfortably more than the crop needs and small
 * enough to always fit.
 */
suspend fun decodeForCrop(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, WORKING_EDGE)
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return@runCatching null

        // Phones record orientation in EXIF rather than rotating the pixels, so
        // a photo taken in portrait arrives on its side unless this is applied.
        applyExifRotation(context, uri, decoded)
    }.getOrNull()
}

/** The largest power of two that keeps the longest edge at or above [target]. */
private fun sampleSizeFor(width: Int, height: Int, target: Int): Int {
    var sample = 1
    while (max(width, height) / (sample * 2) >= target) sample *= 2
    return sample
}

private fun applyExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
    val orientation = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }
    }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        else -> return bitmap
    }
    return runCatching {
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }.getOrDefault(bitmap)
}

/**
 * Cuts the square the member framed out of [source].
 *
 * The screen shows the image scaled to *cover* a square window, which the
 * member then pans and pinches. [zoom] and the offsets describe where they left
 * it, in the same units the crop screen uses: [zoom] multiplies the cover
 * scale, and the offsets are the window-to-image displacement in screen pixels.
 *
 * Running the same arithmetic backwards gives the rectangle of original pixels
 * behind that window.
 */
suspend fun cropSquare(
    source: Bitmap,
    windowPx: Float,
    zoom: Float,
    offsetX: Float,
    offsetY: Float,
    outputEdge: Int = AVATAR_OUTPUT_EDGE
): Bitmap = withContext(Dispatchers.Default) {
    val (left, top, side) = cropRect(
        sourceWidth = source.width,
        sourceHeight = source.height,
        windowPx = windowPx,
        zoom = zoom,
        offsetX = offsetX,
        offsetY = offsetY
    )

    val cropped = Bitmap.createBitmap(source, left, top, side, side)
    // Only ever downscale. Blowing a small crop up to the output size would
    // upload a soft image that looks worse than the one the member picked.
    if (side <= outputEdge) {
        cropped
    } else {
        Bitmap.createScaledBitmap(cropped, outputEdge, outputEdge, true).also {
            if (it != cropped) cropped.recycle()
        }
    }
}
