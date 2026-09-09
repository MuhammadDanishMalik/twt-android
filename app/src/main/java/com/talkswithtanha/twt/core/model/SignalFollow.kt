package com.talkswithtanha.twt.core.model

import java.util.Date

/**
 * One member's relationship with one signal: that they took it, and how it went
 * for them.
 *
 * ### Why one document and not two
 *
 * The obvious shape is a `followers` subcollection under the signal (for fanning
 * notifications out) plus a `followedSignals` subcollection under the user (for
 * their own list). That is two writes to keep in step, and the first time one
 * half fails a member is either getting alerts for a signal they dropped or has
 * a journal entry nobody will ever notify them about.
 *
 * So it is one document in a flat collection, addressed `{uid}_{signalId}`, and
 * queried from both directions. Composite ids rather than random ones because
 * following twice must be impossible.
 *
 * ### Why the signal's numbers are copied in
 *
 * [pair], [type], [entryPrice] and [stopLoss] are denormalised on purpose. They
 * are the numbers the member acted on at the moment they acted. Tanha edits
 * signals afterwards — the app shows an "edited" flag precisely because he does
 * — and a journal that silently rewrites itself to match is not a journal. The
 * security rules refuse to let these four change after creation.
 */
data class SignalFollow(
    /** `{uid}_{signalId}`. */
    val id: String,
    val userId: String,
    val signalId: String,
    // Snapshot of the signal at follow time. Immutable after creation.
    val pair: String,
    val type: SignalType,
    val entryPrice: String,
    val stopLoss: String,
    val followedAt: Date,
    /** False once they drop it. The record stays — see [outcome]. */
    val isActive: Boolean,
    val unfollowedAt: Date? = null,
    /**
     * What the member says happened to *them*. Deliberately theirs to set rather
     * than copied from the signal's status: two people can follow the same call,
     * take profit at different targets, and both be telling the truth about
     * their own result.
     */
    val outcome: FollowOutcome = FollowOutcome.OPEN,
    /** Their result in pips. Signed — negative is a loss. */
    val resultPips: Double? = null,
    /** Their result in money, in the currency's minor unit. Integer, never a float. */
    val resultAmountMinor: Long? = null,
    val resultCurrency: String? = null,
    val note: String? = null,
    val recordedAt: Date? = null
) {
    /**
     * Counts toward the member's record. An open position is not a result yet,
     * and including it would make the win rate move every time somebody follows
     * something.
     */
    val isSettled: Boolean get() = outcome != FollowOutcome.OPEN

    companion object {
        fun makeId(userId: String, signalId: String): String = "${userId}_$signalId"
    }
}

enum class FollowOutcome(val stored: String, val label: String) {
    /** Following, nothing recorded yet. */
    OPEN("open", "Open"),
    WIN("win", "Win"),
    LOSS("loss", "Loss"),
    BREAKEVEN("breakeven", "Break even");

    companion object {
        fun from(value: String?): FollowOutcome =
            entries.firstOrNull { it.stored == value } ?: OPEN
    }
}

/**
 * The member's own record, computed from their settled follows.
 *
 * Computed rather than stored as counters on the user document. Counters drift:
 * one failed increment and a member's win rate is permanently wrong with no way
 * to notice, and there is no cheap way to recompute them from a phone. This is
 * derived from data the app already has in memory, so it cannot disagree with
 * the list it sits above.
 */
data class TradingStats(
    val followed: Int,
    val open: Int,
    val wins: Int,
    val losses: Int,
    val breakEvens: Int,
    val netPips: Double
) {
    val settled: Int get() = wins + losses + breakEvens

    /**
     * Null rather than zero when nothing has settled. "0% win rate" on a new
     * account is a true statement that reads as failure.
     */
    val winRate: Double?
        get() = if (settled > 0) wins.toDouble() / settled.toDouble() else null

    companion object {
        val EMPTY = TradingStats(0, 0, 0, 0, 0, 0.0)

        fun from(follows: List<SignalFollow>): TradingStats = TradingStats(
            followed = follows.size,
            open = follows.count { it.outcome == FollowOutcome.OPEN && it.isActive },
            wins = follows.count { it.outcome == FollowOutcome.WIN },
            losses = follows.count { it.outcome == FollowOutcome.LOSS },
            breakEvens = follows.count { it.outcome == FollowOutcome.BREAKEVEN },
            netPips = follows.sumOf { it.resultPips ?: 0.0 }
        )
    }
}
