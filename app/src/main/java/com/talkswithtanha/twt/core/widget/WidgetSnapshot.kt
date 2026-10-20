package com.talkswithtanha.twt.core.widget

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.talkswithtanha.twt.core.storage.appPreferences
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date

/**
 * What the home-screen widgets know about the member.
 *
 * A widget cannot open a Firestore listener — it is rendered for a moment and
 * then forgotten — so the app hands it a finished picture instead. Whenever the
 * feed, the member's follows or their access change, [WidgetPublisher] writes
 * one of these and asks Glance to redraw. The widgets only ever read.
 *
 * iOS does the same thing through an App Group, because its widgets run in a
 * separate process with a separate container. On Android the widget is part of
 * this app, so DataStore is enough and there is nothing to entitle.
 *
 * ### Already formatted
 *
 * Prices arrive as the strings the team typed ("2,318.40"), exactly as the app
 * shows them. The widget never re-derives a number — one that rounds a price
 * differently from the app it came from is a widget nobody trusts.
 */
data class WidgetSnapshot(
    val signals: List<WidgetSignal> = emptyList(),
    val access: WidgetAccess? = null,
    val isSignedIn: Boolean = false
) {
    /**
     * What the small widget cycles through: the live trades when there are any,
     * otherwise the most recent closed ones, so it is never blank on a quiet day.
     */
    val cyclable: List<WidgetSignal>
        get() = signals.filter { !it.isClosed }.ifEmpty { signals }

    val liveCount: Int get() = signals.count { !it.isClosed }

    companion object {
        val SIGNED_OUT = WidgetSnapshot()
    }
}

data class WidgetSignal(
    val id: String,
    val pair: String,
    /** "BUY" or "SELL". */
    val direction: String,
    val entry: String,
    val stopLoss: String,
    val targets: List<WidgetTarget>,
    /** `SignalStatus.stored` — "ACTIVE", "TP1 HIT", "CLOSED ✓". */
    val status: String,
    val pips: Double?,
    val postedAt: Date,
    /** The member follows it — followed trades are shown first. */
    val isFollowed: Boolean
) {
    val isBuy: Boolean get() = direction == "BUY"
    val isClosed: Boolean get() = status.startsWith("CLOSED")
    val isLost: Boolean get() = status.contains("✗")
    val isWon: Boolean get() = status.contains("✓")
    val hitCount: Int get() = targets.count { it.isHit }

    /** The furthest target, which is the number a member watches for. */
    val finalTarget: String get() = targets.lastOrNull()?.price ?: "—"

    /** What the badge says. "CLOSED ✓" is how the database spells it, not how a
     *  person would. */
    val statusLabel: String
        get() = when (status) {
            "ACTIVE" -> "LIVE"
            "CLOSED ✓" -> "WON"
            "CLOSED ✗" -> "STOPPED"
            else -> status
        }

    /** One line on where the trade is, in words. */
    val progressLine: String
        get() = when {
            isLost -> "Stopped out"
            isWon -> "Closed in profit"
            status == "PENDING" -> "Waiting for entry"
            hitCount > 0 -> "TP$hitCount of ${targets.size} hit"
            targets.size == 1 -> "Running · 1 target"
            else -> "Running · ${targets.size} targets"
        }

    val pipsText: String? get() = pips?.let { "${if (it >= 0) "+" else ""}${it.toInt()}" }
}

data class WidgetTarget(val label: String, val price: String, val isHit: Boolean)

data class WidgetAccess(
    val holderName: String,
    /** Null means the code does not expire. */
    val expiresAt: Date?,
    val grantedAt: Date?
)

/**
 * Where the app leaves the picture and the widgets pick it up.
 *
 * JSON by hand through `org.json` rather than a serialization library: this is
 * the only thing in the app that needs it, and adding a plugin and a codegen
 * step for one object is a worse trade than thirty lines of mapping.
 */
object WidgetStore {

    private val SNAPSHOT = stringPreferencesKey("widget.snapshot")

    /** Which signal the small widget is showing. Advanced by its arrow button. */
    private val CURSOR = stringPreferencesKey("widget.signalCursor")

