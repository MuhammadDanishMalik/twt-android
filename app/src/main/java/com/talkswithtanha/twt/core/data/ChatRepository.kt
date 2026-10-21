package com.talkswithtanha.twt.core.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.talkswithtanha.twt.core.firebase.FirestorePaths.ChatRoomField as R
import com.talkswithtanha.twt.core.firebase.FirestorePaths
import com.talkswithtanha.twt.core.firebase.FirestorePaths.Collection
import com.talkswithtanha.twt.core.firebase.FirestorePaths.MessageField as M
import com.talkswithtanha.twt.core.model.ChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/** Who else has a room open, and who is typing in it. */
data class RoomActivity(val online: Int = 0, val typing: List<String> = emptyList())

interface ChatRepository {
    /** Live window on the most recent messages in a room, oldest first. */
    fun observeMessages(
        roomId: String,
        currentUserId: String,
        limit: Long = DEFAULT_LIMIT
    ): Flow<Snapshot<List<ChatMessage>>>

    /** One page of history older than [before]. Returns an empty list when the
     *  room is exhausted, which is how the caller knows to stop asking. */
    suspend fun fetchOlder(
        roomId: String,
        before: Date,
        currentUserId: String,
        limit: Long = DEFAULT_LIMIT
    ): List<ChatMessage>

    suspend fun send(
        roomId: String,
        text: String,
        senderId: String,
        senderName: String,
        replyTo: ChatMessage.ReplyReference? = null,
        note: ChatMessage.NoteAttachment? = null
    )

    suspend fun edit(roomId: String, messageId: String, text: String)
    /** Deletes the member's own message, for everyone. */
    suspend fun delete(roomId: String, messageId: String)

    /** Who has the room open and who is typing, live. Excludes [currentUserId]. */
    fun observeActivity(roomId: String, currentUserId: String): Flow<RoomActivity>
    /** Marks this member in the room (or out of it, with [present] false). */
    suspend fun setPresence(roomId: String, uid: String, name: String, present: Boolean)
    suspend fun setTyping(roomId: String, uid: String, name: String, typing: Boolean)
    /** Reacts with [emoji]; the same emoji again removes it, another one swaps. */
    suspend fun react(roomId: String, messageId: String, uid: String, emoji: String)
    suspend fun markSeen(roomId: String, messageId: String, uid: String)

    /**
     * Writes the `chatRooms/{roomId}` parent document for a member's own support
     * thread.
     *
     * Firestore subcollections exist perfectly happily without a parent
     * document, so without this the messages are there and the conversation is
     * invisible in Tanha's queue. The rules let a member write only their own
     * `support_{uid}` document.
     */
    suspend fun ensureSupportRoom(
        uid: String,
        userName: String,
        userEmail: String,
        lastMessage: String?
    )

    companion object {
        const val DEFAULT_LIMIT = 60L
    }
}

