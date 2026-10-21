package com.talkswithtanha.twt.core.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Quotes and positioning from the website's Markets page, so the home screen
 * shows the same numbers a member sees on tradewithtanha.io.
 *
 * Both endpoints are cached on the server (prices a minute, positioning an
 * hour), so asking often costs nothing upstream.
 */
object MarketsApi {

    private const val BASE = "https://tradewithtanha.io/userdashboard"
    const val GOLD_NEWS_URL = "$BASE/markets/news"

    data class Quote(
        val key: String,
        val label: String,
        val flag: String,
        val decimals: Int,
        val price: Double,
        val previousClose: Double,
        val high: Double,
        val low: Double,
        /** Percent since the previous close. */
        val change: Double
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private fun get(path: String): JSONObject? = runCatching {
        client.newCall(Request.Builder().url("$BASE$path").get().build()).execute().use { response ->
            if (!response.isSuccessful) null else JSONObject(response.body?.string() ?: return@use null)
        }
    }.getOrNull()

    /** All twelve instruments, in the website's order. Null if the feed is down. */
    suspend fun quotes(): List<Quote>? = withContext(Dispatchers.IO) {
        val array = get("/api/prices")?.optJSONArray("quotes") ?: return@withContext null
        (0 until array.length()).mapNotNull { i ->
            val q = array.getJSONObject(i)
            val price = q.optDouble("price")
            if (price.isNaN()) return@mapNotNull null
            Quote(
                key = q.optString("key"),
                label = q.optString("label"),
                flag = q.optString("flag"),
                decimals = q.optInt("decimals", 2),
                price = price,
                previousClose = q.optDouble("previousClose", price),
                high = q.optDouble("high", price),
                low = q.optDouble("low", price),
                change = q.optDouble("change", 0.0)
            )
        }
    }

    /**
     * What share of managed money is long gold, from the weekly CFTC report
     * the website charts. Null if the report is unavailable.
     */
    suspend fun goldLongPercent(): Int? = withContext(Dispatchers.IO) {
        val rows = get("/api/cot")?.optJSONArray("rows") ?: return@withContext null
        (0 until rows.length()).map { rows.getJSONObject(it) }
            .firstOrNull { it.optString("key") == "gold" }
            ?.optInt("longPct", -1)
            ?.takeIf { it in 0..100 }
    }
}
