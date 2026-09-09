package com.talkswithtanha.twt

import com.talkswithtanha.twt.core.model.AccessCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The normalised code is the Firestore document id, so these are not cosmetic
 * string tests: a code that normalises differently on Android than on iOS looks
 * up a document that does not exist, and the member is told their valid code is
 * invalid.
 */
class AccessCodeTest {

    @Test
    fun `separators and case are stripped`() {
        val expected = "TWT4H2K9XQP"
        assertEquals(expected, AccessCode.normalise("TWT-4H2K-9XQP"))
        assertEquals(expected, AccessCode.normalise("twt 4h2k 9xqp"))
        assertEquals(expected, AccessCode.normalise("twt4h2k9xqp"))
        assertEquals(expected, AccessCode.normalise("  TWT_4H2K_9XQP  "))
    }

    @Test
    fun `display re-inserts the dashes`() {
        assertEquals("TWT-4H2K-9XQP", AccessCode.display("TWT4H2K9XQP"))
    }

    @Test
    fun `display leaves an unexpected shape alone`() {
        assertEquals("TWT123", AccessCode.display("TWT123"))
    }

    @Test
    fun `display groups a six digit joining code`() {
        assertEquals("123-456", AccessCode.display("123456"))
    }

    @Test
    fun `well-formed accepts the minted shape`() {
        assertTrue(AccessCode.isWellFormed("TWT4H2K9XQP"))
        assertTrue(AccessCode.isStandardShape("TWT4H2K9XQP"))
    }

    @Test
    fun `well-formed rejects noise but allows legacy codes`() {
        assertFalse(AccessCode.isWellFormed(""))
        assertFalse(AccessCode.isWellFormed("AB"))
        assertFalse(AccessCode.isWellFormed("THIS-IS-FAR-TOO-LONG-TO-BE-A-CODE"))
        // Short alphanumeric codes predate the TWT prefix. The lookup decides;
        // the client must not refuse to try them.
        assertTrue(AccessCode.isWellFormed("ABC123"))
        assertFalse(AccessCode.isStandardShape("ABC123"))
    }

    @Test
    fun `the alphabet drops the characters that get misread`() {
        // These are dictated over the phone and copied off screenshots, so every
        // look-alike is removed: O and I go along with the 0 and 1 they are
        // confused with, and the digits run 2-9. S goes too, but 5 stays -- a 5
        // read aloud is never heard as an S, only the other way round.
        listOf('O', '0', 'I', '1', 'S').forEach {
            assertFalse("$it must not be in the alphabet", AccessCode.ALPHABET.contains(it))
        }
        assertTrue("5 is kept", AccessCode.ALPHABET.contains('5'))

        assertEquals("23456789", AccessCode.ALPHABET.filter { it.isDigit() })

        // Pinned literally: this string has to match the admin panel character
        // for character, or codes it mints cannot be typed into the app.
        assertEquals("ABCDEFGHJKLMNPQRTUVWXYZ23456789", AccessCode.ALPHABET)
        assertEquals(31, AccessCode.ALPHABET.length)
    }
}
