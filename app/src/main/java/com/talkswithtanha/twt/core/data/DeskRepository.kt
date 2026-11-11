package com.talkswithtanha.twt.core.data

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.talkswithtanha.twt.BuildConfig
import com.talkswithtanha.twt.core.firebase.FirestorePaths
import com.talkswithtanha.twt.core.model.Deal
import com.talkswithtanha.twt.core.model.DealStatus
import com.talkswithtanha.twt.core.model.deskDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** One member's thread with the exchange desk, as the desk's Chats list shows it. */
data class DeskThread(
    val roomId: String,
    val memberId: String,
    val memberName: String,
    val lastMessage: String,
    val lastMessageAt: Date?,
    /** The member wrote and nobody on the desk has opened it since. */
    val unseen: Boolean
)

/** A desk action the server refused, in words for the screen. */
class DeskRefusal(message: String) : Exception(message)

/**
 * The exchange desk, from the phone: exactly what the panel's Deals and Chats
 * pages do, writing the same fields, so a deal accepted here shows accepted
 * there and the other way round.
 *
 * Today's fund is kept on `config/exchangeRate` (`dailyFundUsd`, `fundDate`,
 * `fundUsedUsd`): accepting takes the deal's dollars off it, cancelling a deal
 * that was counted gives them back — the same accounting as the panel's
 * `deals-repo.ts` and the server's deal watcher.
 */
