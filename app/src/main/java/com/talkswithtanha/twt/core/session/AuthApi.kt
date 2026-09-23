package com.talkswithtanha.twt.core.session

import com.talkswithtanha.twt.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** What the server said. */
sealed interface ApiResult {
    data object Ok : ApiResult

    /** [message] is written to be shown to a member as-is. */
    data class Failed(val message: String, val retryAfterSeconds: Int? = null) : ApiResult
}

/**
 * The phone's half of the email code flow.
 *
 * Everything here is a call to the deployed admin app, because everything here
 * needs something the phone must not hold: the Resend key to send mail, and the
 * Firebase Admin SDK to flip `emailVerified` or set a password for somebody who
 * cannot sign in. Putting either in an APK would publish it.
 *
 * Requests that concern the signed-in member carry their Firebase ID token and
 * the server reads the address out of it rather than out of the body, so a
 * caller cannot aim a code at an address they do not own. Password reset is the
 * exception and has to take the address on trust — nobody is signed in, which
 * is the whole situation — so the server answers identically whether or not the
 * account exists.
 */
@Singleton
class AuthApi @Inject constructor() {

    private val client = OkHttpClient.Builder()
        // Short, because a member is staring at a spinner on a sign-up screen.
        // Failing in fifteen seconds with a message beats hanging for sixty.
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    /** False when no deployment URL was configured at build time. */
    val isConfigured: Boolean get() = BuildConfig.ADMIN_API_BASE.isNotBlank()

    private val base: String get() = BuildConfig.ADMIN_API_BASE.trimEnd('/')

    /** Mails a verification code to the signed-in member's own address. */
    suspend fun sendVerificationCode(idToken: String): ApiResult =
        post("/api/auth/send-code", JSONObject().put("idToken", idToken).put("purpose", "verify"))

    /** Mails a reset code. Succeeds whether or not the address has an account. */
    suspend fun sendResetCode(email: String): ApiResult =
        post("/api/auth/send-code", JSONObject().put("email", email).put("purpose", "reset"))

    /** Confirms the code, which marks the address verified server-side. */
    suspend fun verifyEmail(idToken: String, code: String): ApiResult =
        post("/api/auth/verify-code", JSONObject().put("idToken", idToken).put("code", code))

    /**
     * Sets a new password for somebody who proved they can read the inbox.
     *
     * This is the one call that carries a password to our own server rather
     * than to Firebase. It is unavoidable for a code-based reset: the member is
     * not signed in, so there is no credential for the phone to act on. See the
     * note in the route itself.
     */
    suspend fun resetPassword(email: String, code: String, password: String): ApiResult =
        post(
            "/api/auth/reset-password",
            JSONObject().put("email", email).put("code", code).put("password", password)
        )

    private suspend fun post(path: String, body: JSONObject): ApiResult =
        withContext(Dispatchers.IO) {
            if (!isConfigured) {
                return@withContext ApiResult.Failed(
                    "Email is not set up in this build. Contact support."
                )
            }

            val request = Request.Builder()
                .url(base + path)
                .post(body.toString().toRequestBody(JSON))
                .build()

            runCatching {
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    val json = runCatching { JSONObject(text) }.getOrNull()

                    if (response.isSuccessful) return@use ApiResult.Ok

                    // The server writes these for members to read, so they are
                    // passed through rather than replaced with a generic line.
                    val message = json?.optString("error")?.takeIf { it.isNotBlank() }
                        ?: "Something went wrong. Try again."
                    val retryAfter = json?.optInt("retryAfter", 0)?.takeIf { it > 0 }
                    ApiResult.Failed(message, retryAfter)
                }
            }.getOrElse {
                ApiResult.Failed("Could not reach the server. Check your connection.")
            }
        }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
