package com.talkswithtanha.twt

import com.talkswithtanha.twt.core.model.LoginProvider
import com.talkswithtanha.twt.core.model.MembershipType
import com.talkswithtanha.twt.core.model.User
import com.talkswithtanha.twt.core.model.UserRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * `hasAppAccess` is the gate the whole app turns on. Reading `membershipType`
 * directly instead would keep a lapsed member in forever, because nothing on the
 * client rewrites the field when the expiry passes.
 */
class UserAccessTest {

    private fun user(
        membership: MembershipType,
        expiresAt: Date?
    ) = User(
        id = "u1",
        fullName = "Ali Raza",
        email = "ali@example.com",
        loginProvider = LoginProvider.EMAIL,
        membershipType = membership,
        membershipExpiresAt = expiresAt,
        role = UserRole.MEMBER
    )

    private fun daysFromNow(days: Long) =
        Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(days))

    @Test
    fun `premium with no expiry never lapses`() {
        assertTrue(user(MembershipType.PREMIUM, null).hasAppAccess)
    }

    @Test
    fun `premium with a future expiry has access`() {
        assertTrue(user(MembershipType.PREMIUM, daysFromNow(30)).hasAppAccess)
    }

    @Test
    fun `premium with a past expiry does not`() {
        assertFalse(user(MembershipType.PREMIUM, daysFromNow(-1)).hasAppAccess)
    }

    @Test
    fun `free never has access, whatever the expiry says`() {
        assertFalse(user(MembershipType.FREE, null).hasAppAccess)
        assertFalse(user(MembershipType.FREE, daysFromNow(30)).hasAppAccess)
    }

    @Test
    fun `an unreadable membership falls back to no access`() {
        // The failure mode of guessing premium is giving the app away.
        assertFalse(user(MembershipType.from("nonsense"), null).hasAppAccess)
        assertFalse(user(MembershipType.from(null), null).hasAppAccess)
    }

    @Test
    fun `blocking silences without locking out`() {
        val blocked = user(MembershipType.PREMIUM, null).copy(isBlocked = true)
        assertTrue(blocked.hasAppAccess)
        assertFalse(blocked.canPostInCommunity)
    }

    @Test
    fun `first name falls back to the whole string`() {
        assertTrue(user(MembershipType.FREE, null).firstName == "Ali")
        assertTrue(
            user(MembershipType.FREE, null).copy(fullName = "Tanha").firstName == "Tanha"
        )
    }
}
