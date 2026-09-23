package com.talkswithtanha.twt.core.model

import java.util.Locale

/**
 * Country codes to country names.
 *
 * The Android half of `CountryCatalog.swift`. Both apps store the same thing in
 * Firestore — an uppercase two-letter ISO code — and both turn it into a name
 * using the platform's own locale data rather than a bundled list, so a member
 * sees their country in their own language on either phone.
 */
object Countries {

    /** "PK" → "Pakistan". Anything that is not a country code reads "Not set". */
    fun name(code: String?): String {
        val trimmed = code?.trim().orEmpty()
        if (trimmed.length != 2) return NOT_SET
        // An unknown region code makes getDisplayCountry echo the code back,
        // which would show a member the string "ZZ" as though it were a place.
        val resolved = Locale.Builder()
            .setRegion(trimmed.uppercase(Locale.ROOT))
            .build()
            .getDisplayCountry(Locale.getDefault())
        return if (resolved.equals(trimmed, ignoreCase = true)) NOT_SET else resolved
    }

    private const val NOT_SET = "Not set"
}
