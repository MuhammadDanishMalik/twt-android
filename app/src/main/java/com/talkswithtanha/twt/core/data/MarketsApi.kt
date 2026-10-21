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

    // ── Price history for the chart sheet ────────────────────────────────

    /** Yahoo's symbols for our instruments — the ones the website uses. */
    private val YAHOO = mapOf(
        "gold" to "GC=F", "silver" to "SI=F", "eurusd" to "EURUSD=X", "gbpusd" to "GBPUSD=X",
        "usdjpy" to "USDJPY=X", "audusd" to "AUDUSD=X", "usdcad" to "USDCAD=X", "usdchf" to "USDCHF=X",
        "nzdusd" to "NZDUSD=X", "gbpjpy" to "GBPJPY=X", "btc" to "BTC-USD", "eth" to "ETH-USD"
    )

    /** The chart's ranges: label → Yahoo range and candle size. */
    val RANGES = listOf(
        Triple("1D", "1d", "5m"),
        Triple("1W", "5d", "30m"),
        Triple("1M", "1mo", "1d"),
        Triple("3M", "3mo", "1d"),
        Triple("1Y", "1y", "1d"),
        Triple("5Y", "5y", "1wk")
    )

    data class Candle(val time: Long, val open: Double, val high: Double, val low: Double, val close: Double)

    /** Candles for [key] over the range labelled [rangeLabel]; null if unavailable. */
    suspend fun history(key: String, rangeLabel: String): List<Candle>? = withContext(Dispatchers.IO) {
        val symbol = YAHOO[key] ?: return@withContext null
        val (_, range, interval) = RANGES.firstOrNull { it.first == rangeLabel } ?: return@withContext null
        runCatching {
            val url = "https://query1.finance.yahoo.com/v8/finance/chart/" +
                java.net.URLEncoder.encode(symbol, "UTF-8") + "?range=$range&interval=$interval"
            val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (TradeWithTanha app)").get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val result = JSONObject(response.body?.string() ?: return@use null)
                    .getJSONObject("chart").getJSONArray("result").getJSONObject(0)
                val stamps = result.optJSONArray("timestamp") ?: return@use null
                val quote = result.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0)
                val o = quote.optJSONArray("open"); val h = quote.optJSONArray("high")
                val l = quote.optJSONArray("low"); val c = quote.optJSONArray("close")
                (0 until stamps.length()).mapNotNull { i ->
                    // Holidays and gaps come back as nulls; skipping them keeps the line honest.
                    if (c == null || c.isNull(i)) return@mapNotNull null
                    val close = c.getDouble(i)
                    Candle(
                        time = stamps.getLong(i) * 1000,
                        open = o?.takeUnless { it.isNull(i) }?.getDouble(i) ?: close,
                        high = h?.takeUnless { it.isNull(i) }?.getDouble(i) ?: close,
                        low = l?.takeUnless { it.isNull(i) }?.getDouble(i) ?: close,
                        close = close
                    )
                }.takeIf { it.size >= 2 }
            }
        }.getOrNull()
    }
}
