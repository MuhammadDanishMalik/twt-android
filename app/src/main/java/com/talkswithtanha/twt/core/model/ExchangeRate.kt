package com.talkswithtanha.twt.core.model

import java.util.Date
import kotlin.math.abs

/**
 * A single quote from the verified seller.
 *
 * [buyPaisa] is what the member pays in PKR to receive 1 USD; [sellPaisa] is
 * what they receive in PKR for 1 USD. The spread between them is the seller's
 * margin, shown openly rather than hidden — an exchange that hides its spread
 * does not feel trustworthy.
 *
 * **Everything here is an integer number of paisa.** A Double cannot represent
 * 285.30 exactly, and this number multiplies whole transaction amounts: the
 * error compounds into a real discrepancy between what the app quotes and what
 * Tanha is owed. Doubles appear only at the last moment, for display.
 */
data class ExchangeRate(
    val base: String = "USD",
    val quote: String = "PKR",
    val buyPaisa: Long,
    val sellPaisa: Long,
    val previousBuyPaisa: Long,
    val updatedAt: Date = Date(),
    /** Recent buy-rate history in paisa, oldest first. Drives the sparkline. */
    val history: List<Long> = emptyList(),
    val minAmountPaisa: Long? = null,
    val maxAmountPaisa: Long? = null,
    val isAcceptingDeals: Boolean = true,
    /** Dollars the desk deals on [fundDate]; 0 means no limit. Set on the panel's deals page. */
    val dailyFundUsd: Long = 0,
    /** The desk day the fund is for, `yyyy-MM-dd` in Karachi. */
    val fundDate: String? = null,
    /** Dollars of that fund already taken by approved deals. */
    val fundUsedUsd: Long = 0
) {
    val pair: String get() = "$base/$quote"

    /** Dollars the desk can still take today, or null when no fund limits today. */
    fun fundLeftUsd(today: String = deskDay()): Long? =
        if (fundDate == today && dailyFundUsd > 0) (dailyFundUsd - fundUsedUsd).coerceAtLeast(0) else null

    /** Open for new deals: switched on, and today's fund not used up. */
    val takingDeals: Boolean get() = isAcceptingDeals && fundLeftUsd() != 0L

    val spreadPaisa: Long get() = buyPaisa - sellPaisa
    val deltaPaisa: Long get() = buyPaisa - previousBuyPaisa

    val deltaPercent: Double
        get() = if (previousBuyPaisa == 0L) 0.0
        else (deltaPaisa.toDouble() / previousBuyPaisa.toDouble()) * 100.0

    val direction: Direction
        get() = when {
            deltaPaisa == 0L -> Direction.FLAT
            deltaPaisa > 0 -> Direction.UP
            else -> Direction.DOWN
        }

    enum class Direction { UP, DOWN, FLAT }

    /**
     * The rate that applies to a deal on [side], in paisa.
     *
     * Buying and selling are not the same price — the gap between them is the
     * seller's whole margin. Locking a sell at the buy rate hands the member
     * 341 rupees a dollar where they were quoted 230, which is not a rounding
     * error, it is the business.
     */
    fun paisaFor(side: com.talkswithtanha.twt.core.model.DealSide): Long =
        if (side == com.talkswithtanha.twt.core.model.DealSide.BUY) buyPaisa else sellPaisa

    /** PKR (in paisa) required to buy [usdCents] worth of USD. */
    fun pkrPaisaToBuy(usdCents: Long): Long = usdCents * buyPaisa / 100

    /** PKR (in paisa) received for selling [usdCents] worth of USD. */
    fun pkrPaisaToSell(usdCents: Long): Long = usdCents * sellPaisa / 100

    companion object {
        /** Rates always render to 2dp so the digit count never changes between
         *  ticks and the card does not reflow. */
        fun formatPaisa(paisa: Long): String {
            val sign = if (paisa < 0) "-" else ""
            val abs = abs(paisa)
            return "%s%,d.%02d".format(sign, abs / 100, abs % 100)
        }
    }
}

/** Today as the desk counts it: the date in Karachi, as the panel writes `fundDate`. */
fun deskDay(at: Date = Date()): String =
    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        .apply { timeZone = java.util.TimeZone.getTimeZone("Asia/Karachi") }
        .format(at)
