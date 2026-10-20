package com.talkswithtanha.twt.core.model

/**
 * What a member tells us about their trading when they first sign up.
 *
 * Self-reported and informational: the team reads it in the admin panel to
 * know who they are talking to, and nothing in the app grants or withholds
 * anything because of it. Stored as a map on `users/{uid}.tradingProfile`
 * with the [stored] strings below; the security rules check its shape.
 */
data class TradingProfile(
    val brokerStatus: BrokerStatus? = null,
    /** A name from [Brokers.known], or whatever the member typed for "Other". */
    val broker: String? = null,
    val ibStatus: IbStatus? = null,
    val experience: Experience? = null,
    val tradingDuration: TradingDuration? = null
)

/** Implemented by each option set, so the setup screen can render any of them. */
interface ProfileOption {
    val stored: String
    val title: String
    val subtitle: String?

    /**
     * Where this answer sits on a scale, for options that are one — how long,
     * how experienced. Drawn as rising bars beside the answer. Null for
     * answers that are not a scale (which broker, which IB).
     */
    val level: Int? get() = null
    val maxLevel: Int get() = 0

    /** A two-word reply stamped on screen when this answer is picked. */
    val reaction: String? get() = null
}

enum class BrokerStatus(
    override val stored: String,
    override val title: String,
    override val subtitle: String?
) : ProfileOption {
    LIVE("live", "Yes, a live account", "Trading real money"),
    DEMO("demo", "Only a demo account", "Practising first"),
    NONE("none", "Not yet", "We can help you pick one");

    /** Whether there is an account to ask about a broker and IB for. */
    val hasAccount: Boolean get() = this != NONE

    override val reaction: String
        get() = when (this) {
            LIVE -> "Real money"
            DEMO -> "Smart move"
            NONE -> "We'll help"
        }

    companion object {
        fun from(value: String?) = entries.firstOrNull { it.stored == value }
    }
}

enum class IbStatus(
    override val stored: String,
    override val title: String,
    override val subtitle: String?
) : ProfileOption {
    // Deliberately names no broker and no partner code. Promoting a
    // forex/CFD broker's affiliate code inside the app is what Google Play's
    // Financial Services policy looks hardest at, and the team only needs to
    // know whether an account is linked — they confirm which one in chat.
    YES("ib", "Yes, under an IB", "Linked to a partner when it was opened"),
    NONE("none", "No", "Opened directly with the broker"),
    UNSURE("unsure", "Not sure", "We'll check it with you");

    override val reaction: String
        get() = when (this) {
            YES -> "Noted"
            NONE -> "Got it"
            UNSURE -> "We'll check"
        }

    companion object {
        fun from(value: String?) = entries.firstOrNull { it.stored == value }
    }
}

enum class Experience(
    override val stored: String,
    override val title: String,
    override val subtitle: String?
) : ProfileOption {
    BEGINNER("beginner", "Beginner", "Learning how the market works"),
    INTERMEDIATE("intermediate", "Intermediate", "I take trades with a plan"),
    ADVANCED("advanced", "Advanced", "I have my own strategy");

    override val level: Int get() = ordinal + 1
    override val maxLevel: Int get() = entries.size

    override val reaction: String
        get() = when (this) {
            BEGINNER -> "Everyone starts here"
            INTERMEDIATE -> "Solid"
            ADVANCED -> "Respect"
        }

    companion object {
        fun from(value: String?) = entries.firstOrNull { it.stored == value }
    }
}

enum class TradingDuration(
    override val stored: String,
    override val title: String,
    override val subtitle: String?
) : ProfileOption {
    NEW("new", "Haven't started", null),
    UNDER_6M("lt6m", "Under 6 months", null),
    UP_TO_1Y("6to12m", "6 – 12 months", null),
    UP_TO_3Y("1to3y", "1 – 3 years", null),
    OVER_3Y("gt3y", "3+ years", null);

    override val level: Int get() = ordinal
    override val maxLevel: Int get() = entries.size - 1

    override val reaction: String
        get() = when (this) {
            NEW -> "Day one"
            UNDER_6M -> "Fresh start"
            UP_TO_1Y -> "Getting there"
            UP_TO_3Y -> "Seasoned"
            OVER_3Y -> "Veteran"
        }

    companion object {
        fun from(value: String?) = entries.firstOrNull { it.stored == value }
    }
}

object Brokers {
    /**
     * The brokers members name most, alphabetically — a neutral list to pick
     * from, with none put first. The app does not recommend a broker.
     */
    val known = listOf("Exness", "FBS", "HFM", "IC Markets", "Octa", "Pepperstone", "Vantage", "XM")
    const val OTHER = "Other"
}
