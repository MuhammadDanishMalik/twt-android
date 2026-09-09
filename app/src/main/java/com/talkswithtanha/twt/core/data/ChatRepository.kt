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
        replyTo: ChatMessage.ReplyReference? = null
    )

    suspend fun edit(roomId: String, messageId: String, text: String)
    suspend fun toggleReaction(roomId: String, messageId: String, emoji: String, add: Boolean)
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
        replyTo: ChatMessage.ReplyReference?
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
        messages(roomId).add(payload).await()
    }

    override suspend fun edit(roomId: String, messageId: String, text: String) {
        // Exactly the keys the edit rule allows. `senderId` is deliberately not
        // sent — nobody may rewrite who said something.
        messages(roomId).document(messageId)
            .update(mapOf(M.TEXT to text, M.IS_EDITED to true))
            .await()
    }

    override suspend fun toggleReaction(
        roomId: String,
        messageId: String,
        emoji: String,
        add: Boolean
    ) {
        // Increment rather than write a total. Two people reacting at once would
        // otherwise each write "1" and one of them would vanish.
        messages(roomId).document(messageId)
            .update("${M.REACTIONS}.$emoji", FieldValue.increment(if (add) 1L else -1L))
            .await()
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
            reactions = reactions,
            isEdited = document.getBoolean(M.IS_EDITED) ?: false,
            seenBy = seenBy,
            // Per document, not per query. `QuerySnapshot.metadata` is true
            // while *any* write in the query is in flight, so reading it there
            // marks every message in the room as "sending" whenever one of them
            // is -- including messages sent last week by other people.
            isPending = serverTimestamp == null && document.metadata.hasPendingWrites()
        )
    }
}
