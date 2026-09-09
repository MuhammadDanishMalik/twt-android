package com.talkswithtanha.twt.core.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.talkswithtanha.twt.core.firebase.FirestorePaths.Collection
import com.talkswithtanha.twt.core.firebase.FirestorePaths.SignalFollowField as F
import com.talkswithtanha.twt.core.model.FollowOutcome
import com.talkswithtanha.twt.core.model.Signal
import com.talkswithtanha.twt.core.model.SignalFollow
import com.talkswithtanha.twt.core.model.SignalType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

interface SignalFollowRepository {
    /** Live view of everything this member has ever followed, newest first. */
    fun observeFollows(userId: String): Flow<Snapshot<List<SignalFollow>>>

    suspend fun follow(signal: Signal, userId: String)

    /** Stops alerts without destroying the record. */
    suspend fun unfollow(signalId: String, userId: String)

    /** Writes the member's own result. */
    suspend fun recordOutcome(
        signalId: String,
        userId: String,
        outcome: FollowOutcome,
        pips: Double?,
        amountMinor: Long?,
        currency: String?,
        note: String?
    )
}

@Singleton
class FirebaseSignalFollowRepository @Inject constructor(
    private val db: FirebaseFirestore
) : SignalFollowRepository {

    private val follows get() = db.collection(Collection.SIGNAL_FOLLOWS)

    override fun observeFollows(userId: String): Flow<Snapshot<List<SignalFollow>>> =
        follows
            .whereEqualTo(F.USER_ID, userId)
            .orderBy(F.FOLLOWED_AT, Query.Direction.DESCENDING)
            .snapshotFlow()
            .map { snapshot ->
                when (snapshot) {
                    is Snapshot.Failed -> snapshot
                    is Snapshot.Data -> Snapshot.Data(
                        snapshot.value.documents.mapNotNull { map(it) }
                    )
                }
            }

    /**
     * Follows a signal, or revives a follow that was previously dropped.
     *
     * ### Why this is two different writes and not one merged set
     *
     * The security rules treat creating and updating a follow as separate
     * things, and the two allow-lists barely overlap:
     *
     *  - **create** must set `isActive: true`, `outcome: "open"` and
     *    `followedAt == request.time`, and requires the signal to exist.
     *  - **update** may only touch `isActive`, `unfollowedAt`, `outcome`,
     *    `resultPips`, `resultAmountMinor`, `resultCurrency`, `note` and
     *    `recordedAt`. The four snapshot fields and `followedAt` are absent from
     *    that list on purpose: they are what the member acted on, and a journal
     *    whose entry price can be revised afterwards proves nothing.
     *
     * iOS does this as a single `setData(merge:)` that always writes
     * `followedAt: serverTimestamp()`. On a *first* follow that is a create and
     * it is fine. On a re-follow it is an update that moves `followedAt`, which
     * is not in the update allow-list — so the whole write is rejected and
     * re-following a signal you had dropped silently fails.
     *
     * A transaction reads the document and picks the right shape. It also makes
     * following twice impossible, which the composite id already implies and
     * this now actually guarantees.
     */
    override suspend fun follow(signal: Signal, userId: String) {
        val reference = follows.document(SignalFollow.makeId(userId, signal.id))

        db.runTransaction { transaction ->
            val existing = transaction.get(reference)

            if (existing.exists()) {
                // Reviving. Only the keys the update rule allows, and the
                // result fields are cleared so a re-followed trade does not
                // carry the previous attempt's win into the member's stats.
                @Suppress("UNCHECKED_CAST")
                transaction.update(
                    reference,
                    mapOf<String, Any?>(
                        F.IS_ACTIVE to true,
                        F.UNFOLLOWED_AT to null,
                        F.OUTCOME to FollowOutcome.OPEN.stored,
                        F.RESULT_PIPS to null,
                        F.RESULT_AMOUNT_MINOR to null,
                        F.RESULT_CURRENCY to null,
                        F.NOTE to null,
                        F.RECORDED_AT to null
                    ) as Map<String, Any>
                )
            } else {
                transaction.set(
                    reference,
                    mapOf(
                        F.USER_ID to userId,
                        F.SIGNAL_ID to signal.id,
                        // The snapshot. Immutable from here on, by rule.
                        F.PAIR to signal.pair,
                        F.TYPE to signal.type.stored,
                        F.ENTRY_PRICE to signal.entryPrice,
                        F.STOP_LOSS to signal.stopLoss,
                        // The rule requires this to equal `request.time`
                        // exactly, so it has to be the server's clock.
                        F.FOLLOWED_AT to FieldValue.serverTimestamp(),
                        F.IS_ACTIVE to true,
                        F.OUTCOME to FollowOutcome.OPEN.stored
                    )
                )
            }
            null
        }.await()
    }

    override suspend fun unfollow(signalId: String, userId: String) {
        // Not a delete. The record is the member's history, and `isActive` is
        // the single flag the notification fan-out reads — flipping it here is
        // what makes "no more alerts for this one" true server-side.
        follows.document(SignalFollow.makeId(userId, signalId))
            .update(
                mapOf(
                    F.IS_ACTIVE to false,
                    F.UNFOLLOWED_AT to FieldValue.serverTimestamp()
                )
            )
            .await()
    }

    override suspend fun recordOutcome(
        signalId: String,
        userId: String,
        outcome: FollowOutcome,
        pips: Double?,
        amountMinor: Long?,
        currency: String?,
        note: String?
    ) {
        // Nulls are written rather than omitted, so correcting a number back to
        // blank actually removes it instead of leaving the old value in place.
        val payload = mapOf<String, Any?>(
            F.OUTCOME to outcome.stored,
            F.RECORDED_AT to FieldValue.serverTimestamp(),
            // Recording a result closes the position, so the alerts stop too.
            // Somebody who has already written down what they made does not want
            // to be told an hour later that the trade moved again.
            F.IS_ACTIVE to false,
            F.RESULT_PIPS to pips,
            F.RESULT_AMOUNT_MINOR to amountMinor,
            F.RESULT_CURRENCY to currency?.takeIf { it.isNotBlank() },
            F.NOTE to note?.takeIf { it.isNotBlank() }
        )

        @Suppress("UNCHECKED_CAST")
        follows.document(SignalFollow.makeId(userId, signalId))
            .update(payload as Map<String, Any>)
            .await()
    }

    private fun map(document: DocumentSnapshot): SignalFollow? {
        val userId = document.get(F.USER_ID).asNonBlankString() ?: return null
        val signalId = document.get(F.SIGNAL_ID).asNonBlankString() ?: return null

        return SignalFollow(
            id = document.id,
            userId = userId,
            signalId = signalId,
            pair = document.get(F.PAIR).asNonBlankString() ?: "—",
            type = SignalType.from(document.getString(F.TYPE)),
            entryPrice = document.get(F.ENTRY_PRICE).asNonBlankString() ?: "—",
            stopLoss = document.get(F.STOP_LOSS).asNonBlankString() ?: "—",
            // A server timestamp reads as null for the instant between the local
            // write and the server's echo. Falling back to now keeps the row
            // from jumping to the bottom of a date-sorted list and back.
            followedAt = document.getTimestamp(F.FOLLOWED_AT)?.toDate() ?: Date(),
            isActive = document.getBoolean(F.IS_ACTIVE) ?: false,
            unfollowedAt = document.getTimestamp(F.UNFOLLOWED_AT)?.toDate(),
            outcome = FollowOutcome.from(document.getString(F.OUTCOME)),
            resultPips = document.get(F.RESULT_PIPS).asDoubleOrNull(),
            resultAmountMinor = document.get(F.RESULT_AMOUNT_MINOR).asLongOrNull(),
            resultCurrency = document.get(F.RESULT_CURRENCY).asNonBlankString(),
            note = document.get(F.NOTE).asNonBlankString(),
            recordedAt = document.getTimestamp(F.RECORDED_AT)?.toDate()
        )
    }
}
