package com.talkswithtanha.twt

import com.talkswithtanha.twt.core.model.SignalStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The stored status strings carry the tick and cross characters. Getting one
 * wrong does not fail to compile — it makes a won trade fall through to the
 * default and render as still running, and a stop loss never announce itself.
 */
class SignalStatusTest {

    @Test
    fun `the stored strings match what the admin panel writes`() {
        assertEquals("ACTIVE", SignalStatus.ACTIVE.stored)
        assertEquals("TP1 HIT", SignalStatus.TP1_HIT.stored)
        assertEquals("TP2 HIT", SignalStatus.TP2_HIT.stored)
        assertEquals("CLOSED ✓", SignalStatus.WON.stored)
        assertEquals("CLOSED ✗", SignalStatus.LOST.stored)
        assertEquals("PENDING", SignalStatus.PENDING.stored)
    }

    @Test
    fun `parsing round-trips every status`() {
        SignalStatus.entries.forEach {
            assertEquals(it, SignalStatus.from(it.stored))
        }
    }

    @Test
    fun `an unknown status reads as active rather than being dropped`() {
        assertEquals(SignalStatus.ACTIVE, SignalStatus.from("SOMETHING NEW"))
        assertEquals(SignalStatus.ACTIVE, SignalStatus.from(null))
    }

    @Test
    fun `ongoing covers everything that has not finished`() {
        assertTrue(SignalStatus.ACTIVE.isOngoing)
        assertTrue(SignalStatus.PENDING.isOngoing)
        assertTrue(SignalStatus.TP1_HIT.isOngoing)
        assertFalse(SignalStatus.WON.isOngoing)
        assertFalse(SignalStatus.LOST.isOngoing)
    }

    @Test
    fun `a partial target counts as won without being closed`() {
        assertTrue(SignalStatus.TP1_HIT.isWon)
        assertTrue(SignalStatus.TP1_HIT.isOngoing)
        assertTrue(SignalStatus.LOST.isLost)
    }
}
