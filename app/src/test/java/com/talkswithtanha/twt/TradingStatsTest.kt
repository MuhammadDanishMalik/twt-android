package com.talkswithtanha.twt

import com.talkswithtanha.twt.core.model.FollowOutcome
import com.talkswithtanha.twt.core.model.SignalFollow
import com.talkswithtanha.twt.core.model.SignalType
import com.talkswithtanha.twt.core.model.TradingStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Date

class TradingStatsTest {

    private fun follow(
        id: String,
        outcome: FollowOutcome,
        pips: Double? = null,
        isActive: Boolean = true
    ) = SignalFollow(
        id = id,
        userId = "u1",
        signalId = id,
        pair = "XAU/USD",
        type = SignalType.BUY,
        entryPrice = "2350.50",
        stopLoss = "2338.00",
        followedAt = Date(),
        isActive = isActive,
        outcome = outcome,
        resultPips = pips
    )

    @Test
    fun `win rate is null until something settles`() {
        // "0% win rate" on a new account is a true statement that reads as
        // failure, so it is deliberately not shown.
        assertNull(TradingStats.EMPTY.winRate)
        assertNull(TradingStats.from(listOf(follow("a", FollowOutcome.OPEN))).winRate)
    }

    @Test
    fun `open positions do not move the win rate`() {
        val stats = TradingStats.from(
            listOf(
                follow("a", FollowOutcome.WIN),
                follow("b", FollowOutcome.LOSS),
                follow("c", FollowOutcome.OPEN)
            )
        )
        assertEquals(2, stats.settled)
        assertEquals(1, stats.open)
        assertEquals(0.5, stats.winRate!!, 0.0001)
    }

    @Test
    fun `break-evens count as settled but not as wins`() {
        val stats = TradingStats.from(
            listOf(
                follow("a", FollowOutcome.WIN),
                follow("b", FollowOutcome.BREAKEVEN)
            )
        )
        assertEquals(2, stats.settled)
        assertEquals(0.5, stats.winRate!!, 0.0001)
    }

    @Test
    fun `net pips is signed and sums only what was recorded`() {
        val stats = TradingStats.from(
            listOf(
                follow("a", FollowOutcome.WIN, pips = 145.0),
                follow("b", FollowOutcome.LOSS, pips = -30.0),
                follow("c", FollowOutcome.OPEN, pips = null)
            )
        )
        assertEquals(115.0, stats.netPips, 0.0001)
    }

    @Test
    fun `a dropped follow still counts toward the record`() {
        // Unfollowing sets a flag rather than deleting: the record is the
        // member's history and outlives the following.
        val stats = TradingStats.from(
            listOf(follow("a", FollowOutcome.LOSS, pips = -12.0, isActive = false))
        )
        assertEquals(1, stats.followed)
        assertEquals(0, stats.open)
        assertEquals(1, stats.losses)
    }
}
