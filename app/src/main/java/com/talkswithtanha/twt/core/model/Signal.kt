package com.talkswithtanha.twt.core.model

import java.util.Date

/**
 * One trading call Tanha published.
 *
 * Prices are carried twice — once as a number and once as the string he typed.
 * That looks redundant and is deliberate: "1.0855" and "2,350.50" are different
 * conventions per instrument, and re-deriving the display string from a Double
 * gets the decimal places wrong for exactly the pairs that matter. The number is
 * for maths, the string is for showing.
 */
data class Signal(
    /** The Firestore document id. A string, not a UUID — Firestore generates ids
     *  like "3kD9xQ" and the admin panel addresses the same document. */
    val id: String,
    val pair: String,
    val asset: SignalAsset = SignalAsset.FOREX,
    val type: SignalType = SignalType.BUY,
    val entryPrice: String,
    val entryPriceValue: Double = 0.0,
    val takeProfits: List<TakeProfit> = emptyList(),
    val stopLoss: String,
    val stopLossValue: Double = 0.0,
    val status: SignalStatus = SignalStatus.ACTIVE,
    val timestamp: Date = Date(),
    val timeframe: String = "—",
    val riskReward: String = "—",
    val pipsGained: Double? = null,
    val notes: String? = null,
    /** How the trade is meant to be taken. Changes how long a member is expected
     *  to hold, which is the first thing they ask. */
    val tradeStyle: TradeStyle = TradeStyle.SCALP,
    /** A chart screenshot Tanha attaches — his drawing on the chart is the
     *  explanation the numbers cannot give. */
    val chartImageUrl: String? = null,
    /** The client chose "yes, and show members it was edited" over silent edits.
     *  Someone who acted on the original numbers is entitled to know they moved. */
    val isEdited: Boolean = false
) {
    data class TakeProfit(
        val label: String,
        val price: String,
        val isHit: Boolean = false
    )
}

enum class SignalAsset(val stored: String) {
    FOREX("FX"),
    GOLD("XAU"),
    CRYPTO("Crypto"),
    INDICES("Index");

    companion object {
        fun from(value: String?): SignalAsset =
            entries.firstOrNull { it.stored == value } ?: FOREX
    }
}

enum class SignalType(val stored: String) {
    BUY("BUY"),
    SELL("SELL");

    companion object {
        fun from(value: String?): SignalType =
            entries.firstOrNull { it.stored == value } ?: BUY
    }
}

/**
 * Raw values are what the admin panel writes, so they must not be renamed
 * casually.
 */
enum class TradeStyle(val stored: String, val explanation: String) {
    SCALP("Scalp", "Minutes to an hour. Watch it closely."),
    SWING("Swing", "Days. Hold through the noise."),
    DIRECTIONAL("Directional", "A view on where price is heading, not a precise entry."),
    LIMIT_ORDER("Limit Order", "Set the order and wait for price to come to you.");

    companion object {
        fun from(value: String?): TradeStyle =
            entries.firstOrNull { it.stored == value } ?: SCALP
    }
}

/**
 * The stored values include the tick and cross characters. `CLOSED ✓` and
 * `CLOSED ✗` are what is actually in the database — written by the admin panel,
 * read by iOS — so they are matched literally here.
 *
 * Getting one of these wrong does not fail to compile. It makes a won trade fall
 * through to the default and render as still running.
 */
enum class SignalStatus(val stored: String) {
    ACTIVE("ACTIVE"),
    TP1_HIT("TP1 HIT"),
    TP2_HIT("TP2 HIT"),
    WON("CLOSED ✓"),
    LOST("CLOSED ✗"),
    PENDING("PENDING");

    val isOngoing: Boolean
        get() = this == ACTIVE || this == TP1_HIT || this == TP2_HIT || this == PENDING

    val isWon: Boolean get() = this == WON || this == TP1_HIT || this == TP2_HIT
    val isLost: Boolean get() = this == LOST

    companion object {
        fun from(value: String?): SignalStatus =
            entries.firstOrNull { it.stored == value } ?: ACTIVE
    }
}
