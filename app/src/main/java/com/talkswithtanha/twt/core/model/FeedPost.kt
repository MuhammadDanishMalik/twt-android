package com.talkswithtanha.twt.core.model

import java.util.Date

/**
 * One thing Tanha has put in front of the community: a setup, a note, a
 * chart, a clip, a voice note.
 *
 * Only he posts — the security rules enforce that, not the app — and everybody
 * else reads, reacts and asks. It is his desk rather than a forum. The shape is
 * the website's `FeedPost` (twt-web/lib/feed-repo.ts); keep the two in step.
 */
data class FeedPost(
    val id: String,
    val authorId: String,
    val authorName: String,
    val authorPhoto: String?,
    /** Plain text. [bodyMd] carries the formatted version when there is one. */
    val text: String,
    val images: List<String> = emptyList(),
    val videoUrl: String? = null,
    /** The first of [pairs], kept for older posts and older readers. */
    val pair: String? = null,
    /** The signal a setup post carries; the card draws the live trade. */
    val signalId: String? = null,
    val audioUrl: String? = null,
    val audioTitle: String? = null,
    val audioDurationSec: Int = 0,
    val createdAt: Date = Date(),
    val editedAt: Date? = null,
    val reactionCount: Int = 0,
    val reactionCounts: Map<String, Int> = emptyMap(),
    val commentCount: Int = 0,
    val isPinned: Boolean = false,
    /** Set by the composer; older posts are typed from what they carry. */
    val type: PostType = PostType.NOTE,
    /** Rich text: **bold**, # headings, - bullets. */
    val bodyMd: String? = null,
    /** Every pair the post is about. */
    val pairs: List<String> = emptyList(),
    val commentsEnabled: Boolean = true,
    /** One line per image describing the chart. */
    val imageAlts: List<String> = emptyList(),
    /** What the voice note says, for reading instead of listening. */
    val transcript: String? = null,
    /** A scheduled post's go-live time; it stays out of the feed until then. */
    val publishAt: Date? = null,
    /** A screen recording's chapters, in seconds from the start. */
    val chapters: List<VideoChapter> = emptyList(),
    val videoStart: Int = 0,
    val link: LinkCard? = null,
    val viewCount: Int = 0,
    val saveCount: Int = 0
) {
    val body: String get() = bodyMd?.takeIf { it.isNotBlank() } ?: text

    val hasVoiceNote: Boolean get() = !audioUrl.isNullOrBlank()

    /** False for a post scheduled for later — it is not shown until its time. */
    fun isLive(now: Long = System.currentTimeMillis()): Boolean = publishAt == null || publishAt.time <= now

    /** `https://www.tradingview.com/chart/?symbol=XAUUSD`, when a pair is set. */
    val chartUrl: String?
        get() = (pairs.firstOrNull() ?: pair)?.uppercase()?.filter { it.isLetterOrDigit() }?.takeIf { it.isNotEmpty() }
            ?.let { "https://www.tradingview.com/chart/?symbol=$it" }
}

/** What a post is, which decides its card. The stored values are the website's. */
enum class PostType(val stored: String, val label: String) {
    SETUP("setup", "Setup"),
    NOTE("analysis", "Note"),
    CHART("chart", "Chart"),
    VIDEO("video", "Video"),
    VOICE("voice", "Voice note"),
    ALERT("alert", "Alert");

    companion object {
        fun from(value: String?): PostType? = entries.firstOrNull { it.stored == value }
    }
}

data class VideoChapter(val at: Int, val label: String)

/** A shared link, unfurled by the panel: picture, site and title. */
data class LinkCard(
    val url: String,
    val site: String,
    val title: String,
    val description: String?,
    val image: String?
)

/**
 * What a trader actually does with a post — found it useful, took the trade,
 * is watching the level — rather than how it made them feel. "124 members
 * took this" is worth more to the next reader than 124 hearts.
 *
 * The old kinds (love, fire, clap, laugh) still sit in older posts' counters;
 * they are simply not drawn any more.
 */
enum class Reaction(val stored: String, val glyph: String, val label: String, val setupsOnly: Boolean = false) {
    USEFUL("useful", "👍", "Useful"),
    TOOK("took", "🎯", "Took it", setupsOnly = true),
    WATCHING("watching", "👀", "Watching");

    companion object {
        fun from(value: String?): Reaction? = entries.firstOrNull { it.stored == value }
    }
}

/**
 * A question or an answer under a post.
 *
 * [parentId] is null for a question on the post and carries a comment id for a
 * reply to it — one level deep. [isAuthorReply] is Tanha's answer; only staff
 * can set it, which the rules check.
 */
data class FeedComment(
    val id: String,
    val authorId: String,
    val authorName: String,
    val authorPhoto: String?,
    val text: String,
    val createdAt: Date = Date(),
    val parentId: String? = null,
    val replyCount: Int = 0,
    val isAuthorReply: Boolean = false,
    val audioUrl: String? = null
)

/** One change the desk made to a live trade, newest first on the setup card. */
data class SignalUpdate(
    val id: String,
    val type: String,
    val note: String,
    val price: String?,
    val at: Date,
    val audioUrl: String?,
    val audioDurationSec: Int,
    val transcript: String?
) {
    val label: String
        get() = when (type) {
            "opened" -> "Trade opened"
            "tp_hit" -> "Target hit"
            "sl_to_be" -> "Stop to breakeven"
            "partial" -> "Partial close"
            "closed" -> "Closed"
            "stopped" -> "Stopped out"
            "cancelled" -> "Cancelled"
            else -> "Update"
        }
}

/** The member's view of the feed: what they filtered on. */
data class FeedFilter(
    val type: FilterType = FilterType.ALL,
    val pair: String? = null,
    val unreadOnly: Boolean = false
)

enum class FilterType(val stored: String, val label: String) {
    ALL("all", "All"),
    SETUP("setup", "Setups"),
    ANALYSIS("analysis", "Analysis"),
    VIDEO("video", "Videos"),
    VOICE("voice", "Voice"),
    SAVED("saved", "Saved");

    companion object {
        fun from(value: String?): FilterType = entries.firstOrNull { it.stored == value } ?: ALL
    }
}

/**
 * A member's own feed state, kept in `users/{uid}/feedState/main` so it is the
 * same on the phone and the website: what they saved and into which
 * collections, when they last looked, and the filter they left it on.
 */
data class FeedState(
    /** postId → the collections it is saved in; saved with none is an empty list. */
    val saved: Map<String, List<String>> = emptyMap(),
    val lastSeenAt: Long? = null,
    val filter: FeedFilter = FeedFilter(),
    val collections: List<String> = DEFAULT_COLLECTIONS
) {
    companion object {
        val DEFAULT_COLLECTIONS = listOf("Study later", "Setups I like")
    }
}
