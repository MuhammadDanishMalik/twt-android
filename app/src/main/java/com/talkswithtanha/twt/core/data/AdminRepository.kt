package com.talkswithtanha.twt.core.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.talkswithtanha.twt.core.firebase.FirestorePaths.Collection
import com.talkswithtanha.twt.core.firebase.FirestorePaths.DealField as D
import com.talkswithtanha.twt.core.firebase.FirestorePaths.SignalField as S
import com.talkswithtanha.twt.core.model.Deal
import com.talkswithtanha.twt.core.model.DealStatus
import com.talkswithtanha.twt.core.model.Signal
import com.talkswithtanha.twt.core.model.SignalAsset
import com.talkswithtanha.twt.core.model.SignalStatus
import com.talkswithtanha.twt.core.model.SignalType
import com.talkswithtanha.twt.core.model.TradeStyle
import com.talkswithtanha.twt.core.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The writes only a staff account may make: posting a signal, moving one along,
 * the marketplace, and the app's own furniture.
 *
 * One repository rather than four because the admin area is one screen, and
 * splitting it would mean four files that each exist to hold two methods.
 *
 * ### The same documents the admin panel writes
 *
 * Every field here is mirrored from `twt-admin/lib/signals-repo.ts`,
 * `deals-repo.ts` and `banners-repo.ts`. A signal posted from a phone must be
 * indistinguishable from one posted at a laptop — same keys, same defaults,
 * `views` and `followers` seeded so the panel's columns are not blank.
 *
 * Nothing here is guarded beyond hiding the screens: `can()` in firestore.rules
 * is what enforces staff-only and per-area access, and it is the only thing
 * that can.
 */
interface AdminRepository {
    /**
     * Posts a trade, and puts it in the feed.
     *
     * [author] is who is posting: the feed row carries their name, and a signal
     * nobody can attribute is not worth sharing.
     */
    suspend fun createSignal(draft: SignalDraft, author: User? = null): String
    suspend fun setSignalStatus(signalId: String, status: SignalStatus)
    suspend fun setTakeProfitHit(signalId: String, index: Int, isHit: Boolean)
    suspend fun deleteSignal(signalId: String)

    fun observeAllDeals(): Flow<Snapshot<List<Deal>>>
    suspend fun setDealStatus(dealId: String, status: DealStatus, note: String)

    fun observeBanners(): Flow<Snapshot<List<AdminBanner>>>
    suspend fun setBannerActive(bannerId: String, isActive: Boolean)
    suspend fun saveBanner(banner: AdminBanner)
    suspend fun deleteBanner(bannerId: String)
}

/**
 * What the Post a signal form collects. Prices stay strings all the way to
 * Firestore, as they do on the panel: decimal places differ per instrument, and
 * parsing "2,318.40" into a Double and printing it back is how gold ends up
 * quoted to two places and yen to five.
 */
data class SignalDraft(
    val pair: String = "",
    val asset: SignalAsset = SignalAsset.FOREX,
    val type: SignalType = SignalType.BUY,
    val entryPrice: String = "",
    val stopLoss: String = "",
    /** Prices only; labels are generated as TP1, TP2, TP3 in order. */
    val takeProfits: List<String> = listOf("", "", ""),
    val timeframe: String = "H1",
    val riskReward: String = "",
    val tradeStyle: TradeStyle = TradeStyle.SCALP,
    val notes: String = "",
    val isPublished: Boolean = true
) {
    val isComplete: Boolean
        get() = pair.isNotBlank() &&
            entryPrice.isNotBlank() &&
            stopLoss.isNotBlank() &&
            takeProfits.any { it.isNotBlank() }
}

