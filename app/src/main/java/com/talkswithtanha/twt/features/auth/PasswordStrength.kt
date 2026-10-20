package com.talkswithtanha.twt.features.auth

/** The shortest password a new account may have. Matches iOS sign-up. */
const val MIN_NEW_PASSWORD_LENGTH = 8

/** How a new password rates, weakest first. */
enum class PasswordStrength(val label: String, val filledBars: Int) {
    TooShort("Too short", 1),
    Weak("Weak", 1),
    Fair("Fair", 2),
    Good("Good", 3),
    Strong("Strong", 4)
}

/** The individual things the meter checks, shown as a checklist under it. */
data class PasswordChecks(
    val longEnough: Boolean,
    val mixedCase: Boolean,
    val hasNumber: Boolean,
    val hasSymbol: Boolean
)

fun passwordChecks(password: String) = PasswordChecks(
    longEnough = password.length >= MIN_NEW_PASSWORD_LENGTH,
    mixedCase = password.any { it.isLowerCase() } && password.any { it.isUpperCase() },
    hasNumber = password.any { it.isDigit() },
    hasSymbol = password.any { !it.isLetterOrDigit() }
)

/**
 * A quick, local rating — a nudge, not a gate. Length counts most, because a
 * long passphrase beats a short password full of symbols; variety adds to it.
 * A handful of passwords every attacker tries first are marked weak however
 * they are dressed up.
 */
fun ratePassword(password: String): PasswordStrength {
    if (password.length < MIN_NEW_PASSWORD_LENGTH) return PasswordStrength.TooShort

    val lower = password.lowercase()
    val stripped = lower.filter { it.isLetter() }
    val obvious = password.toSet().size <= 2 ||
        COMMON.any { lower.startsWith(it) || stripped == it } ||
        isSequence(lower)
    if (obvious) return PasswordStrength.Weak

    val checks = passwordChecks(password)
    var score = 1
    if (password.length >= 12) score++
    if (password.length >= 16) score++
    if (checks.mixedCase) score++
    if (checks.hasNumber) score++
    if (checks.hasSymbol) score++

    // Eight letters of one case is weak; each kind of variety, or real
    // length, moves it up a step. All four kinds at eight is strong.
    return when {
        score <= 1 -> PasswordStrength.Weak
        score == 2 -> PasswordStrength.Fair
        score == 3 -> PasswordStrength.Good
        else -> PasswordStrength.Strong
    }
}

/** "12345678", "abcdefgh", "87654321" and the like. */
private fun isSequence(s: String): Boolean =
    s.length >= 4 && s.zipWithNext().all { (a, b) -> b - a == 1 } ||
        s.length >= 4 && s.zipWithNext().all { (a, b) -> a - b == 1 }

private val COMMON = listOf(
    "password", "passw0rd", "qwerty", "letmein", "welcome", "iloveyou",
    "admin", "abc123", "trading", "forex", "tanha", "twt"
)
