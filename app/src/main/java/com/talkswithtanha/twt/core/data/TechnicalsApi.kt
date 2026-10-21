package com.talkswithtanha.twt.core.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * The live technicals the website's Markets page shows — 26 indicators summed
 * into three gauges — read from the same endpoint, so the app and the site
 * never disagree about whether gold is a sell.
 *
 * The member site computes them on its server from Yahoo candles and caches
 * them for a minute; this only asks for the result.
 */
object TechnicalsApi {

    private const val BASE = "https://tradewithtanha.io/userdashboard/api/technicals"

    data class Instrument(val key: String, val label: String, val flag: String)

    /** The website's list, in its order. */
    val INSTRUMENTS = listOf(
        Instrument("gold", "Gold", "🥇"),
        Instrument("silver", "Silver", "🥈"),
        Instrument("eurusd", "EUR/USD", "🇪🇺"),
        Instrument("gbpusd", "GBP/USD", "🇬🇧"),
        Instrument("usdjpy", "USD/JPY", "🇯🇵"),
        Instrument("audusd", "AUD/USD", "🇦🇺"),
        Instrument("usdcad", "USD/CAD", "🇨🇦"),
        Instrument("usdchf", "USD/CHF", "🇨🇭"),
        Instrument("nzdusd", "NZD/USD", "🇳🇿"),
        Instrument("gbpjpy", "GBP/JPY", "🇬🇧"),
        Instrument("btc", "Bitcoin", "₿"),
        Instrument("eth", "Ether", "Ξ")
    )

    /** Request key → the label the website shows. */
    val TIMEFRAMES = listOf(
        "1m" to "1m", "5m" to "5m", "15m" to "15m", "30m" to "30m",
        "1h" to "1H", "4h" to "4H", "1d" to "1D", "1w" to "1W", "1M" to "1M"
    )

    data class Gauge(val buy: Int, val sell: Int, val neutral: Int, val score: Float, val label: String)
    data class Row(val name: String, val value: Double?, val verdict: String)

    data class Technicals(
        val price: Double,
        val oscillators: Gauge,
        val movingAverages: Gauge,
        val summary: Gauge,
        val rows: List<Row>
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Null when the network or the feed fails; the card says so and retries. */
    suspend fun load(key: String, timeframe: String): Technicals? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url("$BASE?key=$key&tf=$timeframe").get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                parse(JSONObject(response.body?.string() ?: return@use null))
            }
        }.getOrNull()
    }

    private fun gauge(o: JSONObject) = Gauge(
        buy = o.optInt("buy"),
        sell = o.optInt("sell"),
        neutral = o.optInt("neutral"),
        score = o.optDouble("score", 0.0).toFloat(),
        label = o.optString("label", "Neutral")
    )

    private fun rows(o: JSONObject): List<Row> {
        val array = o.optJSONArray("rows") ?: return emptyList()
        return (0 until array.length()).map { i ->
            val r = array.getJSONObject(i)
            Row(
                name = r.optString("name"),
                value = if (r.isNull("value")) null else r.optDouble("value"),
                verdict = r.optString("verdict", "neutral")
            )
        }
    }

    private fun parse(json: JSONObject): Technicals {
        val osc = json.getJSONObject("oscillators")
        val ma = json.getJSONObject("movingAverages")
        return Technicals(
            price = json.optDouble("price"),
            oscillators = gauge(osc.getJSONObject("summary")),
            movingAverages = gauge(ma.getJSONObject("summary")),
            summary = gauge(json.getJSONObject("summary")),
            rows = rows(osc) + rows(ma)
        )
    }
}
