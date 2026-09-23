package com.talkswithtanha.twt.core.images

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Where an upload got to. */
sealed interface UploadResult {
    /** [url] is the `secure_url`, ready to store and render. */
    data class Success(val url: String, val publicId: String) : UploadResult
    data class Failed(val message: String) : UploadResult
}

/**
 * Posts an image to Cloudinary against an unsigned preset.
 *
 * Unsigned, so there is no signature to compute and no secret to hold — see the
 * note in [Cloudinary]. The preset named in the request is what constrains the
 * upload, and it is enforced by Cloudinary rather than here.
 */
@Singleton
class CloudinaryUploader @Inject constructor() {

    private val client = OkHttpClient()

    /**
     * Uploads [bitmap] as a JPEG and returns its delivery URL.
     *
     * JPEG at 90 rather than PNG: an avatar is a photograph, and the PNG of a
     * photograph is several times the size for no visible gain. Cloudinary
     * re-encodes on delivery anyway — `f_auto` will hand out AVIF or WebP — so
     * this only decides what crosses the member's mobile data on the way up.
     */
    suspend fun uploadAvatar(bitmap: Bitmap): UploadResult = withContext(Dispatchers.IO) {
        val bytes = ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            stream.toByteArray()
        }

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                name = "file",
                filename = "avatar.jpg",
                body = bytes.toRequestBody("image/jpeg".toMediaType())
            )
            .addFormDataPart("upload_preset", Cloudinary.AVATAR_PRESET)
            .build()

        val request = Request.Builder()
            .url(Cloudinary.UPLOAD_ENDPOINT)
            .post(body)
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(text) }.getOrNull()

                if (!response.isSuccessful) {
                    // Cloudinary puts the useful part in error.message — "Upload
                    // preset not found" and the like, which is the difference
                    // between a setup mistake and a network one.
                    val detail = json?.optJSONObject("error")?.optString("message")
                    return@use UploadResult.Failed(
                        detail?.takeIf { it.isNotBlank() } ?: "Upload failed (${response.code})"
                    )
                }

                val url = json?.optString("secure_url").orEmpty()
                val publicId = json?.optString("public_id").orEmpty()
                if (url.isBlank()) UploadResult.Failed("Cloudinary returned no image URL")
                else UploadResult.Success(url, publicId)
            }
        }.getOrElse { error ->
            UploadResult.Failed(error.message ?: "Could not reach Cloudinary")
        }
    }
}
