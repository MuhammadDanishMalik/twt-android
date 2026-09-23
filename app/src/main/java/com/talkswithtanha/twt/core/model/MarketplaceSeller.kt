package com.talkswithtanha.twt.core.model

/**
 * The seller's shopfront, as shown above the rate.
 *
 * Everything is nullable and the screen omits whatever is absent. These are
 * claims about the person a member is about to transfer money to, so each one
 * is Tanha's to make in the admin panel rather than the app's to assume. A
 * hardcoded "4.9 ★" would be an invented reputation, and on a screen whose
 * whole job is to be trusted with a bank transfer that is the one thing worth
 * getting right.
 */
data class MarketplaceSeller(
    val name: String? = null,
    val handle: String? = null,
    val isVerified: Boolean = false,
    val dealsCompleted: Long? = null,
    val rating: Double? = null,
    val releaseMinutes: Int? = null
) {
    /** True when there is at least one statistic worth giving a row to. */
    val hasStats: Boolean
        get() = dealsCompleted != null || rating != null || releaseMinutes != null

    /** `2,847` → `2.8k`, because the row has three columns and little width. */
    fun dealsLabel(): String? {
        val count = dealsCompleted ?: return null
        return when {
            count >= 1_000_000 -> "%.1fm".format(count / 1_000_000.0)
            count >= 1_000 -> "%.1fk".format(count / 1_000.0)
            else -> count.toString()
        }
    }

    fun ratingLabel(): String? = rating?.let { "%.1f".format(it) }

    fun releaseLabel(): String? = releaseMinutes?.let { "~$it min" }
}
