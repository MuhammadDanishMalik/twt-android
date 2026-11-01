package com.talkswithtanha.twt.core.images

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * A picked screenshot made ready to upload: at most 1280px on the long edge
 * and WebP at 72 — every figure on a receipt stays legible, and a phone
 * screenshot that was 2-3 MB goes up as roughly 100-200 KB. Null when the
 * image cannot be read.
 */
suspend fun prepareScreenshot(context: Context, uri: Uri): Pair<Bitmap, ByteArray>? = withContext(Dispatchers.IO) {
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
        val bitmap = context.contentResolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return@runCatching null
        val scale = MAX_EDGE.toFloat() / maxOf(bitmap.width, bitmap.height)
        val sized = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
        sized to compactBytes(sized)
    }.getOrNull()
}

private const val MAX_EDGE = 1280

/** WebP where the lossy encoder exists (API 30+), JPEG 75 before it. */
fun compactBytes(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().use { out ->
    if (android.os.Build.VERSION.SDK_INT >= 30) {
        bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 72, out)
    } else {
        bitmap.compress(Bitmap.CompressFormat.JPEG, 75, out)
    }
    out.toByteArray()
}