/** A home-screen carousel banner, as the admin area edits it. */
data class AdminBanner(
    val id: String = "",
    val title: String = "",
    val subtitle: String = "",
    val imageUrl: String = "",
    /** One of `none`, `signals`, `academy`, `chat`, `url` — the panel's set. */
    val action: String = "none",
    val url: String = "",
    val isActive: Boolean = true,
    val order: Int = 0
) {
    companion object {
        val actions = listOf(
            "none" to "Just show it",
            "signals" to "Open Signals",
            "academy" to "Open Academy",
            "chat" to "Open Chat",
            "url" to "Open a web link"
        )
    }
}

@Singleton
class FirebaseAdminRepository @Inject constructor(
    private val db: FirebaseFirestore,
    private val feed: FeedRepository
) : AdminRepository {

    private val signals get() = db.collection(Collection.SIGNALS)
    private val deals get() = db.collection(Collection.DEALS)
    private val banners get() = db.collection(Collection.BANNERS)

    // ── Signals ──────────────────────────────────────────────────────────

    override suspend fun createSignal(draft: SignalDraft, author: User?): String {
        val document = signals.document()

        val targets = draft.takeProfits
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapIndexed { index, price ->
                mapOf("label" to "TP${index + 1}", "price" to price, "isHit" to false)
            }

        document.set(
            mapOf(
                S.PAIR to draft.pair.trim().uppercase(),
                S.ASSET to draft.asset.stored,
                S.TYPE to draft.type.stored,
                S.ENTRY_PRICE to draft.entryPrice,
                S.ENTRY_PRICE_VALUE to numeric(draft.entryPrice),
                S.TAKE_PROFITS to targets,
                S.STOP_LOSS to draft.stopLoss,
                S.STOP_LOSS_VALUE to numeric(draft.stopLoss),
                S.STATUS to SignalStatus.ACTIVE.stored,
                S.TIMEFRAME to draft.timeframe,
                S.RISK_REWARD to draft.riskReward,
                S.NOTES to draft.notes.trim().ifEmpty { null },
                S.PIPS_GAINED to null,
                S.IS_PUBLISHED to draft.isPublished,
                S.TRADE_STYLE to draft.tradeStyle.stored,
                S.CHART_IMAGE_URL to null,
                S.SCREENSHOTS to emptyList<String>(),
                // Server time, not the phone's: a device with a wrong clock
                // would otherwise pin the signal to the top of every feed.
                S.TIMESTAMP to FieldValue.serverTimestamp(),
                "views" to 0,
                "followers" to 0,
                S.IS_EDITED to false
            )
        ).await()

        // Into the feed, where members already are. Best effort on purpose: a
        // feed that refuses the post must not lose the trade, which is already
        // written and already visible in the Signals tab.
        if (author != null && draft.isPublished) {
            runCatching {
                feed.shareSignal(
                    Signal(
                        id = document.id,
                        pair = draft.pair.trim().uppercase(),
                        type = draft.type,
                        entryPrice = draft.entryPrice,
                        stopLoss = draft.stopLoss,
                        notes = draft.notes.trim().ifEmpty { null }
                    ),
                    author
                )
            }.onSuccess { postId ->
                // Written back so the panel's publish toggle knows this trade
                // has already been shared. The two sides share one feed, and
                // without this a signal made here and re-published there would
                // appear twice.
                runCatching { document.update("feedPostId", postId).await() }
            }
        }

        return document.id
    }

    override suspend fun setSignalStatus(signalId: String, status: SignalStatus) {
        signals.document(signalId).update(S.STATUS, status.stored).await()
    }

    /**
     * Ticking a target rewrites the whole array — Firestore cannot set one field
     * inside an array element, and the alternative (a subcollection per target)
     * would be a schema change the panel does not share.
     */
    override suspend fun setTakeProfitHit(signalId: String, index: Int, isHit: Boolean) {
        val reference = signals.document(signalId)
        val snapshot = reference.get().await()

        @Suppress("UNCHECKED_CAST")
        val targets = (snapshot.get(S.TAKE_PROFITS) as? List<Map<String, Any?>>)
            ?.map { it.toMutableMap() }
            ?.toMutableList()
            ?: return
        if (index !in targets.indices) return

        targets[index]["isHit"] = isHit
        reference.update(S.TAKE_PROFITS, targets).await()
    }

    override suspend fun deleteSignal(signalId: String) {
        signals.document(signalId).delete().await()
    }

    // ── Deals ────────────────────────────────────────────────────────────

    override fun observeAllDeals(): Flow<Snapshot<List<Deal>>> =
        deals
            .orderBy(D.CREATED_AT, Query.Direction.DESCENDING)
            .limit(80)
            .snapshotFlow()
            .map { snapshot ->
                when (snapshot) {
                    is Snapshot.Failed -> snapshot
                    is Snapshot.Data -> Snapshot.Data(snapshot.value.documents.mapNotNull { dealFrom(it) })
                }
            }

    /**
     * Appends an event and moves the status, exactly as the admin panel does —
     * `arrayUnion` rather than read-modify-write, because the member may be
     * acting on the same deal at the same moment and one of the two writes
     * would otherwise be dropped.
     */
    override suspend fun setDealStatus(dealId: String, status: DealStatus, note: String) {
        deals.document(dealId).update(
            mapOf(
                D.STATUS to status.stored,
                D.EVENTS to FieldValue.arrayUnion(
                    mapOf(
                        "id" to UUID.randomUUID().toString(),
                        "status" to status.stored,
                        "note" to note,
                        // A client clock, because serverTimestamp() is not
                        // allowed inside an array element. The ordering that
                        // matters is the status, not this.
                        "timestamp" to Timestamp(Date())
                    )
                )
            )
        ).await()
    }

    // ── Banners ──────────────────────────────────────────────────────────

    override fun observeBanners(): Flow<Snapshot<List<AdminBanner>>> =
        banners
            .orderBy("order")
            .snapshotFlow()
            .map { snapshot ->
                when (snapshot) {
                    is Snapshot.Failed -> snapshot
                    is Snapshot.Data -> Snapshot.Data(
                        snapshot.value.documents.map { document ->
                            AdminBanner(
                                id = document.id,
                                title = document.getString("title").orEmpty(),
                                subtitle = document.getString("subtitle").orEmpty(),
                                imageUrl = document.getString("imageUrl").orEmpty(),
                                action = document.getString("action") ?: "none",
                                url = document.getString("url").orEmpty(),
                                isActive = document.getBoolean("isActive") ?: false,
                                order = (document.getLong("order") ?: 0L).toInt()
                            )
                        }
                    )
                }
            }

    override suspend fun setBannerActive(bannerId: String, isActive: Boolean) {
        banners.document(bannerId).update(
            mapOf("isActive" to isActive, "updatedAt" to FieldValue.serverTimestamp())
        ).await()
    }

    override suspend fun saveBanner(banner: AdminBanner) {
        val payload = mutableMapOf<String, Any?>(
            "title" to banner.title.trim(),
            "subtitle" to banner.subtitle.trim(),
            "imageUrl" to banner.imageUrl.trim(),
            "action" to banner.action,
            // Only a web-link banner carries a URL. The panel clears it the same
            // way, so a banner edited on a phone does not come back with a dead
            // link attached to an Academy button.
            "url" to if (banner.action == "url") banner.url.trim() else "",
            "isActive" to banner.isActive,
            "order" to banner.order,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        if (banner.id.isEmpty()) {
            payload["clicks"] = 0
            payload["createdAt"] = FieldValue.serverTimestamp()
            banners.add(payload).await()
        } else {
            banners.document(banner.id).set(payload, com.google.firebase.firestore.SetOptions.merge()).await()
        }
    }

    override suspend fun deleteBanner(bannerId: String) {
        banners.document(bannerId).delete().await()
    }

    /** "2,318.40" → 2318.40. Mirrors `numericValue` in the panel. */
    private fun numeric(raw: String): Double =
        raw.filter { it.isDigit() || it == '.' || it == '-' }.toDoubleOrNull() ?: 0.0
}