@Singleton
class DeskRepository @Inject constructor(
    private val db: FirebaseFirestore,
    private val uploader: com.talkswithtanha.twt.core.images.CloudinaryUploader
) {
    private val deals get() = db.collection(FirestorePaths.Collection.DEALS)
    private val rateDoc get() = db.collection(FirestorePaths.Collection.CONFIG).document("exchangeRate")
    private val http = OkHttpClient()

    private fun event(status: DealStatus, note: String) = FieldValue.arrayUnion(
        mapOf(
            "id" to UUID.randomUUID().toString(),
            "status" to status.stored,
            "note" to note,
            "timestamp" to Timestamp(Date())
        )
    )

    /**
     * "Yes, go ahead": the member is shown where to pay — [account], the one
     * the desk chose for this deal — and the dollars come off today's fund.
     * With [saveAccount], it joins the desk's list for next time.
     */
    suspend fun accept(dealId: String, by: String, account: com.talkswithtanha.twt.core.model.PaymentAccount? = null, saveAccount: Boolean = false) {
        val dealRef = deals.document(dealId)
        db.runTransaction { tx ->
            val deal = tx.get(dealRef)
            val cfg = tx.get(rateDoc)
            if (deal.getString("status") != DealStatus.AWAITING_APPROVAL.stored) {
                throw DeskRefusal("This deal is no longer waiting for approval.")
            }
            val today = deskDay()
            val fund = cfg.getLong("dailyFundUsd") ?: 0L
            val counted = cfg.getString("fundDate") == today && fund > 0
            val amount = deal.getDouble("amountUSD") ?: 0.0
            val cfgChanges = mutableMapOf<String, Any>()
            if (counted) {
                val used = cfg.getDouble("fundUsedUsd") ?: 0.0
                if (used + amount > fund) {
                    throw DeskRefusal("Only ${"%,d".format((fund - used).toLong().coerceAtLeast(0))} USDT of today's fund is left.")
                }
                cfgChanges["fundUsedUsd"] = used + amount
            }
            if (cfgChanges.isNotEmpty()) tx.update(rateDoc, cfgChanges)
            tx.update(
                dealRef,
                mapOf(
                    "status" to DealStatus.AWAITING_PAYMENT.stored,
                    "approvedAt" to Timestamp(Date()),
                    "approvedBy" to by,
                    "fundDay" to if (counted) today else null,
                    "events" to event(DealStatus.AWAITING_PAYMENT, "Approved by $by. Payment details shown to the member")
                ) + (account?.let {
                    mapOf("sellerAccount" to mapOf("method" to it.method, "accountTitle" to it.accountTitle, "accountNumber" to it.accountNumber))
                } ?: emptyMap())
            )
        }.await()
    }

    /**
     * This dealer's own accounts, kept apart from the desk's shared list:
     * `config/deskAccounts_{uid}`. Only he is offered them when accepting.
     */
    private fun myAccountsDoc(uid: String) = db.collection(FirestorePaths.Collection.CONFIG).document("deskAccounts_$uid")

    fun observeMyAccounts(uid: String): Flow<List<com.talkswithtanha.twt.core.model.PaymentAccount>> =
        myAccountsDoc(uid).snapshotFlow().map { snap ->
            val d = (snap as? Snapshot.Data)?.value ?: return@map emptyList()
            (d.get("accounts") as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().mapNotNull { m ->
                val number = (m["accountNumber"] as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                com.talkswithtanha.twt.core.model.PaymentAccount(
                    method = (m["method"] as? String).orEmpty(),
                    accountTitle = (m["accountTitle"] as? String).orEmpty(),
                    accountNumber = number
                )
            }.reversed() // newest first
        }

    suspend fun saveMyAccount(uid: String, account: com.talkswithtanha.twt.core.model.PaymentAccount) {
        myAccountsDoc(uid).set(
            mapOf(
                "accounts" to FieldValue.arrayUnion(
                    mapOf("method" to account.method, "accountTitle" to account.accountTitle, "accountNumber" to account.accountNumber)
                ),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).await()
    }

    /** Points a member who has not paid yet at a different account. */
    suspend fun changePaymentAccount(dealId: String, account: com.talkswithtanha.twt.core.model.PaymentAccount, by: String) {
        deals.document(dealId).update(
            mapOf(
                "sellerAccount" to mapOf("method" to account.method, "accountTitle" to account.accountTitle, "accountNumber" to account.accountNumber),
                "events" to event(DealStatus.AWAITING_PAYMENT, "$by changed where to pay: ${account.method} ${account.accountNumber.takeLast(4)}")
            )
        ).await()
    }

    /** "Not this one", before anything was shown or paid. */
    suspend fun decline(dealId: String, reason: String) {
        deals.document(dealId).update(
            mapOf(
                "status" to DealStatus.CANCELLED.stored,
                "cancelReason" to "rejected",
                "events" to event(DealStatus.CANCELLED, "Not accepted by the desk — $reason")
            )
        ).await()
    }

    /** Cancels, giving the deal's dollars back to today's fund if they were counted. */
    suspend fun cancel(dealId: String, reason: String) {
        val dealRef = deals.document(dealId)
        db.runTransaction { tx ->
            val deal = tx.get(dealRef)
            val cfg = tx.get(rateDoc)
            val fundDay = deal.getString("fundDay")
            if (fundDay != null && fundDay == cfg.getString("fundDate")) {
                val used = cfg.getDouble("fundUsedUsd") ?: 0.0
                tx.update(rateDoc, "fundUsedUsd", (used - (deal.getDouble("amountUSD") ?: 0.0)).coerceAtLeast(0.0))
            }
            tx.update(
                dealRef,
                mapOf(
                    "status" to DealStatus.CANCELLED.stored,
                    "fundDay" to null,
                    "events" to event(DealStatus.CANCELLED, "Cancelled by Trade with Tanha — $reason")
                )
            )
        }.await()
    }

    /**
     * "The money arrived", with the desk's proof: a screenshot of the bank or
     * wallet message on the dealer's own phone. The member sees it too, then
     * is asked where to send theirs.
     */
    suspend fun confirmPayment(dealId: String, by: String, proof: ByteArray) {
        val url = uploader.uploadImageOrThrow(proof)
        deals.document(dealId).update(
            mapOf(
                "status" to DealStatus.PAYMENT_APPROVED.stored,
                "receiptProofUrl" to url,
                "events" to event(DealStatus.PAYMENT_APPROVED, "Payment confirmed by $by")
            )
        ).await()
    }

    /** "I have sent their funds", with the transaction id or hash they went with. */
    suspend fun release(dealId: String, reference: String, by: String, proof: ByteArray) {
        val url = uploader.uploadImageOrThrow(proof)
        deals.document(dealId).update(
            mapOf(
                "status" to DealStatus.COMPLETED.stored,
                "releaseProofUrl" to url,
                "releaseReference" to reference,
                "releasedBy" to by,
                "releasedAt" to Timestamp(Date()),
                "events" to event(DealStatus.COMPLETED, "Funds sent by $by · ref $reference")
            )
        ).await()
    }

    suspend fun dispute(dealId: String, reason: String) {
        deals.document(dealId).update(
            mapOf(
                "status" to DealStatus.DISPUTED.stored,
                "events" to event(DealStatus.DISPUTED, "Flagged for review — $reason")
            )
        ).await()
    }

    /** Opens or closes the desk for new deals. Open deals carry on either way. */
    suspend fun setTakingDeals(on: Boolean, by: String) {
        rateDoc.set(
            mapOf("isAcceptingDeals" to on, "updatedBy" to by, "updatedAt" to FieldValue.serverTimestamp()),
            SetOptions.merge()
        ).await()
    }

    /** Today's fund. Set again the same day, what was already used is kept. */
    suspend fun setDailyFund(amountUsd: Long, by: String) {
        val today = deskDay()
        db.runTransaction { tx ->
            val cfg = tx.get(rateDoc)
            val used = if (cfg.getString("fundDate") == today) cfg.getDouble("fundUsedUsd") ?: 0.0 else 0.0
            tx.set(
                rateDoc,
                mapOf(
                    "dailyFundUsd" to amountUsd.coerceAtLeast(0),
                    "fundDate" to today,
                    "fundUsedUsd" to used,
                    "updatedBy" to by,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
        }.await()
    }

    /**
     * Today's buy and sell rate, in paisa: the same fields and the same
     * 24-point history the panel's rate page writes, so the app's sparkline
     * and the members' screens move the moment it is saved.
     */
    suspend fun setRates(buyPaisa: Long, sellPaisa: Long, by: String) {
        db.runTransaction { tx ->
            val cfg = tx.get(rateDoc)
            val previous = cfg.getLong("pkrPerUsdPaisa") ?: 0L
            val history = (cfg.get("history") as? List<*>).orEmpty().mapNotNull { (it as? Number)?.toLong() } + buyPaisa
            tx.set(
                rateDoc,
                mapOf(
                    "pkrPerUsdPaisa" to buyPaisa,
                    "sellPkrPerUsdPaisa" to sellPaisa,
                    "previousPkrPerUsdPaisa" to if (previous > 0) previous else buyPaisa,
                    "history" to history.takeLast(24),
                    "updatedBy" to by,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
        }.await()
    }

    /** Every exchange chat, the most recent first. Sorted here, so no index is needed. */
    fun observeThreads(): Flow<Snapshot<List<DeskThread>>> =
        db.collection(FirestorePaths.Collection.CHAT_ROOMS)
            .whereEqualTo("kind", "dealer")
            .snapshotFlow()
            .map { snapshot ->
                when (snapshot) {
                    is Snapshot.Failed -> snapshot
                    is Snapshot.Data -> Snapshot.Data(
                        snapshot.value.documents.map { d ->
                            val lastAt = d.getTimestamp("lastMessageAt")?.toDate()
                            val seenAt = d.getTimestamp("staffSeenAt")?.toDate()
                            val needsReply = d.getBoolean("needsReply") == true
                            DeskThread(
                                roomId = d.id,
                                memberId = d.getString("memberId") ?: d.id.removePrefix(FirestorePaths.ChatRoom.DEALER_PREFIX),
                                memberName = d.getString("memberName")?.takeIf { it.isNotBlank() } ?: "Member",
                                lastMessage = d.getString("lastMessage").orEmpty(),
                                lastMessageAt = lastAt,
                                unseen = needsReply && (seenAt == null || (lastAt != null && lastAt.after(seenAt)))
                            )
                        }.sortedByDescending { it.lastMessageAt?.time ?: 0L }
                    )
                }
            }

    /** The desk answered in a member's thread: it leaves the waiting queue, read up to now. */
    suspend fun markReplied(roomId: String, preview: String) {
        db.collection(FirestorePaths.Collection.CHAT_ROOMS).document(roomId).set(
            mapOf(
                "kind" to "dealer",
                "memberId" to roomId.removePrefix(FirestorePaths.ChatRoom.DEALER_PREFIX),
                "needsReply" to false,
                "lastMessage" to preview.take(140),
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "staffSeenAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).await()
    }

    /** The desk has the thread open. */
    suspend fun markSeen(roomId: String) {
        runCatching {
            db.collection(FirestorePaths.Collection.CHAT_ROOMS).document(roomId)
                .update("staffSeenAt", FieldValue.serverTimestamp()).await()
        }
    }

    /**
     * A member wrote in their own deal chat: the panel pushes the dealer, and
     * only the dealer (W9). Best effort; the message is already sent.
     */
    suspend fun notifyDealer(roomId: String, preview: String) = withContext(Dispatchers.IO) {
        val base = BuildConfig.ADMIN_API_BASE.trimEnd('/')
        if (base.isBlank()) return@withContext
        runCatching {
            val token = FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token ?: return@runCatching
            val body = JSONObject()
                .put("idToken", token)
                .put("roomId", roomId)
                .put("preview", preview.take(140))
                .toString()
                .toRequestBody("application/json".toMediaType())
            http.newCall(Request.Builder().url("$base/api/deal-chat/notify").post(body).build()).execute().close()
        }
    }

    /**
     * Pushes the desk's reply to the member, unless their chat is open — the
     * panel's /api/support/notify, which decides that on the server.
     */
    suspend fun notifyMember(roomId: String, preview: String, from: String) = withContext(Dispatchers.IO) {
        val base = BuildConfig.ADMIN_API_BASE.trimEnd('/')
        if (base.isBlank()) return@withContext
        runCatching {
            val token = FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token ?: return@runCatching
            val body = JSONObject()
                .put("idToken", token)
                .put("roomId", roomId)
                .put("preview", preview.take(140))
                .put("from", from)
                .toString()
                .toRequestBody("application/json".toMediaType())
            http.newCall(Request.Builder().url("$base/api/support/notify").post(body).build()).execute().close()
        }
    }
}
