package com.talkswithtanha.twt.core.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tanha's live analysis: one Firestore document per market ("XAUUSD"), with
 * the levels, zones, trend lines and notes he drew in the panel. Listened to,
 * so a level he drags moves on the phone a second later.
 *
 * The shape is the website's `LiveAnalysis` (lib/live/shared.ts).
 */
data class LiveDrawing(
    val id: String,
    val kind: Kind,
    val color: String,
    val p1: Double,
    val p2: Double? = null,
    /** Unix seconds. */
    val t1: Long? = null,
    val t2: Long? = null,
    val label: String = ""
) {
    enum class Kind { LEVEL, ZONE, TREND, NOTE }
}

data class LiveAnalysis(
    val key: String,
    val pair: String,
    val timeframe: LiveApi.Timeframe,
    val drawings: List<LiveDrawing>,
    val note: String,
    val updatedAt: Date?
)

@Singleton
class LiveAnalysisRepository @Inject constructor(
    private val db: FirebaseFirestore
) {
    /** Every market Tanha has put live, most recently touched first. */
    fun observeLive(): Flow<Snapshot<List<LiveAnalysis>>> =
        db.collection("liveAnalysis")
            .whereEqualTo("isLive", true)
            .snapshotFlow()
            .map { snap ->
                when (snap) {
                    is Snapshot.Data -> Snapshot.Data(
                        snap.value.documents.mapNotNull(::parse).sortedByDescending { it.updatedAt?.time ?: 0L }
                    )
                    is Snapshot.Failed -> snap
                }
            }

    private fun parse(doc: DocumentSnapshot): LiveAnalysis? {
        val raw = doc.get("drawings") as? List<*> ?: emptyList<Any>()
        val drawings = raw.mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            val kind = when (m["kind"]) {
                "level" -> LiveDrawing.Kind.LEVEL
                "zone" -> LiveDrawing.Kind.ZONE
                "trend" -> LiveDrawing.Kind.TREND
                "note" -> LiveDrawing.Kind.NOTE
                else -> return@mapNotNull null
            }
            LiveDrawing(
                id = m["id"] as? String ?: return@mapNotNull null,
                kind = kind,
                color = m["color"] as? String ?: "green",
                p1 = (m["p1"] as? Number)?.toDouble() ?: return@mapNotNull null,
                p2 = (m["p2"] as? Number)?.toDouble(),
                t1 = (m["t1"] as? Number)?.toLong(),
                t2 = (m["t2"] as? Number)?.toLong(),
                label = m["label"] as? String ?: ""
            )
        }
        return LiveAnalysis(
            key = doc.id,
            pair = doc.getString("pair") ?: doc.id,
            timeframe = LiveApi.Timeframe.of(doc.getString("timeframe")),
            drawings = drawings,
            note = doc.getString("note") ?: "",
            updatedAt = doc.getTimestamp("updatedAt")?.toDate()
        )
    }
}
