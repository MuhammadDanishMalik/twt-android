package com.talkswithtanha.twt.core.model

import java.util.Date

/**
 * The app's own idea of a person.
 *
 * Deliberately free of any Firebase import: the Firestore mapping lives in
 * `FirebaseUserRepository`, so swapping the backend later touches one file
 * rather than every screen that reads the current user.
 */
data class User(
    /** The Firebase Auth uid, and also the document id in `users`. */
    val id: String,
    val fullName: String,
    val email: String,
    /** Only ever set by Apple/Google sign-in. */
    val profilePhoto: String? = null,
    val phone: String? = null,
    /** A short self-introduction. Nothing in the app depends on it. */
    val bio: String? = null,
    /** ISO 3166-1 alpha-2. Kept for the marketplace, which offers different
     *  receiving accounts per country. */
    val country: String? = null,
    val loginProvider: LoginProvider = LoginProvider.EMAIL,
    /**
     * Whether an access code has been redeemed on this account. There is no free
     * tier — this is [MembershipType.FREE] for exactly as long as someone has
     * signed up and not yet typed their code.
     */
    val membershipType: MembershipType = MembershipType.FREE,
    /** When access ends. Null while there is none, and null once granted means
     *  "does not expire" — a code Tanha issued permanently. */
    val membershipExpiresAt: Date? = null,
    val accessCode: String? = null,
    val accessGrantedAt: Date? = null,
    val role: UserRole = UserRole.MEMBER,
    /** Set from the admin panel. A blocked member can still sign in and read,
     *  but cannot post — the client asked to silence people, not lock them out. */
    val isBlocked: Boolean = false,
    val referralCode: String? = null,
    val fcmTokens: List<String> = emptyList(),
    // Single-device session enforcement.
    val currentSessionId: String? = null,
    val lastActiveDeviceId: String? = null,
    val lastActiveDeviceModel: String? = null,
    val lastActivePlatform: String? = null,
    val lastLoginAt: Date? = null,
    val createdAt: Date = Date(),
    val updatedAt: Date = Date()
) {

    /**
     * The single answer to "may this person into the app at all".
     *
     * Every gate must go through here rather than testing
     * `membershipType == PREMIUM` directly. Reading the raw field means a lapsed
     * access code keeps working forever, because nothing on the client rewrites
     * the field when the date passes — the admin panel and the security rules
     * both apply this same expiry test, and this is the copy the UI reads.
     *
     * Evaluated against `Date()` on each call rather than cached, so a member
     * whose code expires while the app is open loses access at the next
     * recomposition instead of at the next launch.
     */
    val hasAppAccess: Boolean
        get() = membershipType == MembershipType.PREMIUM &&
            (membershipExpiresAt == null || membershipExpiresAt.after(Date()))

    /**
     * May send messages in the community.
     *
     * Access alone is enough. With the access gate in front of the whole app,
     * everybody who can see a room has been let in deliberately; blocking —
     * silencing one person from the admin panel without locking them out — is
     * the only thing left that closes it.
     */
    val canPostInCommunity: Boolean get() = !isBlocked

    val isStaff: Boolean get() = role == UserRole.OWNER || role == UserRole.ADMIN

    /** First name, for greetings. Falls back to the whole string rather than
     *  showing an empty header for a single-word name. */
    val firstName: String
        get() = fullName.trim().substringBefore(' ').ifEmpty { fullName }

    companion object {
        /**
         * The document written the first time someone signs in. Everything
         * privileged starts at its safest value — no access, member, not blocked.
         * Access is raised only by redeeming a real code (which the rules verify
         * against `accessTokens`) or by staff from the admin panel.
         */
        fun newAccount(
            id: String,
            fullName: String,
            email: String,
            profilePhoto: String? = null,
            country: String? = null,
            loginProvider: LoginProvider
        ): User = User(
            id = id,
            fullName = fullName,
            email = email,
            profilePhoto = profilePhoto,
            country = country,
            loginProvider = loginProvider,
            membershipType = MembershipType.FREE,
            role = UserRole.MEMBER,
            isBlocked = false,
            lastLoginAt = Date()
        )

        /** Something to show in the header before the person has set a name. */
        fun fallbackName(email: String): String {
            val handle = email.substringBefore('@')
            return if (handle.isEmpty()) "Trader"
            else handle.replaceFirstChar { it.uppercase() }
        }
    }
}

/**
 * Retained as the storage shape rather than renamed, because it is written by
 * the iOS app, the admin panel and the security rules, and every existing user
 * document already carries it. [FREE] no longer means a tier — it means "no
 * access code redeemed yet".
 */
enum class MembershipType(val stored: String) {
    FREE("free"),
    PREMIUM("premium");

    companion object {
        /**
         * An unreadable membership field falls back to [FREE] — which means
         * "back to the access gate". The failure mode of guessing premium is
         * giving the app away.
         */
        fun from(value: String?): MembershipType =
            entries.firstOrNull { it.stored == value } ?: FREE
    }
}

enum class UserRole(val stored: String) {
    /** An ordinary app user. */
    MEMBER("member"),
    /** Tanha. */
    OWNER("owner"),
    /** Staff he grants admin-panel access to. */
    ADMIN("admin");

    companion object {
        fun from(value: String?): UserRole =
            entries.firstOrNull { it.stored == value } ?: MEMBER
    }
}

enum class LoginProvider(val stored: String) {
    /**
     * Present because existing accounts carry it — members who signed up on
     * iPhone. Android never writes it: Sign in with Apple is an iOS-only flow
     * here and is deliberately not offered.
     */
    APPLE("Apple"),
    GOOGLE("Google"),
    EMAIL("Email");

    companion object {
        /** Firestore holds whatever was last written. An unknown provider must
         *  not throw away the whole user document. */
        fun from(value: String?): LoginProvider =
            entries.firstOrNull { it.stored == value } ?: EMAIL
    }
}