@Singleton
class FirebaseChatRepository @Inject constructor(
    private val db: FirebaseFirestore
) : ChatRepository {

    private fun messages(roomId: String): CollectionReference =
        db.collection(Collection.CHAT_ROOMS)
            .document(roomId)
            .collection(Collection.MESSAGES)

    override fun observeMessages(
        roomId: String,
        currentUserId: String,
        limit: Long
    ): Flow<Snapshot<List<ChatMessage>>> =
        // Newest-first with a limit, then reversed for display. Ordering
        // ascending and limiting would return the *oldest* messages in the room,
        // which is the opposite of what a chat screen opens to.
        messages(roomId)
            .orderBy(M.TIMESTAMP, Query.Direction.DESCENDING)
            .limit(limit)
            .snapshotFlow()
            .map { snapshot ->
                when (snapshot) {
                    is Snapshot.Failed -> snapshot
                    is Snapshot.Data -> Snapshot.Data(
                        snapshot.value.documents
                            .mapNotNull { map(it, currentUserId) }
                            .reversed()
                    )
                }
            }

    override suspend fun fetchOlder(
        roomId: String,
        before: Date,
        currentUserId: String,
        limit: Long
    ): List<ChatMessage> =
        messages(roomId)
            .orderBy(M.TIMESTAMP, Query.Direction.DESCENDING)
            .whereLessThan(M.TIMESTAMP, Timestamp(before))
            .limit(limit)
            .get()
            .await()
            .documents
            .mapNotNull { map(it, currentUserId) }
            .reversed()

    override suspend fun send(
        roomId: String,
        text: String,
        senderId: String,
        senderName: String,
        replyTo: ChatMessage.ReplyReference?,
        note: ChatMessage.NoteAttachment?
    ) {
        val payload = mutableMapOf<String, Any>(
            M.TEXT to text,
            M.SENDER_ID to senderId,
            M.SENDER_NAME to senderName,
            // Server time, and the rules require exactly this. A client-set
            // timestamp lets someone date a message to the year 3000 and pin it
            // to the top of the room permanently.
            M.TIMESTAMP to FieldValue.serverTimestamp(),
            M.REACTIONS to emptyMap<String, Int>(),
            M.IS_EDITED to false,
            M.IS_PINNED to false
        )
        replyTo?.let {
            payload[M.REPLY_TO] = mapOf(
                M.Reply.SENDER_NAME to it.senderName,
                M.Reply.TEXT to it.text
            )
        }
        note?.let {
            payload[M.NOTE_ATTACHMENT] = mapOf(
                M.Note.NOTE_ID to it.noteId,
                M.Note.VIDEO_ID to it.videoId,
                M.Note.VIDEO_TITLE to it.videoTitle,
                M.Note.TIMESTAMP to it.timestamp,
                M.Note.NOTE_TEXT to it.noteText
            )
        }
        messages(roomId).add(payload).await()
    }

    override suspend fun delete(roomId: String, messageId: String) {
        messages(roomId).document(messageId).delete().await()
    }

    override fun observeActivity(roomId: String, currentUserId: String): Flow<RoomActivity> =
        db.collection(Collection.CHAT_ROOMS).document(roomId).snapshotFlow().map { snapshot ->
            val doc = (snapshot as? Snapshot.Data)?.value
            val now = System.currentTimeMillis()
            fun entries(field: String, timeKey: String, freshMs: Long): List<String> =
                (doc?.get(field) as? Map<*, *>).orEmpty().mapNotNull { (uid, value) ->
                    val entry = value as? Map<*, *> ?: return@mapNotNull null
                    if (uid == currentUserId) return@mapNotNull null
                    val at = (entry[timeKey] as? Timestamp)?.toDate()?.time ?: return@mapNotNull null
                    if (now - at > freshMs) return@mapNotNull null
                    (entry["name"] as? String)?.takeIf { it.isNotBlank() } ?: "Someone"
                }
            RoomActivity(
                // Present = seen in the last two minutes; apps refresh every 30 s.
                online = entries(R.PRESENCE, "lastSeenAt", 2 * 60_000L).size,
                // Typing goes stale fast: a phone that died mid-sentence must not
                // keep "typing…" up for good.
                typing = entries(R.TYPING, "typingAt", 8_000L)
            )
        }

    override suspend fun setPresence(roomId: String, uid: String, name: String, present: Boolean) {
        db.collection(Collection.CHAT_ROOMS).document(roomId).update(
            "${R.PRESENCE}.$uid",
            if (present) mapOf("name" to name, "lastSeenAt" to FieldValue.serverTimestamp()) else FieldValue.delete()
        ).await()
    }

    override suspend fun setTyping(roomId: String, uid: String, name: String, typing: Boolean) {
        db.collection(Collection.CHAT_ROOMS).document(roomId).update(
            "${R.TYPING}.$uid",
            if (typing) mapOf("name" to name, "typingAt" to FieldValue.serverTimestamp()) else FieldValue.delete()
        ).await()
    }

    override suspend fun edit(roomId: String, messageId: String, text: String) {
        // Exactly the keys the edit rule allows. `senderId` is deliberately not
        // sent — nobody may rewrite who said something.
        messages(roomId).document(messageId)
            .update(mapOf(M.TEXT to text, M.IS_EDITED to true))
            .await()
    }

    override suspend fun react(roomId: String, messageId: String, uid: String, emoji: String) {
        // One reaction per member, as in WhatsApp. reactedBy says who chose what;
        // the reactions totals stay in step in the same transaction, and use
        // increments so two people reacting at once both count.
        val ref = messages(roomId).document(messageId)
        db.runTransaction { tx ->
            val snap = tx.get(ref)
            if (!snap.exists()) return@runTransaction
            @Suppress("UNCHECKED_CAST")
            val totals = snap.get(M.REACTIONS) as? Map<String, Any?> ?: emptyMap()
            val previous = (snap.get(M.REACTED_BY) as? Map<*, *>)?.get(uid) as? String
            val updates = mutableMapOf<String, Any>()
            fun decrement(e: String) {
                val count = (totals[e] as? Number)?.toLong() ?: 0L
                updates["${M.REACTIONS}.$e"] = if (count <= 1L) FieldValue.delete() else FieldValue.increment(-1L)
            }
            if (previous == emoji) {
                decrement(emoji)
                updates["${M.REACTED_BY}.$uid"] = FieldValue.delete()
            } else {
                previous?.let(::decrement)
                updates["${M.REACTIONS}.$emoji"] = FieldValue.increment(1L)
                updates["${M.REACTED_BY}.$uid"] = emoji
            }
            tx.update(ref, updates)
        }.await()
    }

    override suspend fun markSeen(roomId: String, messageId: String, uid: String) {
        messages(roomId).document(messageId)
            .update(M.SEEN_BY, FieldValue.arrayUnion(uid))
            .await()
    }

    override suspend fun ensureSupportRoom(
        uid: String,
        userName: String,
        userEmail: String,
        lastMessage: String?
    ) {
        val payload = mutableMapOf<String, Any>(
            R.KIND to R.KIND_SUPPORT,
            R.MEMBER_ID to uid,
            R.MEMBER_NAME to userName,
            R.MEMBER_EMAIL to userEmail,
            R.UPDATED_AT to FieldValue.serverTimestamp()
        )
        lastMessage?.let {
            payload[R.LAST_MESSAGE] = it.take(140)
            payload[R.LAST_MESSAGE_AT] = FieldValue.serverTimestamp()
            payload[R.NEEDS_REPLY] = true
        }
        // Merged, so opening the thread never wipes the unread flag or the last
        // message that put it in Tanha's queue.
        db.collection(Collection.CHAT_ROOMS)
            .document(FirestorePaths.ChatRoom.support(uid))
            .set(payload, SetOptions.merge())
            .await()
    }

    private fun map(
        document: DocumentSnapshot,
        currentUserId: String
    ): ChatMessage? {
        val senderId = document.get(M.SENDER_ID).asNonBlankString().orEmpty()

        val reply = (document.get(M.REPLY_TO) as? Map<*, *>)?.let {
            ChatMessage.ReplyReference(
                senderName = it[M.Reply.SENDER_NAME].asNonBlankString().orEmpty(),
                text = it[M.Reply.TEXT].asNonBlankString().orEmpty()
            )
        }

        // Reactions can come back at zero or negative after people remove them;
        // those should not render as an empty chip.
        val reactions = (document.get(M.REACTIONS) as? Map<*, *>)
            .orEmpty()
            .mapNotNull { (key, value) ->
                val name = key as? String ?: return@mapNotNull null
                val count = value.asLongOrNull()?.toInt() ?: return@mapNotNull null
                if (count > 0) name to count else null
            }
            .toMap()

        @Suppress("UNCHECKED_CAST")
        val seenBy = (document.get(M.SEEN_BY) as? List<*>)?.filterIsInstance<String>().orEmpty()

        // A message being written right now has a null server timestamp for one
        // beat. Falling back to now keeps it at the bottom of the list instead
        // of jumping to 1970 and back.
        val serverTimestamp = document.getTimestamp(M.TIMESTAMP)

        return ChatMessage(
            id = document.id,
            text = document.getString(M.TEXT).orEmpty(),
            senderName = document.get(M.SENDER_NAME).asNonBlankString() ?: "Member",
            senderId = senderId,
            isCurrentUser = senderId.isNotEmpty() && senderId == currentUserId,
            timestamp = serverTimestamp?.toDate() ?: Date(),
            replyTo = reply,
            noteAttachment = (document.get(M.NOTE_ATTACHMENT) as? Map<*, *>)?.let { raw ->
                ChatMessage.NoteAttachment(
                    noteId = raw[M.Note.NOTE_ID] as? String ?: "",
                    videoId = raw[M.Note.VIDEO_ID] as? String ?: "",
                    videoTitle = raw[M.Note.VIDEO_TITLE] as? String ?: "",
                    // iOS writes a Double; a whole number can come back as Long.
                    timestamp = (raw[M.Note.TIMESTAMP] as? Number)?.toDouble() ?: 0.0,
                    noteText = raw[M.Note.NOTE_TEXT] as? String ?: ""
                )
            },
            reactions = reactions,
            isEdited = document.getBoolean(M.IS_EDITED) ?: false,
            seenBy = seenBy,
            myReaction = (document.get(M.REACTED_BY) as? Map<*, *>)?.get(currentUserId) as? String,
            type = ChatMessage.Type.from(document.getString(M.TYPE)),
            audioUrl = document.getString(M.AUDIO_URL)?.takeIf { it.startsWith("http") },
            audioDurationSecs = (document.get(M.AUDIO_DURATION) as? Number)?.toInt() ?: 0,
            isPinned = document.getBoolean(M.IS_PINNED) ?: false,
            targetName = document.getString(M.TARGET_NAME),
            // Per document, not per query. `QuerySnapshot.metadata` is true
            // while *any* write in the query is in flight, so reading it there
            // marks every message in the room as "sending" whenever one of them
            // is -- including messages sent last week by other people.
            isPending = serverTimestamp == null && document.metadata.hasPendingWrites()
        )
    }
}
