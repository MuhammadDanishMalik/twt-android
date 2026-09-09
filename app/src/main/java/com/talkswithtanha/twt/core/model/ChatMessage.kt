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
    val reactions: Map<String, Int> = emptyMap(),
    val isEdited: Boolean = false,
    val seenBy: List<String> = emptyList(),
    /** True between hitting send and the server echoing the write back. */
    val isPending: Boolean = false
) {
    data class ReplyReference(val senderName: String, val text: String)

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
