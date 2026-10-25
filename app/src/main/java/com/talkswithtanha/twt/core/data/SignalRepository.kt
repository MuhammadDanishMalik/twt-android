package com.talkswithtanha.twt.core.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.talkswithtanha.twt.core.firebase.FirestorePaths.Collection
import com.talkswithtanha.twt.core.firebase.FirestorePaths.SignalField as F
import com.talkswithtanha.twt.core.model.Signal
import com.talkswithtanha.twt.core.model.SignalAsset
import com.talkswithtanha.twt.core.model.SignalStatus
import com.talkswithtanha.twt.core.model.SignalType
import com.talkswithtanha.twt.core.model.TradeStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

interface SignalRepository {
    fun observeSignals(limit: Long = DEFAULT_LIMIT): Flow<Snapshot<List<Signal>>>
    fun observeSignal(id: String): Flow<Snapshot<Signal?>>
    suspend fun fetchSignal(id: String): Signal?

    companion object {
        const val DEFAULT_LIMIT = 50L
    }
}

/**
 * The only file that knows how a [Signal] is shaped in Firestore.
 */
@Singleton
class FirebaseSignalRepository @Inject constructor(
    private val db: FirebaseFirestore,
    private val session: com.talkswithtanha.twt.core.session.SessionRepository
) : SignalRepository {

    private val signals get() = db.collection(Collection.SIGNALS)

    /**
     * Drafts stay out of the app. Tanha builds a signal over several minutes;
     * without this filter members would see a half-finished one with no stop
     * loss.
     *
     * Note this query filters on one field and orders by another, so Firestore
     * needs a composite index for it. That only fails once the collection is
     * non-empty, which is why it passes every test until the day real data
     * arrives — the error carries a console link that creates the index.
     */
    private fun publishedQuery(limit: Long): Query = signals
        .whereEqualTo(F.IS_PUBLISHED, true)
        .orderBy(F.TIMESTAMP, Query.Direction.DESCENDING)
        .limit(limit)

    /**
     * Every published signal for a Pro member or staff. A free member may only
     * read analysis posts and settled results — a query for everything is
     * refused outright, not filtered — so for them it is those two queries,
     * merged newest first, as on the website (signals-repo.ts).
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun observeSignals(limit: Long): Flow<Snapshot<List<Signal>>> =
        session.currentUser
            .map { user -> user == null || user.hasAppAccess || user.isStaff }
            .distinctUntilChanged()
            .flatMapLatest { full ->
                if (full) {
                    publishedQuery(limit).snapshotFlow().map { snapshot -> snapshot.toSignals() }
                } else {
                    val analysis = signals
                        .whereEqualTo(F.IS_PUBLISHED, true)
                        .whereEqualTo(F.IS_ANALYSIS, true)
                        .orderBy(F.TIMESTAMP, Query.Direction.DESCENDING)
                        .limit(limit)
                        .snapshotFlow()
                    val settled = signals
                        .whereEqualTo(F.IS_PUBLISHED, true)
                        .whereIn(F.STATUS, SETTLED_STATUSES)
                        .orderBy(F.TIMESTAMP, Query.Direction.DESCENDING)
                        .limit(limit)
                        .snapshotFlow()
                    kotlinx.coroutines.flow.combine(analysis, settled) { a, b ->
                        val left = a.toSignals()
                        val right = b.toSignals()
                        when {
                            left is Snapshot.Data && right is Snapshot.Data ->
                                Snapshot.Data((left.value + right.value).distinctBy { it.id }.sortedByDescending { it.timestamp })
                            left is Snapshot.Data -> left
                            right is Snapshot.Data -> right
                            else -> left
                        }
                    }
                }
            }

    private fun Snapshot<com.google.firebase.firestore.QuerySnapshot>.toSignals(): Snapshot<List<Signal>> =
        when (this) {
            is Snapshot.Failed -> this
            is Snapshot.Data -> Snapshot.Data(value.documents.mapNotNull { map(it) })
        }

    private companion object {
        /** The results a free member may read: the same list as the rules. */
        val SETTLED_STATUSES = listOf("TP1 HIT", "TP2 HIT", "CLOSED ✓", "CLOSED ✗")
    }

    override fun observeSignal(id: String): Flow<Snapshot<Signal?>> =
        signals.document(id).snapshotFlow().map { snapshot ->
            when (snapshot) {
                is Snapshot.Failed -> snapshot
                is Snapshot.Data -> Snapshot.Data(snapshot.value?.let { map(it) })
            }
        }

    override suspend fun fetchSignal(id: String): Signal? =
        signals.document(id).get().await().let { if (it.exists()) map(it) else null }

    /**
     * Returns null for a document that cannot be read as a signal.
     *
     * A trade with no pair or no entry price is not something to render with
     * blanks in it — a member acting on a signal with a missing stop loss is a
     * real loss of money. Dropping it is the safe failure.
     */
    private fun map(document: DocumentSnapshot): Signal? {
        val pair = document.get(F.PAIR).asNonBlankString() ?: return null
        val entryPrice = document.get(F.ENTRY_PRICE).asNonBlankString() ?: return null
        val stopLoss = document.get(F.STOP_LOSS).asNonBlankString() ?: return null

        val takeProfits = (document.get(F.TAKE_PROFITS) as? List<*>)
            .orEmpty()
            .filterIsInstance<Map<*, *>>()
            .mapIndexedNotNull { index, raw ->
                val price = raw[F.TakeProfit.PRICE].asNonBlankString() ?: return@mapIndexedNotNull null
                Signal.TakeProfit(
                    label = raw[F.TakeProfit.LABEL].asNonBlankString() ?: "TP${index + 1}",
                    price = price,
                    isHit = raw[F.TakeProfit.IS_HIT] as? Boolean ?: false
                )
            }

        return Signal(
            id = document.id,
            pair = pair,
            asset = SignalAsset.from(document.getString(F.ASSET)),
            type = SignalType.from(document.getString(F.TYPE)),
            entryPrice = entryPrice,
            entryPriceValue = document.get(F.ENTRY_PRICE_VALUE).asDoubleOrNull()
                ?: parsePrice(entryPrice),
            takeProfits = takeProfits,
            stopLoss = stopLoss,
            stopLossValue = document.get(F.STOP_LOSS_VALUE).asDoubleOrNull()
                ?: parsePrice(stopLoss),
            status = SignalStatus.from(document.getString(F.STATUS)),
            timestamp = document.getTimestamp(F.TIMESTAMP)?.toDate() ?: Date(),
            timeframe = document.get(F.TIMEFRAME).asNonBlankString() ?: "—",
            riskReward = document.get(F.RISK_REWARD).asNonBlankString() ?: "—",
            pipsGained = document.get(F.PIPS_GAINED).asDoubleOrNull(),
            notes = document.get(F.NOTES).asNonBlankString(),
            tradeStyle = TradeStyle.from(document.getString(F.TRADE_STYLE)),
            chartImageUrl = document.get(F.CHART_IMAGE_URL).asNonBlankString(),
            screenshots = (document.get(F.SCREENSHOTS) as? List<*>)
                .orEmpty()
                .mapNotNull { it.asNonBlankString() },
            isEdited = document.getBoolean(F.IS_EDITED) ?: false,
            isAnalysis = document.getBoolean(F.IS_ANALYSIS) ?: false
        )
    }

    /** Last-resort fallback when the numeric field is missing: strip the
     *  thousands separators the display string carries and parse what is left. */
    private fun parsePrice(display: String): Double =
        display.replace(",", "").trim().toDoubleOrNull() ?: 0.0
}
