package com.talkswithtanha.twt

import com.talkswithtanha.twt.features.auth.PasswordStrength
import com.talkswithtanha.twt.features.auth.passwordChecks
import com.talkswithtanha.twt.features.auth.ratePassword
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordStrengthTest {

    @Test
    fun `under eight characters is too short`() {
        assertEquals(PasswordStrength.TooShort, ratePassword(""))
        assertEquals(PasswordStrength.TooShort, ratePassword("Ab1!xyz"))
    }

    @Test
    fun `obvious passwords are weak however they are dressed up`() {
        assertEquals(PasswordStrength.Weak, ratePassword("Password123!"))
        assertEquals(PasswordStrength.Weak, ratePassword("12345678"))
        assertEquals(PasswordStrength.Weak, ratePassword("abcdefgh"))
        assertEquals(PasswordStrength.Weak, ratePassword("aaaaaaaaaa"))
        assertEquals(PasswordStrength.Weak, ratePassword("qwerty12"))
    }

    @Test
    fun `plain lowercase is weak, variety and length raise it`() {
        assertEquals(PasswordStrength.Weak, ratePassword("goldmoon"))
        assertEquals(PasswordStrength.Fair, ratePassword("Goldmoon"))
        assertEquals(PasswordStrength.Good, ratePassword("Goldmoon7"))
        assertEquals(PasswordStrength.Strong, ratePassword("Goldmoon7!"))
        assertEquals(PasswordStrength.Strong, ratePassword("river-gold-moon-sails"))
    }

    @Test
    fun `checks report each rule`() {
        val c = passwordChecks("Goldmoon7")
        assertTrue(c.longEnough)
        assertTrue(c.mixedCase)
        assertTrue(c.hasNumber)
        assertFalse(c.hasSymbol)
    }
}
