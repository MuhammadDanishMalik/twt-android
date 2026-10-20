package com.talkswithtanha.twt.core.model

import com.talkswithtanha.twt.core.firebase.FirestorePaths
import java.util.Date

data class ChatMessage(
    /** The Firestore document id. */
    val id: String,
    val text: String,
    /**
     * Denormalised onto the message on purpose. The security rules keep the
     * `users` collection private, so a member cannot look up who wrote something
     * — the name has to travel with the message.
     */
    val senderName: String,
    /** The author's uid. What the rules check before allowing an edit, and what
     *  tells the app whose bubble is whose. */
    val senderId: String,
    val isCurrentUser: Boolean,
    val timestamp: Date,
    val replyTo: ReplyReference? = null,
    /** A lesson note shared into the chat. */
    val noteAttachment: NoteAttachment? = null,
    val reactions: Map<String, Int> = emptyMap(),
    val isEdited: Boolean = false,
    val seenBy: List<String> = emptyList(),
    /** The emoji this member reacted with, if any. One per member per message. */
    val myReaction: String? = null,
    /** True between hitting send and the server echoing the write back. */
    val isPending: Boolean = false
) {
    data class ReplyReference(val senderName: String, val text: String)

    /**
     * A copy of a saved lesson note, taken at the moment it was shared. A copy
     * rather than a reference because notes live only on the sender's phone.
     */
    data class NoteAttachment(
        val noteId: String,
        /** The YouTube id of the lesson. */
        val videoId: String,
        val videoTitle: String,
        /** Seconds into the video. */
        val timestamp: Double,
        val noteText: String
    ) {
        val formattedTimestamp: String get() = formatVideoTime(timestamp)
    }

    /** Whether this message can still be edited (within five minutes). */
    val canEdit: Boolean
        get() = isCurrentUser && (Date().time - timestamp.time) < 5 * 60 * 1000
}

/**
 * Which room a screen is showing. Rooms are addressed by id in the security
 * rules, so the id is the thing that matters and the display name follows from
 * it rather than the other way round.
 */
sealed class ChatRoom(val id: String, val displayName: String) {

    data object Community : ChatRoom(FirestorePaths.ChatRoom.COMMUNITY, "Community")

    /**
     * Gated on *reading*, not just posting. An account whose code has lapsed
     * keeps the general room and loses this one.
     */
    data object Premium : ChatRoom(FirestorePaths.ChatRoom.PREMIUM, "Premium")

    /** The private thread between one member and Tanha. */
    class Support(uid: String) :
        ChatRoom(FirestorePaths.ChatRoom.support(uid), "Talks with Tanha")

    val isSupport: Boolean get() = id.startsWith(FirestorePaths.ChatRoom.SUPPORT_PREFIX)
    val isPremium: Boolean get() = id == FirestorePaths.ChatRoom.PREMIUM
}

/** 125.4 → "02:05", the way both apps print a moment in a video. */
fun formatVideoTime(seconds: Double): String {
    val total = seconds.toInt().coerceAtLeast(0)
    return "%02d:%02d".format(total / 60, total % 60)
}
