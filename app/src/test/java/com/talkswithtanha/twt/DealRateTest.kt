package com.talkswithtanha.twt

import com.talkswithtanha.twt.core.model.Deal
import com.talkswithtanha.twt.core.model.DealSide
import com.talkswithtanha.twt.core.model.DealStatus
import com.talkswithtanha.twt.core.model.ExchangeRate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which side of the spread a deal locks.
 *
 * The bug this exists for: every deal locked `buyPaisa`, including sells. A
 * member selling $100 was credited 34,100 rupees instead of the 23,000 they
 * were quoted — an 11,100 rupee loss per hundred dollars, out of Tanha's
 * pocket, with nothing on any screen to show it had happened.
 */
class DealRateTest {

    // 341.00 to buy a dollar, 230.00 to sell one.
    private val rate = ExchangeRate(
        buyPaisa = 34_100,
        sellPaisa = 23_000,
        previousBuyPaisa = 34_100
    )

    @Test
    fun `a buy locks the buy rate`() {
        assertEquals(34_100L, rate.paisaFor(DealSide.BUY))
    }

    @Test
    fun `a sell locks the sell rate`() {
        assertEquals(23_000L, rate.paisaFor(DealSide.SELL))
    }

    private fun deal(side: DealSide) = Deal(
        id = "d", userId = "u", userName = "n", reference = "TWT-TEST",
        side = side, amountUsdCents = 10_000,
        lockedRatePaisa = rate.paisaFor(side),
        status = DealStatus.AWAITING_PAYMENT
    )

    @Test
    fun `buying a hundred dollars costs the buy rate`() {
        val d = deal(DealSide.BUY)
        assertEquals("34,100 PKR", d.payLabel())
        assertEquals("100.00 USD", d.receiveLabel())
    }

    @Test
    fun `selling a hundred dollars pays the sell rate`() {
        val d = deal(DealSide.SELL)
        assertEquals("100.00 USD", d.payLabel())
        // The number the whole bug turned on.
        assertEquals("23,000 PKR", d.receiveLabel())
    }

    @Test
    fun `the spread always favours the seller`() {
        val bought = deal(DealSide.BUY).payTotalMinor      // PKR in
        val sold = deal(DealSide.SELL).receiveTotalMinor   // PKR out
        assert(bought > sold) {
            "a member could buy and immediately sell at a profit: in $bought, out $sold"
        }
    }
}