    /** Returns false when nothing changed, so the caller can skip a redraw. */
    suspend fun save(context: Context, snapshot: WidgetSnapshot): Boolean {
        val encoded = encode(snapshot)
        val current = context.appPreferences.data.first()[SNAPSHOT]
        if (current == encoded) return false
        context.appPreferences.edit { it[SNAPSHOT] = encoded }
        return true
    }

    suspend fun load(context: Context): WidgetSnapshot =
        context.appPreferences.data.first()[SNAPSHOT]?.let(::decode) ?: WidgetSnapshot.SIGNED_OUT

    suspend fun cursor(context: Context): Int =
        context.appPreferences.data.first()[CURSOR]?.toIntOrNull() ?: 0

    suspend fun setCursor(context: Context, value: Int) {
        context.appPreferences.edit { it[CURSOR] = value.toString() }
    }

    // ── JSON ─────────────────────────────────────────────────────────────

    private fun encode(snapshot: WidgetSnapshot): String {
        val signals = JSONArray()
        snapshot.signals.forEach { signal ->
            val targets = JSONArray()
            signal.targets.forEach { target ->
                targets.put(
                    JSONObject()
                        .put("label", target.label)
                        .put("price", target.price)
                        .put("isHit", target.isHit)
                )
            }
            signals.put(
                JSONObject()
                    .put("id", signal.id)
                    .put("pair", signal.pair)
                    .put("direction", signal.direction)
                    .put("entry", signal.entry)
                    .put("stopLoss", signal.stopLoss)
                    .put("targets", targets)
                    .put("status", signal.status)
                    .put("pips", signal.pips ?: JSONObject.NULL)
                    .put("postedAt", signal.postedAt.time)
                    .put("isFollowed", signal.isFollowed)
            )
        }

        val root = JSONObject()
            .put("signals", signals)
            .put("isSignedIn", snapshot.isSignedIn)

        snapshot.access?.let { access ->
            root.put(
                "access",
                JSONObject()
                    .put("holderName", access.holderName)
                    .put("expiresAt", access.expiresAt?.time ?: JSONObject.NULL)
                    .put("grantedAt", access.grantedAt?.time ?: JSONObject.NULL)
            )
        }

        return root.toString()
    }

    private fun decode(raw: String): WidgetSnapshot = runCatching {
        val root = JSONObject(raw)
        val signalsJson = root.optJSONArray("signals") ?: JSONArray()

        val signals = (0 until signalsJson.length()).map { index ->
            val item = signalsJson.getJSONObject(index)
            val targetsJson = item.optJSONArray("targets") ?: JSONArray()
            WidgetSignal(
                id = item.optString("id"),
                pair = item.optString("pair"),
                direction = item.optString("direction"),
                entry = item.optString("entry"),
                stopLoss = item.optString("stopLoss"),
                targets = (0 until targetsJson.length()).map { target ->
                    val raw = targetsJson.getJSONObject(target)
                    WidgetTarget(
                        label = raw.optString("label"),
                        price = raw.optString("price"),
                        isHit = raw.optBoolean("isHit")
                    )
                },
                status = item.optString("status"),
                pips = if (item.isNull("pips")) null else item.optDouble("pips"),
                postedAt = Date(item.optLong("postedAt")),
                isFollowed = item.optBoolean("isFollowed")
            )
        }

        val access = root.optJSONObject("access")?.let {
            WidgetAccess(
                holderName = it.optString("holderName"),
                expiresAt = if (it.isNull("expiresAt")) null else Date(it.optLong("expiresAt")),
                grantedAt = if (it.isNull("grantedAt")) null else Date(it.optLong("grantedAt"))
            )
        }

        WidgetSnapshot(
            signals = signals,
            access = access,
            isSignedIn = root.optBoolean("isSignedIn")
        )
        // A snapshot written by a newer build that this one cannot read must not
        // crash the widget host. Falling back to signed-out shows "Open Twt",
        // which is wrong but harmless, and the next publish fixes it.
    }.getOrDefault(WidgetSnapshot.SIGNED_OUT)
}
