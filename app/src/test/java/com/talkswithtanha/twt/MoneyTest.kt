package com.talkswithtanha.twt

import com.talkswithtanha.twt.core.model.ExchangeRate
import com.talkswithtanha.twt.features.signals.formatMinor
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Money is an integer in minor units, everywhere, always.
 *
 * A Double cannot represent 285.30 exactly, and the rate multiplies whole
 * transaction amounts — the error compounds into a real discrepancy between what
 * the app quotes and what Tanha is owed.
 */
class MoneyTest {

    private fun rate(buyPaisa: Long, sellPaisa: Long = buyPaisa) = ExchangeRate(
        buyPaisa = buyPaisa,
        sellPaisa = sellPaisa,
        previousBuyPaisa = buyPaisa
    )

    @Test
    fun `paisa format keeps two decimal places and groups thousands`() {
        assertEquals("285.30", ExchangeRate.formatPaisa(28530))
        assertEquals("0.05", ExchangeRate.formatPaisa(5))
        assertEquals("1,000.00", ExchangeRate.formatPaisa(100000))
        assertEquals("-12.34", ExchangeRate.formatPaisa(-1234))
    }

    @Test
    fun `conversion stays in integers`() {
        // 100 USD at PKR 285.30 is exactly PKR 28,530.00 -- 2,853,000 paisa.
        assertEquals(2_853_000L, rate(28530).pkrPaisaToBuy(10_000))
    }

    @Test
    fun `the classic float error does not appear`() {
        // 0.1 + 0.2 as a Double is 0.30000000000000004. In paisa it is 30.
        val tenPaisa = 10L
        val twentyPaisa = 20L
        assertEquals(30L, tenPaisa + twentyPaisa)
        assertEquals("0.30", ExchangeRate.formatPaisa(tenPaisa + twentyPaisa))
    }

    @Test
    fun `spread is the difference the seller keeps`() {
        assertEquals(330L, rate(buyPaisa = 28750, sellPaisa = 28420).spreadPaisa)
    }

    @Test
    fun `direction reflects movement against the previous tick`() {
        val up = ExchangeRate(buyPaisa = 28750, sellPaisa = 28420, previousBuyPaisa = 28690)
        assertEquals(ExchangeRate.Direction.UP, up.direction)
        assertEquals(60L, up.deltaPaisa)

        val flat = ExchangeRate(buyPaisa = 28750, sellPaisa = 28420, previousBuyPaisa = 28750)
        assertEquals(ExchangeRate.Direction.FLAT, flat.direction)
    }

    @Test
    fun `minor units render with the right sign and symbol`() {
        assertEquals("$12.34", formatMinor(1234, "USD"))
        assertEquals("-$12.34", formatMinor(-1234, "USD"))
        assertEquals("$0.07", formatMinor(7, "USD"))
        assertEquals("PKR 1500.00", formatMinor(150000, "PKR"))
    }
}
