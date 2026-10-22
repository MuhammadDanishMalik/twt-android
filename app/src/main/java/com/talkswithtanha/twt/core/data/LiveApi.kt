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

    private val BASE = com.talkswithtanha.twt.BuildConfig.LIVE_API_BASE

    enum class Kind(val label: String) { METAL("Metals"), FX("Forex"), INDEX("Indices"), ENERGY("Energy"), CRYPTO("Crypto") }

    data class Instrument(
        val key: String,
        val pair: String,
        val label: String,
        val decimals: Int,
        val kind: Kind = Kind.FX,
        /** Other words people search with: "gold", "dow", "cable". */
        val aliases: String = ""
    )

    private val CURRENCY = mapOf(
        "USD" to "US dollar", "EUR" to "Euro", "GBP" to "British pound", "JPY" to "Japanese yen",
        "AUD" to "Australian dollar", "NZD" to "New Zealand dollar", "CAD" to "Canadian dollar",
        "CHF" to "Swiss franc", "SGD" to "Singapore dollar", "HKD" to "Hong Kong dollar",
        "ZAR" to "South African rand", "MXN" to "Mexican peso", "TRY" to "Turkish lira",
        "SEK" to "Swedish krona", "NOK" to "Norwegian krone", "PLN" to "Polish zloty", "CNH" to "Chinese yuan",
    )

    private val NICKNAME = mapOf(
        "GBPUSD" to "cable", "EURUSD" to "fiber", "USDJPY" to "gopher", "AUDUSD" to "aussie",
        "NZDUSD" to "kiwi", "USDCAD" to "loonie", "USDCHF" to "swissie", "GBPJPY" to "guppy geppy", "EURJPY" to "yuppy",
    )

    private val FX = listOf(
        "EURUSD", "GBPUSD", "USDJPY", "AUDUSD", "NZDUSD", "USDCAD", "USDCHF",
        "EURGBP", "EURJPY", "EURCHF", "EURAUD", "EURCAD", "EURNZD",
        "GBPJPY", "GBPCHF", "GBPAUD", "GBPCAD", "GBPNZD",
        "AUDJPY", "AUDCAD", "AUDCHF", "AUDNZD", "NZDJPY", "NZDCAD", "NZDCHF", "CADJPY", "CADCHF", "CHFJPY",
        "USDSGD", "USDHKD", "USDZAR", "USDMXN", "USDTRY", "USDSEK", "USDNOK", "USDPLN", "USDCNH",
        "EURTRY", "EURSEK", "EURNOK", "EURPLN",
    )

    /** Mirrors `LIVE_INSTRUMENTS` in the website's lib/live/shared.ts. */
    val INSTRUMENTS: List<Instrument> = buildList {
        add(Instrument("XAUUSD", "XAU/USD", "Gold", 2, Kind.METAL, "gold"))
        add(Instrument("XAGUSD", "XAG/USD", "Silver", 3, Kind.METAL, "silver"))
        add(Instrument("XPTUSD", "XPT/USD", "Platinum", 2, Kind.METAL, "platinum"))
        add(Instrument("XPDUSD", "XPD/USD", "Palladium", 2, Kind.METAL, "palladium"))
        FX.forEach { k ->
            val base = k.take(3)
            val quote = k.drop(3)
            add(Instrument(k, "$base/$quote", "${CURRENCY[base]} / ${CURRENCY[quote]}", if (quote == "JPY") 3 else 5, Kind.FX, NICKNAME[k].orEmpty()))
        }
        add(Instrument("US30", "US30", "Dow Jones 30", 2, Kind.INDEX, "dow djia wall street"))
        add(Instrument("US500", "US500", "S&P 500", 2, Kind.INDEX, "spx sp500 s&p spx500"))
        add(Instrument("NAS100", "NAS100", "Nasdaq 100", 2, Kind.INDEX, "nasdaq ndx us100 tech"))
        add(Instrument("US2000", "US2000", "Russell 2000", 2, Kind.INDEX, "russell rut small caps"))
        add(Instrument("GER40", "GER40", "DAX 40", 2, Kind.INDEX, "dax germany de40"))
        add(Instrument("UK100", "UK100", "FTSE 100", 2, Kind.INDEX, "ftse footsie"))
        add(Instrument("FRA40", "FRA40", "CAC 40", 2, Kind.INDEX, "cac france"))
        add(Instrument("EU50", "EU50", "Euro Stoxx 50", 2, Kind.INDEX, "stoxx europe"))
        add(Instrument("JP225", "JP225", "Nikkei 225", 2, Kind.INDEX, "nikkei japan"))
        add(Instrument("HK50", "HK50", "Hang Seng", 2, Kind.INDEX, "hang seng hsi hong kong"))
        add(Instrument("AUS200", "AUS200", "ASX 200", 2, Kind.INDEX, "asx australia"))
        add(Instrument("USOIL", "USOIL", "WTI crude oil", 2, Kind.ENERGY, "oil wti crude cl"))
        add(Instrument("UKOIL", "UKOIL", "Brent crude oil", 2, Kind.ENERGY, "oil brent crude"))
        add(Instrument("NGAS", "NGAS", "Natural gas", 3, Kind.ENERGY, "gas natgas ng"))
        add(Instrument("BTCUSD", "BTC/USD", "Bitcoin", 2, Kind.CRYPTO, "bitcoin btc"))
        add(Instrument("ETHUSD", "ETH/USD", "Ethereum", 2, Kind.CRYPTO, "ethereum ether eth"))
        add(Instrument("SOLUSD", "SOL/USD", "Solana", 2, Kind.CRYPTO, "solana"))
        add(Instrument("XRPUSD", "XRP/USD", "XRP", 4, Kind.CRYPTO, "ripple"))
        add(Instrument("BNBUSD", "BNB/USD", "BNB", 2, Kind.CRYPTO, "binance coin"))
        add(Instrument("DOGEUSD", "DOGE/USD", "Dogecoin", 5, Kind.CRYPTO, "doge dogecoin"))
        add(Instrument("ADAUSD", "ADA/USD", "Cardano", 4, Kind.CRYPTO, "cardano"))
        add(Instrument("LTCUSD", "LTC/USD", "Litecoin", 2, Kind.CRYPTO, "litecoin"))
    }

    /** What brokers and traders also call them. */
    private val ALIAS_KEYS = mapOf(
        "SPX500" to "US500", "SP500" to "US500", "US100" to "NAS100", "USTEC" to "NAS100",
        "DE40" to "GER40", "DE30" to "GER40", "GER30" to "GER40", "JPN225" to "JP225",
        "WTI" to "USOIL", "XTIUSD" to "USOIL", "BRENT" to "UKOIL", "XBRUSD" to "UKOIL",
        "GOLD" to "XAUUSD", "SILVER" to "XAGUSD",
    )

    private fun keyOf(s: String) = s.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }

    /** "XAU/USD", "xauusd", "US 30" → the instrument, or null when there is no live feed for it. */
    fun find(pairOrKey: String): Instrument? {
        val key = keyOf(pairOrKey)
        val wanted = ALIAS_KEYS[key] ?: key
        return INSTRUMENTS.firstOrNull { it.key == wanted }
    }

    /** Markets matching what was typed, best first — the website's `searchInstruments`. */
    fun search(query: String): List<Instrument> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return INSTRUMENTS
        val squashed = q.filter { it.isLetterOrDigit() }
        fun rank(x: Instrument): Int {
            val key = x.key.lowercase()
            val words = "${x.label} ${x.aliases} ${x.kind.label}".lowercase()
            return when {
                squashed.isNotEmpty() && key == squashed -> 0
                squashed.isNotEmpty() && key.startsWith(squashed) -> 1
                squashed.isNotEmpty() && key.contains(squashed) -> 2
                words.contains(q) -> 3
                q.split(Regex("\\s+")).all { w -> words.contains(w) || key.contains(w.filter { it.isLetterOrDigit() }) } -> 4
                else -> 9
            }
        }
        return INSTRUMENTS.map { it to rank(it) }.filter { it.second < 9 }.sortedBy { it.second }.map { it.first }
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
