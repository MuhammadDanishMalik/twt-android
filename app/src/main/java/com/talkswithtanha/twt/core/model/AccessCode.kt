package com.talkswithtanha.twt.core.model

import java.util.Date

/**
 * The string a member types to get into the app, and the rules for turning what
 * they typed into the thing we look up.
 *
 * People retype these off a WhatsApp message, a screenshot or a voice note. So
 * the code that reaches Firestore is *normalised*: uppercased, with every
 * separator and space stripped. `twt 4h2k 9xqp`, `TWT-4H2K-9XQP` and
 * `twt4h2k9xqp` are the same document. Rejecting the first two would be correct
 * and would also generate a support message every time.
 *
 * The normalised form **is** the Firestore document id. That is deliberate:
 * reading a token then needs `get`, never `list`, so the security rules can keep
 * the collection unlistable. Knowing the code is the only way to read the code.
 */
object AccessCode {

    /**
     * Characters a generated code may contain.
     *
     * No `O`/`0`, no `I`/`1`, no `S`/`5`. Not neatness — these are dictated over
     * the phone and copied off a photo of a screen, and every collision here is
     * a member who is certain they typed it right.
     *
     * **Must match the admin panel's generator**, or codes it mints are not
     * typeable in the app.
     */
    const val ALPHABET = "ABCDEFGHJKLMNPQRTUVWXYZ23456789"

    const val PREFIX = "TWT"

    /** `TWT` then eight alphabet characters — what the panel mints. */
    private val TOKEN_SHAPE = Regex("^TWT[A-Z0-9]{8}$")

    /**
     * Anything else short and alphanumeric is still worth *looking up*. Tanha has
     * handed out codes that predate the TWT prefix, and refusing to try them
     * client-side turns a valid code into "that does not look like a code".
     * The lookup decides; this only filters out obvious noise.
     */
    private val GENERAL_SHAPE = Regex("^[A-Z0-9]{4,16}$")

    /** Everything that is not a letter or a digit is noise a human added. */
    fun normalise(raw: String): String =
        raw.uppercase().filter { it.isLetter() || it.isDigit() }

    /** `TWT4H2K9XQP` -> `TWT-4H2K-9XQP`, for showing back to the member. */
    fun display(normalised: String): String = when {
        normalised.startsWith(PREFIX) && normalised.length == PREFIX.length + 8 -> {
            val body = normalised.drop(PREFIX.length)
            "$PREFIX-${body.take(4)}-${body.takeLast(4)}"
        }
        normalised.length == 6 && normalised.all { it.isDigit() } ->
            "${normalised.take(3)}-${normalised.takeLast(3)}"
        else -> normalised
    }

    fun isWellFormed(normalised: String): Boolean = GENERAL_SHAPE.matches(normalised)

    fun isStandardShape(normalised: String): Boolean = TOKEN_SHAPE.matches(normalised)
}

/**
 * What redeeming a code got you. Read back off the token document so the
 * confirmation screen can name the real end date rather than the one the client
 * hoped for.
 */
data class AccessGrant(
    /** Null means the grant does not expire — a code Tanha issued permanently. */
    val expiresAt: Date?,
    /** Free text from the admin panel: "Ali — 3 months". Shown nowhere in the
     *  app; carried so an error can be logged with something human in it. */
    val label: String?
)

/**
 * One case per thing a member can actually be told.
 *
 * The distinction between [NotFound] and [AlreadyClaimed] is worth keeping: the
 * first means try again, the second means talk to us, and collapsing them into
 * "invalid code" makes every one of them a support message.
 */
sealed class AccessRedemptionError(val message: String) {
    data object Malformed : AccessRedemptionError(
        "That does not look like an access code. They start with TWT and have eight more characters."
    )
    data object NotFound : AccessRedemptionError(
        "We could not find that access code. Check it character by character, or contact support."
    )
    data object AlreadyClaimed : AccessRedemptionError(
        "This access code is already in use on another account. Contact support and we will sort it out."
    )
    data object Revoked : AccessRedemptionError(
        "This access code has been turned off. Contact support to get a new one."
    )
    data object Expired : AccessRedemptionError(
        "This access code has expired. Contact support to renew your access."
    )
    data object Offline : AccessRedemptionError(
        "No connection. Check your internet and try again."
    )
    class Unknown(detail: String) : AccessRedemptionError(detail)
}

/** Thrown by the repository so a screen never has to interpret a Firestore error. */
class AccessRedemptionException(val reason: AccessRedemptionError) :
    Exception(reason.message)
