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
    val isAcceptingDeals: Boolean = true
) {
    val pair: String get() = "$base/$quote"

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
