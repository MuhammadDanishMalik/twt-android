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
        Triple("6M", "6mo", "1d"),
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

    // ── News, calendar, the Fed and positioning — the Markets and News tabs ──

    data class Story(
        val title: String,
        val summary: String,
        val source: String,
        val link: String,
        val at: java.util.Date?,
        /** What it is about: "USD", "XAU", "OIL", "EUR"… */
        val tags: List<String>
    )

    data class Event(
        val title: String,
        val currency: String,
        /** "High", "Medium", "Low" or "Holiday". */
        val impact: String,
        val at: java.util.Date?,
        val forecast: String,
        val previous: String
    )

    data class FedMeeting(val date: java.util.Date?, val likeliest: String, val outcomes: List<Pair<String, Double>>)
    data class Fed(val targetFrom: Double, val targetTo: Double, val effective: Double, val meetings: List<FedMeeting>)

    data class Positioning(
        val key: String,
        val label: String,
        val flag: String,
        val group: String,
        val longPercent: Int,
        val net: Long,
        val change: Long,
        /** Where this week's net sits in its three-year range, 0–100; null when there is too little history. */
        val index: Int?,
        val openInterest: Long,
        val openInterestChange: Long,
        /** Weekly, oldest first, about five years. */
        val history: List<CotWeek>,
        /** This week by trader group: managed money, producers, swap dealers… */
        val groups: List<CotGroup>
    )

    data class CotWeek(val date: String, val net: Long, val commercialNet: Long, val openInterest: Long)
    data class CotGroup(val name: String, val long: Long, val short: Long, val net: Long, val change: Long)

    private fun isoDate(raw: String?): java.util.Date? = raw?.takeIf { it.isNotBlank() }?.let {
        runCatching { java.time.OffsetDateTime.parse(it).toInstant().let { i -> java.util.Date.from(i) } }
            .recoverCatching { java.util.Date.from(java.time.Instant.parse(raw)) }
            .getOrNull()
    }

    private fun strings(a: org.json.JSONArray?): List<String> =
        (0 until (a?.length() ?: 0)).map { a!!.getString(it) }

    suspend fun news(): List<Story>? = withContext(Dispatchers.IO) {
        val array = get("/api/news")?.optJSONArray("stories") ?: return@withContext null
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Story(
                title = o.optString("title"),
                summary = o.optString("summary"),
                source = o.optString("source"),
                link = o.optString("link"),
                at = isoDate(o.optString("at")),
                tags = strings(o.optJSONArray("tags"))
            )
        }.filter { it.title.isNotBlank() && it.link.startsWith("http") }
    }

    suspend fun calendar(): List<Event>? = withContext(Dispatchers.IO) {
        val array = get("/api/calendar")?.optJSONArray("events") ?: return@withContext null
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Event(
                title = o.optString("title"),
                currency = o.optString("currency"),
                impact = o.optString("impact"),
                at = isoDate(o.optString("at")),
                forecast = o.optString("forecast"),
                previous = o.optString("previous")
            )
        }
    }

    suspend fun fed(): Fed? = withContext(Dispatchers.IO) {
        val o = get("/api/fed") ?: return@withContext null
        val meetings = o.optJSONArray("meetings")
        Fed(
            targetFrom = o.optDouble("targetFrom"),
            targetTo = o.optDouble("targetTo"),
            effective = o.optDouble("effective"),
            meetings = (0 until (meetings?.length() ?: 0)).map { i ->
                val m = meetings!!.getJSONObject(i)
                val outs = m.optJSONArray("outcomes")
                FedMeeting(
                    date = isoDate(m.optString("date")),
                    likeliest = m.optString("likeliest"),
                    outcomes = (0 until (outs?.length() ?: 0)).map { j ->
                        val x = outs!!.getJSONObject(j)
                        x.optString("label") to x.optDouble("probability")
                    }
                )
            }
        )
    }

    suspend fun positioning(): Pair<String, List<Positioning>>? = withContext(Dispatchers.IO) {
        val o = get("/api/cot") ?: return@withContext null
        val rows = o.optJSONArray("rows") ?: return@withContext null
        o.optString("asOf") to (0 until rows.length()).map { i ->
            val r = rows.getJSONObject(i)
            Positioning(
                key = r.optString("key"),
                label = r.optString("label"),
                flag = r.optString("flag"),
                group = r.optString("group"),
                longPercent = r.optInt("longPct"),
                net = r.optLong("net"),
                change = r.optLong("change"),
                index = if (r.isNull("index")) null else r.optInt("index"),
                openInterest = r.optLong("openInterest"),
                openInterestChange = r.optLong("openInterestChange"),
                history = r.optJSONArray("history").let { a ->
                    (0 until (a?.length() ?: 0)).map { j ->
                        val w = a!!.getJSONObject(j)
                        CotWeek(w.optString("date"), w.optLong("net"), w.optLong("commercialNet"), w.optLong("openInterest"))
                    }
                },
                groups = r.optJSONArray("groups").let { a ->
                    (0 until (a?.length() ?: 0)).map { j ->
                        val g = a!!.getJSONObject(j)
                        CotGroup(g.optString("group"), g.optLong("long"), g.optLong("short"), g.optLong("net"), g.optLong("change"))
                    }
                }
            )
        }
    }
}
