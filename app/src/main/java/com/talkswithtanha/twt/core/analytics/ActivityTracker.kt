package com.talkswithtanha.twt.core.analytics

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.talkswithtanha.twt.core.session.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the member does in the app, for the admin panel's user history.
 *
 * Two records under `users/{uid}`, both allowed by firestore.rules and read
 * only by staff:
 *
 *  - `usage/{yyyy-mm-dd}`: that day's running totals — seconds in the app,
 *    number of opens, and a count per screen. Only ever incremented.
 *  - `activity/{id}`: an append-only log of moments worth a line in the
 *    timeline: the app opening, a session ending (with its length), a signal
 *    being opened.
 *
 * Time is counted only while the app is in the foreground, flushed every
 * minute and when it goes to the background, so a crash loses at most a
 * minute. Nothing here is allowed to throw into the app: a failed write is
 * logged and dropped.
 */
@Singleton
class ActivityTracker @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val session: SessionRepository,
    private val scope: CoroutineScope
) : DefaultLifecycleObserver {

    @Volatile private var uid: String? = null
    @Volatile private var foreground = false
    @Volatile private var countedUntil = 0L
    @Volatile private var sessionStart = 0L
    private var ticker: Job? = null

    /** Called once from TwtApplication.onCreate, on the main thread. */
    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        scope.launch {
            session.currentUser.map { it?.id }.distinctUntilChanged().collect { next ->
                // Signing out (or switching account) closes the old member's time first.
                if (uid != null && next != uid) flush()
                uid = next
                if (next != null && foreground) beginSession()
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        foreground = true
        if (uid != null) beginSession()
    }

    override fun onStop(owner: LifecycleOwner) {
        foreground = false
        ticker?.cancel()
        val member = uid ?: return
        val seconds = ((System.currentTimeMillis() - sessionStart) / 1000).coerceAtLeast(0)
        flush()
        event(member, "session_end", mapOf("seconds" to seconds))
    }

    /** A screen was shown. Counted per day; not logged as its own event. */
    fun screen(route: String?) {
        val member = uid ?: return
        val name = route?.substringBefore('/')?.substringBefore('?')?.take(40)?.ifBlank { null } ?: return
        write(member, mapOf("screens" to mapOf(name to FieldValue.increment(1))))
    }

    /** Something worth a line in the member's timeline. */
    fun log(type: String, detail: Map<String, Any?> = emptyMap()) {
        val member = uid ?: return
        event(member, type, detail)
    }

    private fun beginSession() {
        val member = uid ?: return
        val now = System.currentTimeMillis()
        sessionStart = now
        countedUntil = now
        write(member, mapOf("opens" to FieldValue.increment(1)))
        event(member, "app_open")
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                delay(60_000)
                flush()
            }
        }
    }

    /** Adds the foreground seconds since the last flush to today's total. */
    private fun flush() {
        val member = uid ?: return
        val now = System.currentTimeMillis()
        val seconds = ((now - countedUntil) / 1000).coerceIn(0, 3600)
        countedUntil = now
        if (seconds > 0) write(member, mapOf("seconds" to FieldValue.increment(seconds)))
    }

    private fun write(member: String, fields: Map<String, Any>) {
        val day = LocalDate.now().toString()
        val payload = fields + mapOf(
            "day" to day,
            "platform" to PLATFORM,
            "lastAt" to FieldValue.serverTimestamp()
        )
        firestore.collection("users").document(member).collection("usage").document(day)
            .set(payload, SetOptions.merge())
            .addOnFailureListener { Log.w(TAG, "usage write failed", it) }
    }

    private fun event(member: String, type: String, detail: Map<String, Any?> = emptyMap()) {
        val payload = buildMap<String, Any> {
            put("type", type)
            put("at", FieldValue.serverTimestamp())
            put("platform", PLATFORM)
            val clean = detail.filterValues { it != null }.entries.take(8).associate { it.key to it.value!! }
            if (clean.isNotEmpty()) put("detail", clean)
        }
        firestore.collection("users").document(member).collection("activity")
            .add(payload)
            .addOnFailureListener { Log.w(TAG, "activity write failed", it) }
    }

    private companion object {
        const val TAG = "ActivityTracker"
        const val PLATFORM = "Android"
    }
}
