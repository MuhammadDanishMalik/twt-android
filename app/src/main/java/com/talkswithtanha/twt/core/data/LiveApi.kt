package com.talkswithtanha.twt.core.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Live candles and the price right now, from the website — the same feed the
 * Live analysis page draws, so a level sits on the same candle on the phone
 * and on tradewithtanha.io.
 *
 * The website chooses the sources (spot gold from PAXG aligned to Swissquote,
 * forex from Yahoo) and caches them, so the app never talks to them itself.
 */
object LiveApi {

    private const val BASE = "https://tradewithtanha.io/userdashboard/api/live"

    data class Instrument(val key: String, val pair: String, val label: String, val decimals: Int)

    /** Mirrors `LIVE_INSTRUMENTS` in the website's lib/live/shared.ts. */
    val INSTRUMENTS = listOf(
        Instrument("XAUUSD", "XAU/USD", "Gold", 2),
        Instrument("XAGUSD", "XAG/USD", "Silver", 3),
        Instrument("EURUSD", "EUR/USD", "Euro", 5),
        Instrument("GBPUSD", "GBP/USD", "Cable", 5),
        Instrument("USDJPY", "USD/JPY", "Yen", 3),
        Instrument("AUDUSD", "AUD/USD", "Aussie", 5),
        Instrument("NZDUSD", "NZD/USD", "Kiwi", 5),
        Instrument("USDCAD", "USD/CAD", "Loonie", 5),
        Instrument("USDCHF", "USD/CHF", "Swissie", 5),
        Instrument("EURJPY", "EUR/JPY", "Euro yen", 3),
        Instrument("GBPJPY", "GBP/JPY", "Pound yen", 3),
        Instrument("EURGBP", "EUR/GBP", "Euro pound", 5),
        Instrument("BTCUSD", "BTC/USD", "Bitcoin", 2),
    )

    /** "XAU/USD", "xauusd" → the instrument, or null when there is no live feed for it. */
    fun find(pairOrKey: String): Instrument? {
        val key = pairOrKey.uppercase().filter { it in 'A'..'Z' }
        return INSTRUMENTS.firstOrNull { it.key == key }
    }

    enum class Timeframe(val key: String, val label: String, val seconds: Long) {
        M1("1m", "1m", 60), M5("5m", "5m", 300), M15("15m", "15m", 900),
        H1("1h", "1H", 3600), H4("4h", "4H", 14400), D1("1d", "1D", 86400);

        companion object {
            fun of(key: String?) = entries.firstOrNull { it.key == key } ?: M15
        }
    }

    data class Bar(val t: Long, val o: Double, val h: Double, val l: Double, val c: Double)

    data class Candles(val decimals: Int, val bars: List<Bar>, val source: String)

    data class Quote(val price: Double, val bid: Double?, val ask: Double?, val ts: Long, val closed: Boolean)

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private fun get(path: String): JSONObject? = runCatching {
        client.newCall(Request.Builder().url("$BASE$path").get().build()).execute().use { response ->
            if (!response.isSuccessful) null else JSONObject(response.body?.string() ?: return@use null)
        }
    }.getOrNull()

    suspend fun candles(key: String, tf: Timeframe): Candles? = withContext(Dispatchers.IO) {
        val json = get("/candles?pair=$key&tf=${tf.key}") ?: return@withContext null
        val array = json.optJSONArray("bars") ?: return@withContext null
        val bars = (0 until array.length()).map { i ->
            val b = array.getJSONObject(i)
            Bar(b.getLong("t"), b.getDouble("o"), b.getDouble("h"), b.getDouble("l"), b.getDouble("c"))
        }
        if (bars.isEmpty()) null else Candles(json.optInt("decimals", 2), bars, json.optString("source"))
    }

    suspend fun quote(key: String): Quote? = withContext(Dispatchers.IO) {
        val q = get("/quote?pair=$key") ?: return@withContext null
        val price = q.optDouble("price")
        if (price.isNaN()) return@withContext null
        Quote(
            price = price,
            bid = q.optDouble("bid").takeUnless { it.isNaN() },
            ask = q.optDouble("ask").takeUnless { it.isNaN() },
            ts = q.optLong("ts", System.currentTimeMillis()),
            closed = q.optBoolean("closed", false)
        )
    }
}
