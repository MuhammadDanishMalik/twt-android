package com.talkswithtanha.twt.features.markets

/**
 * Every currency the app shows goes with its flag, so a row reads at a
 * glance — "🇬🇧 GBP" rather than three letters to decode.
 */
internal val CURRENCY_FLAGS = mapOf(
    "USD" to "🇺🇸", "EUR" to "🇪🇺", "GBP" to "🇬🇧", "JPY" to "🇯🇵", "AUD" to "🇦🇺",
    "CAD" to "🇨🇦", "CHF" to "🇨🇭", "NZD" to "🇳🇿", "CNY" to "🇨🇳", "HKD" to "🇭🇰",
    "SGD" to "🇸🇬", "INR" to "🇮🇳", "KRW" to "🇰🇷", "ZAR" to "🇿🇦", "MXN" to "🇲🇽",
    "BRL" to "🇧🇷", "SEK" to "🇸🇪", "NOK" to "🇳🇴", "DKK" to "🇩🇰", "TRY" to "🇹🇷",
    "XAU" to "🥇", "XAG" to "🥈", "OIL" to "🛢️", "BTC" to "₿"
)

internal fun currencyFlag(code: String): String = CURRENCY_FLAGS[code.uppercase()] ?: "🌐"

/** "🇬🇧 GBP" — the way a currency is written anywhere it carries information. */
internal fun withFlag(code: String): String = "${currencyFlag(code)} $code"
