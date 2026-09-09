package com.talkswithtanha.twt.core.model

/**
 * Where a member is sent when they need a person.
 *
 * Every field has a compiled-in fallback so a missing or half-filled
 * `config/support` document degrades to something that still opens, rather than
 * to a screen with dead buttons. The Firestore document is the source of truth:
 * a support number changes — a SIM is swapped, a second staff member takes the
 * queue — and a number that can only be corrected by shipping an update is a
 * number that stays wrong for however long review takes.
 *
 * The iOS project keeps placeholders for these in `SupportLinks.swift` with a
 * TODO. On Android they are read live and the fallbacks below are only ever seen
 * before the first snapshot arrives.
 */
data class SupportConfig(
    val accessPageUrl: String = DEFAULT_ACCESS_PAGE,
    /** Digits only: country code first, no `+`, no spaces. `wa.me` rejects
     *  anything else, and it fails by opening a blank page rather than saying so. */
    val whatsAppNumber: String = DEFAULT_WHATSAPP,
    val supportEmail: String = DEFAULT_EMAIL,
    val termsUrl: String = DEFAULT_TERMS,
    val privacyUrl: String = DEFAULT_PRIVACY
) {
    /**
     * A WhatsApp deep link with the first message already typed.
     *
     * `https://wa.me/...` rather than `whatsapp://`: the https form falls back to
     * the web client when WhatsApp is not installed, where the custom scheme
     * silently does nothing.
     */
    fun whatsAppUrl(message: String): String =
        "https://wa.me/$whatsAppNumber?text=" + java.net.URLEncoder.encode(message, "UTF-8")

    fun mailtoUrl(subject: String): String =
        "mailto:$supportEmail?subject=" + java.net.URLEncoder.encode(subject, "UTF-8")

    companion object {
        const val DEFAULT_ACCESS_PAGE = "https://twt-admin.vercel.app/get-access"
        const val DEFAULT_WHATSAPP = "923000000000"
        const val DEFAULT_EMAIL = "support@talkswithtanha.com"
        const val DEFAULT_TERMS = "https://twt-admin.vercel.app/terms"
        const val DEFAULT_PRIVACY = "https://twt-admin.vercel.app/privacy"
    }
}
