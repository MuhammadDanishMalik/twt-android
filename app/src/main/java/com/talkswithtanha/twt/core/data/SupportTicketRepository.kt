package com.talkswithtanha.twt.core.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.talkswithtanha.twt.core.firebase.FirestorePaths.Collection
import com.talkswithtanha.twt.core.firebase.FirestorePaths.TicketField as T
import com.talkswithtanha.twt.core.firebase.FirestorePaths.TicketMessageField as M
import com.talkswithtanha.twt.core.model.SupportTicket
import com.talkswithtanha.twt.core.model.TicketMessage
import com.talkswithtanha.twt.core.model.TicketPriority
import com.talkswithtanha.twt.core.model.TicketStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads and writes `supportTickets`, the collection the admin panel works as a
 * queue.
 *
 * Every write here has to satisfy firestore.rules, where a member may touch only
 * their own ticket and only a short list of fields on it — so the writes are
 * narrow on purpose. A convenience like "update the whole document" is rejected
 * at the server and surfaces as a permission error with no clue which field
 * caused it.
 *
 * [observeQueue] is the staff half: the unfiltered collection, which the rules
 * allow only to an account holding the `tickets` area. Starting it for an
 * ordinary member produces a permission error on every launch, so callers check
 * `User.can(StaffArea.TICKETS)` first.
 */
interface SupportTicketRepository {
    fun observeMyTickets(userId: String): Flow<Snapshot<List<SupportTicket>>>
    fun observeQueue(): Flow<Snapshot<List<SupportTicket>>>
    fun observeMessages(ticketId: String): Flow<Snapshot<List<TicketMessage>>>

    /** Returns the new ticket's id. */
    suspend fun create(
        subject: String,
        category: String,
        firstMessage: String,
        memberId: String,
        memberName: String,
        memberEmail: String
    ): String

    suspend fun send(
        ticketId: String,
        text: String,
        senderId: String,
        senderName: String,
        isStaff: Boolean,
        status: TicketStatus
    )

    suspend fun setStatus(ticketId: String, status: TicketStatus, asStaff: Boolean)

    /** Clears the member's unread dot when they open the thread. */
    suspend fun markRead(ticketId: String)
}

