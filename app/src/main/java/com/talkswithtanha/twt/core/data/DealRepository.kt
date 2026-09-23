package com.talkswithtanha.twt.core.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.talkswithtanha.twt.core.firebase.FirestorePaths.Collection
import com.talkswithtanha.twt.core.firebase.FirestorePaths.DealField as F
import com.talkswithtanha.twt.core.model.Deal
import com.talkswithtanha.twt.core.model.DealEvent
import com.talkswithtanha.twt.core.model.DealSide
import com.talkswithtanha.twt.core.model.DealStatus
import com.talkswithtanha.twt.core.model.PaymentAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface DealRepository {
    fun observeDeals(userId: String): Flow<Snapshot<List<Deal>>>

    /** Opens a deal. Status is pinned to `awaitingPayment` by the rules. */
    suspend fun createDeal(deal: Deal): String

    /**
     * The member declaring they have paid — the **only** transition this app is
     * allowed to make.
     */
    suspend fun markPaymentSent(dealId: String, reference: String)
}

/**
 * The `deals` collection.
 *
 * Deliberately smaller than the iOS repository, which also has `cancelDeal`,
 * `raiseDispute` and `setReceivingAccount`. Those move a deal to `cancelled`,
 * `disputed` and `releasing`, and the security rules allow a member exactly one
 * transition — `awaitingPayment` to `paymentSent`. The other three are refused
 * server-side for anybody who is not staff, so they are not offered here; a
 * member who needs one asks in their thread, and Tanha does it from the panel.
 */
@Singleton
class FirebaseDealRepository @Inject constructor(
    private val db: FirebaseFirestore
) : DealRepository {

    private val deals get() = db.collection(Collection.DEALS)

    override fun observeDeals(userId: String): Flow<Snapshot<List<Deal>>> =
        deals
            .whereEqualTo(F.USER_ID, userId)
            .orderBy(F.CREATED_AT, Query.Direction.DESCENDING)
            .snapshotFlow()
            .map { snapshot ->
                when (snapshot) {
                    is Snapshot.Failed -> snapshot
                    is Snapshot.Data -> Snapshot.Data(
                        snapshot.value.documents.mapNotNull { map(it) }
                    )
                }
            }

    override suspend fun createDeal(deal: Deal): String {
        val reference = deals.document()
        val payload = mutableMapOf<String, Any>(
            F.USER_ID to deal.userId,
            F.USER_NAME to deal.userName,
            F.REFERENCE to deal.reference,
            F.SIDE to deal.side.stored,
            // Written as whole units for the admin panel, which reads
            // `amountUSD` as a number, and in minor units beside it so nothing
            // downstream has to trust a float.
            F.AMOUNT_USD to deal.amountUsdCents / 100.0,
            F.AMOUNT_USD_CENTS to deal.amountUsdCents,
            F.LOCKED_RATE to deal.lockedRatePaisa / 100.0,
            F.LOCKED_RATE_PAISA to deal.lockedRatePaisa,
            // Pinned by the rules. Sending anything else here is refused.
            F.STATUS to DealStatus.AWAITING_PAYMENT.stored,
            F.CREATED_AT to FieldValue.serverTimestamp(),
            F.EVENTS to listOf(
                eventData(DealStatus.AWAITING_PAYMENT, "Deal opened")
            )
        )
        deal.sellerAccount?.let { payload[F.SELLER_ACCOUNT] = accountData(it) }

        reference.set(payload).await()
        return reference.id
    }

    override suspend fun markPaymentSent(dealId: String, reference: String) {
        deals.document(dealId).update(
            mapOf(
                F.STATUS to DealStatus.PAYMENT_SENT.stored,
                F.PAYMENT_REFERENCE to reference,
                // Appended rather than replaced: the history of a deal is what
                // the argument three weeks later is about.
                F.EVENTS to FieldValue.arrayUnion(
                    eventData(DealStatus.PAYMENT_SENT, "Payment reported — ref $reference")
                )
            )
        ).await()
    }

    private fun eventData(status: DealStatus, note: String): Map<String, Any> = mapOf(
        "id" to UUID.randomUUID().toString(),
        "status" to status.stored,
        "note" to note,
        // A client clock, because `arrayUnion` cannot carry a server timestamp.
        // It is a caption on a history line, not something anything decides on.
        "timestamp" to Timestamp(Date())
    )

    private fun accountData(account: PaymentAccount): Map<String, Any> = mapOf(
        "method" to account.method,
        "accountTitle" to account.accountTitle,
        "accountNumber" to account.accountNumber
    )

    private fun map(document: DocumentSnapshot): Deal? {
        val userId = document.get(F.USER_ID).asNonBlankString() ?: return null

        val cents = document.get(F.AMOUNT_USD_CENTS).asLongOrNull()
            ?: document.get(F.AMOUNT_USD).asDoubleOrNull()?.let { Math.round(it * 100) }
            ?: return null
        val ratePaisa = document.get(F.LOCKED_RATE_PAISA).asLongOrNull()
            ?: document.get(F.LOCKED_RATE).asDoubleOrNull()?.let { Math.round(it * 100) }
            ?: 0L

        return Deal(
            id = document.id,
            userId = userId,
            userName = document.get(F.USER_NAME).asNonBlankString().orEmpty(),
            reference = document.get(F.REFERENCE).asNonBlankString() ?: document.id.take(6).uppercase(),
            side = DealSide.from(document.getString(F.SIDE)),
            amountUsdCents = cents,
            lockedRatePaisa = ratePaisa,
            status = DealStatus.from(document.getString(F.STATUS)),
            sellerAccount = account(document.get(F.SELLER_ACCOUNT)),
            receivingAccount = account(document.get(F.RECEIVING_ACCOUNT)),
            paymentReference = document.get(F.PAYMENT_REFERENCE).asNonBlankString(),
            events = (document.get(F.EVENTS) as? List<*>)
                .orEmpty()
                .filterIsInstance<Map<*, *>>()
                .mapNotNull { raw ->
                    DealEvent(
                        id = raw["id"].asNonBlankString() ?: return@mapNotNull null,
                        status = DealStatus.from(raw["status"] as? String),
                        note = raw["note"].asNonBlankString().orEmpty(),
                        timestamp = (raw["timestamp"] as? Timestamp)?.toDate() ?: Date()
                    )
                }
                .sortedBy { it.timestamp },
            createdAt = document.getTimestamp(F.CREATED_AT)?.toDate() ?: Date()
        )
    }

    private fun account(raw: Any?): PaymentAccount? {
        val map = raw as? Map<*, *> ?: return null
        val number = map["accountNumber"].asNonBlankString() ?: return null
        return PaymentAccount(
            method = map["method"].asNonBlankString().orEmpty(),
            accountTitle = map["accountTitle"].asNonBlankString().orEmpty(),
            accountNumber = number
        )
    }
}
