package com.talkswithtanha.twt

import com.talkswithtanha.twt.core.navigation.VERIFICATION_REQUIRED_FROM
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * The line between members who must verify their email and members who must not.
 *
 * This is a hardcoded instant, and the first version of it was wrong by exactly
 * one year — which sent every existing member to a verification screen they
 * could never pass, on an app they had already paid for. Nothing failed; the
 * constant simply said 2025 while its comment said 2026.
 *
 * A wrong value here is silent in both directions: too early locks members out,
 * too late lets unverified accounts straight through.
 */
class VerificationCutoffTest {

    private fun field(unit: Int): Int {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.time = VERIFICATION_REQUIRED_FROM
        return calendar.get(unit)
    }

    @Test
    fun `is the date the verification step shipped`() {
        assertTrue("expected 2026, got ${field(Calendar.YEAR)}", field(Calendar.YEAR) == 2026)
        // Calendar months are zero-based, so September is 8.
        assertTrue("expected September, got month ${field(Calendar.MONTH)}", field(Calendar.MONTH) == 8)
    }

    @Test
    fun `is in the past, so existing members are exempt`() {
        assertTrue(
            "a cutoff in the future exempts everybody and the check does nothing",
            VERIFICATION_REQUIRED_FROM.time < System.currentTimeMillis()
        )
    }

    @Test
    fun `is recent, not a stale epoch from a previous year`() {
        // The bug it is guarding against: a value a year out still parses, is
        // still in the past, and still breaks every account created since.
        val oneYear = 365L * 24 * 60 * 60 * 1000
        assertTrue(
            "cutoff is more than a year old — check it was not copied from a previous release",
            System.currentTimeMillis() - VERIFICATION_REQUIRED_FROM.time < oneYear
        )
    }
}
