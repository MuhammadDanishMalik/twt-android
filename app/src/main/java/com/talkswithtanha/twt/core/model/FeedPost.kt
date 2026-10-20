package com.talkswithtanha.twt.core.model

import java.util.Date

/**
 * One thing Tanha has put in front of the community: a note, a chart, a clip,
 * a piece of news.
 *
 * Only he posts — the security rules enforce that, not the app — and everybody
 * else reads, reacts and replies. It is his desk rather than a forum, which is
 * the whole reason this is not another chat room.
 */
data class FeedPost(
    val id: String,
    val authorId: String,
    val authorName: String,
    val authorPhoto: String?,
    val text: String,
    val images: List<String> = emptyList(),
    val videoUrl: String? = null,
    /**
     * The instrument this post is about, when there is one — "XAU/USD".
     *
     * It is what makes this a trader's feed rather than a timeline: a post
     * about gold carries a way straight to gold's chart.
     */
    val pair: String? = null,
    /**
     * The signal this post is about.
     *
     * Set automatically when a trade is posted — the call lands in the feed as
     * the ticket itself rather than as a sentence about it — and the row then
     * draws the real card, with its levels and a Follow button.
     */
    val signalId: String? = null,
    /**
     * A voice note Tanha recorded, as a Cloudinary URL.
     *
     * The thing people actually want from a mentor is him talking, and a
     * ninety-second note before the London open is worth more than a paragraph
     * written an hour later.
     */
    val audioUrl: String? = null,
    /**
     * What the note is about — "Why I am staying out of gold today".
     *
     * Required when there is audio, and the reason voice notes work here at
     * all: a column of identical grey players with nothing but a duration on
     * them is unscannable, and nobody taps a note without knowing what it is.
     */
    val audioTitle: String? = null,
    /** Recorded length, so the card can say "1:42" before anything is loaded. */
    val audioDurationSec: Int = 0,
    val createdAt: Date = Date(),
    val editedAt: Date? = null,
    val reactionCount: Int = 0,
    /** How many of each kind, for the little row of faces under a busy post. */
    val reactionCounts: Map<String, Int> = emptyMap(),
    val commentCount: Int = 0,
    val isPinned: Boolean = false
) {
    val hasMedia: Boolean get() = images.isNotEmpty() || !videoUrl.isNullOrBlank()

    val hasVoiceNote: Boolean get() = !audioUrl.isNullOrBlank()

    /** What this post *is*, which decides how the row draws it. */
    val kind: FeedKind
        get() = when {
            signalId != null -> FeedKind.SIGNAL
            hasVoiceNote -> FeedKind.VOICE
            !videoUrl.isNullOrBlank() -> FeedKind.BREAKDOWN
            images.isNotEmpty() -> FeedKind.ANALYSIS
            else -> FeedKind.NOTE
        }

    /** `https://www.tradingview.com/chart/?symbol=XAUUSD`, when a pair is set. */
    val chartUrl: String?
        get() = pair?.uppercase()?.filter { it.isLetterOrDigit() }?.takeIf { it.isNotEmpty() }
            ?.let { "https://www.tradingview.com/chart/?symbol=$it" }

    /** The kinds people actually used, biggest first — at most three fit. */
    val topReactions: List<Reaction>
        get() = reactionCounts.entries
            .filter { it.value > 0 }
            .sortedByDescending { it.value }
            .mapNotNull { entry -> Reaction.from(entry.key) }
            .take(3)
}

/**
 * What a member can leave on a post.
 *
 * A heart alone would be the whole product — X has one and it works — but the
 * client asked for reactions, so there are four and no more. A long list turns
 * a one-tap gesture into a decision, and the fifth option is always the one
 * nobody picks.
 */
/**
 * The four things that end up in this feed.
 *
 * Labelled rather than left to the reader: a member scrolling at the open wants
 * to know in one glance whether this is a trade to act on or a note to read
 * later, and "it has a picture" is not that signal.
 */
enum class FeedKind(val label: String?) {
    /** A trade. The row draws the real ticket. */
    SIGNAL("SIGNAL"),

    /** Tanha talking. The row draws a player with the note's title on it. */
    VOICE("VOICE NOTE"),

    /** A session walkthrough — the video ones. */
    BREAKDOWN("BREAKDOWN"),

    /** A chart with commentary. */
    ANALYSIS("ANALYSIS"),

    /** Words. The majority, and the one kind that needs no label. */
    NOTE(null)
}

enum class Reaction(val stored: String, val glyph: String, val label: String) {
    LOVE("love", "❤️", "Love"),
    FIRE("fire", "🔥", "Fire"),
    CLAP("clap", "👏", "Respect"),
    LAUGH("laugh", "😂", "Funny");

    companion object {
        fun from(value: String?): Reaction? = entries.firstOrNull { it.stored == value }
    }
}

/**
 * A reply.
 *
 * [parentId] is null for a reply to the post and carries a comment id for a
 * reply to a reply — one level deep, like X. Deeper threading reads as a forum
 * and is unreadable on a phone.
 */
data class FeedComment(
    val id: String,
    val authorId: String,
    val authorName: String,
    val authorPhoto: String?,
    val text: String,
    val createdAt: Date = Date(),
    val parentId: String? = null,
    val replyCount: Int = 0
)