@Singleton
class FirebaseSupportTicketRepository @Inject constructor(
    private val db: FirebaseFirestore
) : SupportTicketRepository {

    private val tickets get() = db.collection(Collection.SUPPORT_TICKETS)

    private fun messages(ticketId: String) =
        tickets.document(ticketId).collection(Collection.MESSAGES)

    override fun observeMyTickets(userId: String): Flow<Snapshot<List<SupportTicket>>> =
        tickets
            .whereEqualTo(T.MEMBER_ID, userId)
            // Ordered by `updatedAt`, not by when it was opened: a three-week-old
            // ticket somebody just answered is the one the member is thinking
            // about. Needs the composite index in firestore.indexes.json.
            .orderBy(T.UPDATED_AT, Query.Direction.DESCENDING)
            .limit(50)
            .snapshotFlow()
            .map { snapshot ->
                when (snapshot) {
                    is Snapshot.Failed -> snapshot
                    is Snapshot.Data -> Snapshot.Data(snapshot.value.documents.mapNotNull { map(it) })
                }
            }

    override fun observeQueue(): Flow<Snapshot<List<SupportTicket>>> =
        tickets
            .orderBy(T.UPDATED_AT, Query.Direction.DESCENDING)
            .limit(100)
            .snapshotFlow()
            .map { snapshot ->
                when (snapshot) {
                    is Snapshot.Failed -> snapshot
                    is Snapshot.Data -> Snapshot.Data(snapshot.value.documents.mapNotNull { map(it) })
                }
            }

    override fun observeMessages(ticketId: String): Flow<Snapshot<List<TicketMessage>>> =
        messages(ticketId)
            .orderBy(M.TIMESTAMP)
            .limit(200)
            .snapshotFlow()
            .map { snapshot ->
                when (snapshot) {
                    is Snapshot.Failed -> snapshot
                    is Snapshot.Data -> Snapshot.Data(
                        snapshot.value.documents.mapNotNull { document ->
                            val text = document.getString(M.TEXT) ?: return@mapNotNull null
                            TicketMessage(
                                id = document.id,
                                text = text,
                                senderId = document.getString(M.SENDER_ID).orEmpty(),
                                senderName = document.getString(M.SENDER_NAME).orEmpty(),
                                isStaff = document.getBoolean(M.IS_STAFF) ?: false,
                                // A message written a moment ago has no server
                                // timestamp yet; dating it now keeps it at the
                                // bottom of the thread instead of jumping to 1970.
                                timestamp = document.getDate(M.TIMESTAMP) ?: Date()
                            )
                        }
                    )
                }
            }

    override suspend fun create(
        subject: String,
        category: String,
        firstMessage: String,
        memberId: String,
        memberName: String,
        memberEmail: String
    ): String {
        val document = tickets.document()

        document.set(
            mapOf(
                T.SUBJECT to subject.take(120),
                T.CATEGORY to category,
                T.STATUS to TicketStatus.OPEN.stored,
                T.PRIORITY to TicketPriority.NORMAL.stored,
                T.MEMBER_ID to memberId,
                T.MEMBER_NAME to memberName,
                T.MEMBER_EMAIL to memberEmail,
                T.OPENED_BY_STAFF to false,
                T.CREATED_AT to FieldValue.serverTimestamp(),
                T.UPDATED_AT to FieldValue.serverTimestamp(),
                T.LAST_MESSAGE to firstMessage.take(160),
                T.LAST_MESSAGE_AT to FieldValue.serverTimestamp(),
                T.LAST_MESSAGE_FROM_STAFF to false,
                // The whole point of raising it: it is now waiting on us.
                T.NEEDS_REPLY to true,
                T.UNREAD_FOR_MEMBER to false,
                T.MESSAGE_COUNT to 1,
                T.FOLLOW_UP_COUNT to 0
            )
        ).await()

        messages(document.id).add(
            mapOf(
                M.TEXT to firstMessage,
                M.SENDER_ID to memberId,
                M.SENDER_NAME to memberName,
                M.IS_STAFF to false,
                M.TIMESTAMP to FieldValue.serverTimestamp()
            )
        ).await()

        return document.id
    }

    override suspend fun send(
        ticketId: String,
        text: String,
        senderId: String,
        senderName: String,
        isStaff: Boolean,
        status: TicketStatus
    ) {
        messages(ticketId).add(
            mapOf(
                M.TEXT to text,
                M.SENDER_ID to senderId,
                M.SENDER_NAME to senderName,
                M.IS_STAFF to isStaff,
                M.TIMESTAMP to FieldValue.serverTimestamp()
            )
        ).await()

        val payload = mutableMapOf<String, Any>(
            T.LAST_MESSAGE to text.take(160),
            T.LAST_MESSAGE_AT to FieldValue.serverTimestamp(),
            T.LAST_MESSAGE_FROM_STAFF to isStaff,
            T.NEEDS_REPLY to !isStaff,
            T.UNREAD_FOR_MEMBER to isStaff,
            T.MESSAGE_COUNT to FieldValue.increment(1),
            T.UPDATED_AT to FieldValue.serverTimestamp(),
            T.STATUS to status.stored
        )
        if (isStaff) payload[T.ASSIGNED_TO_NAME] = senderName

        tickets.document(ticketId).update(payload).await()
    }

    override suspend fun setStatus(ticketId: String, status: TicketStatus, asStaff: Boolean) {
        val payload = mutableMapOf<String, Any>(
            T.STATUS to status.stored,
            T.UPDATED_AT to FieldValue.serverTimestamp()
        )
        if (asStaff) {
            payload[T.NEEDS_REPLY] = false
            if (status.isFinished) payload[T.RESOLVED_AT] = FieldValue.serverTimestamp()
        }
        tickets.document(ticketId).update(payload).await()
    }

    override suspend fun markRead(ticketId: String) {
        tickets.document(ticketId).update(
            mapOf(
                T.UNREAD_FOR_MEMBER to false,
                T.UPDATED_AT to FieldValue.serverTimestamp()
            )
        ).await()
    }

    private fun map(snapshot: DocumentSnapshot): SupportTicket? {
        val subject = snapshot.getString(T.SUBJECT) ?: return null
        val created = snapshot.getDate(T.CREATED_AT) ?: Date()
        return SupportTicket(
            id = snapshot.id,
            subject = subject,
            category = snapshot.getString(T.CATEGORY) ?: "Other",
            status = TicketStatus.from(snapshot.getString(T.STATUS)),
            priority = TicketPriority.from(snapshot.getString(T.PRIORITY)),
            memberId = snapshot.getString(T.MEMBER_ID).orEmpty(),
            memberName = snapshot.getString(T.MEMBER_NAME) ?: "Member",
            memberEmail = snapshot.getString(T.MEMBER_EMAIL).orEmpty(),
            openedByStaff = snapshot.getBoolean(T.OPENED_BY_STAFF) ?: false,
            createdAt = created,
            updatedAt = snapshot.getDate(T.UPDATED_AT) ?: created,
            lastMessage = snapshot.getString(T.LAST_MESSAGE).orEmpty(),
            lastMessageAt = snapshot.getDate(T.LAST_MESSAGE_AT),
            lastMessageFromStaff = snapshot.getBoolean(T.LAST_MESSAGE_FROM_STAFF) ?: false,
            needsReply = snapshot.getBoolean(T.NEEDS_REPLY) ?: false,
            unreadForMember = snapshot.getBoolean(T.UNREAD_FOR_MEMBER) ?: false,
            messageCount = (snapshot.getLong(T.MESSAGE_COUNT) ?: 0L).toInt()
        )
    }
}
