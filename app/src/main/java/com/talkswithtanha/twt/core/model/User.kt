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
    /**
     * Which areas of the business this account may work in. Empty means one of
     * two things and the app treats them the same: either they are not staff, or
     * they are a staff account from before areas existed, which the security
     * rules still give everything. See [can].
     */
    val staffAccess: List<StaffArea> = emptyList(),
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
    /** Broker, IB and experience, from the profile-setup step. */
    val tradingProfile: TradingProfile? = null,
    /** When profile setup was finished. Null means it has not been — the gate
     *  sends a member without access there before the access-code screen. */
    val profileSetupAt: Date? = null,
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

    /**
     * Their code ran out, as opposed to never having had one.
     *
     * The difference decides what happens to them: somebody who has just signed
     * up and not yet typed a code belongs at the access gate, and somebody whose
     * three months are up is signed out of this device. Both have
     * [hasAppAccess] false, so testing that alone would throw every new member
     * off the app in the seconds between signing up and redeeming.
     */
    val accessExpired: Boolean
        get() = accessCode != null && membershipExpiresAt?.before(Date()) == true

    val isStaff: Boolean get() = role == UserRole.OWNER || role == UserRole.ADMIN

    /**
     * May they work in this area?
     *
     * Mirrors `can()` in firestore.rules, which is what actually enforces it —
     * this only decides what the app offers. An empty list on a staff account
     * means "everything", which is how the accounts that predate the admin
     * panel's Team page keep working.
     */
    fun can(area: StaffArea): Boolean = when {
        !isStaff -> false
        role == UserRole.OWNER -> true
        staffAccess.isEmpty() -> true
        else -> area in staffAccess
    }

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

/**
 * What one member of the team may work on.
 *
 * Tanha owns the business and is never checked against these; everybody he
 * brings in gets a list, set from the admin panel's Team page. The areas are
 * drawn around *jobs* rather than screens — somebody running the exchange needs
 * deals and the chats that come with them, and handing them that should not also
 * hand them the ability to post a trade to five hundred people.
 *
 * Mirrors `StaffArea` in iOS and in twt-admin/lib/types.ts. A name that drifts
 * does not fail to compile; it quietly hides a screen from somebody entitled
 * to it.
 */
enum class StaffArea(val stored: String, val title: String) {
    SIGNALS("signals", "Signals"),
    TICKETS("tickets", "Support tickets"),
    CHATS("chats", "Member chats"),
    MARKETPLACE("marketplace", "Marketplace"),
    APP_CONTENT("appContent", "App content"),
    MEMBERS("members", "Members & money");

    companion object {
        /** Unknown names are dropped rather than failing the whole profile: a
         *  panel that learns a seventh area before the app does must not sign
         *  everybody out. */
        fun from(values: List<*>?): List<StaffArea> =
            values.orEmpty().mapNotNull { raw -> entries.firstOrNull { it.stored == raw } }
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
