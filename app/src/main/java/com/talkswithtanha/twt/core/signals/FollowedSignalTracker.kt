package com.talkswithtanha.twt.core.signals

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.talkswithtanha.twt.core.data.SignalFollowRepository
import com.talkswithtanha.twt.core.data.SignalRepository
import com.talkswithtanha.twt.core.data.Snapshot
import com.talkswithtanha.twt.core.model.FollowOutcome
import com.talkswithtanha.twt.core.model.Signal
import com.talkswithtanha.twt.core.model.SignalFollow
import com.talkswithtanha.twt.core.model.TradingStats
import com.talkswithtanha.twt.core.notifications.FollowedSignalNotifier
import com.talkswithtanha.twt.core.session.SessionRepository
import com.talkswithtanha.twt.core.storage.appPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which signals this member is following, what happened to them, and everything
 * that follows from that: the ongoing notification, the alerts when a followed
 * trade hits its target or its stop, and the member's own record.
 *
 * ### Where the alerts actually come from
 *
 * Two separate mechanisms, and it matters which is doing the work:
 *
 *  - **Local**, from [reconcile] below. The signals listener is live whenever
 *    the app is running, so a status change is noticed here and turned into a
 *    notification and a card update immediately.
 *  - **Push**, from a server, which is what covers a phone with the app closed.
 *    That half needs something server-side to send it — see
 *    `TwtMessagingService`. Nothing in this project can send an FCM message yet.
 *
 * This class is written so the local path is complete and correct on its own,
 * and so the push path only has to deliver the same payload.
 */
@Singleton
class FollowedSignalTracker @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val followRepository: SignalFollowRepository,
    private val signalRepository: SignalRepository,
    private val notifier: FollowedSignalNotifier,
    private val session: SessionRepository,
    private val scope: CoroutineScope
) {

    private val _follows = MutableStateFlow<List<SignalFollow>>(emptyList())
    val follows: StateFlow<List<SignalFollow>> = _follows.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val stats: StateFlow<TradingStats> = follows
        .map { TradingStats.from(it) }
        .stateIn(scope, SharingStarted.Eagerly, TradingStats.EMPTY)

    /** Still being followed and not yet written up. */
    val openFollows: StateFlow<List<SignalFollow>> = follows
        .map { list -> list.filter { it.isActive && it.outcome == FollowOutcome.OPEN } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private var watchedUserId: String? = null

    private var started = false

    /**
     * Starts watching. Called once from `TwtApplication`, not from a screen.
     *
     * Explicit rather than work in `init` for two reasons. A constructor that
     * launches coroutines is a constructor with side effects, which makes this
     * class impossible to build in a test without it immediately talking to
     * Firestore. And injection-triggered construction would mean the alerting
     * only starts when some screen first happens to need the tracker — so a
     * member who opens the app straight into the chat tab would not have their
     * followed trades watched at all until they wandered onto a signals screen.
     */
    fun start() {
        if (started) return
        started = true

        // Follows are per-account, so the listener follows whoever is signed in.
        // Tearing it down on sign-out matters: the next person on this device
        // must not inherit the previous member's positions, or their
        // notifications.
        scope.launch {
            session.currentUser.collect { user ->
                when {
                    user == null -> stop()
                    user.id != watchedUserId -> startFollows(user.id)
                }
            }
        }

        // The reconciliation loop. Every time either the follow list or the
        // signals feed changes, look for followed trades whose status moved.
        scope.launch {
            combine(
                follows,
                signalRepository.observeSignals()
            ) { follows, signals -> follows to signals }
                .collect { (follows, signals) ->
                    if (signals is Snapshot.Data) reconcile(follows, signals.value)
                }
        }
    }

    fun isFollowing(signalId: String): Boolean =
        _follows.value.any { it.signalId == signalId && it.isActive }

    fun followFor(signalId: String): SignalFollow? =
        _follows.value.firstOrNull { it.signalId == signalId }

    private fun startFollows(userId: String) {
        watchedUserId = userId
        _isLoading.value = true
        scope.launch {
            followRepository.observeFollows(userId).collect { snapshot ->
                when (snapshot) {
                    is Snapshot.Data -> {
                        _follows.value = snapshot.value
                        _isLoading.value = false
                    }
                    is Snapshot.Failed -> {
                        // Most often the composite index for this query not
                        // being deployed yet; the console link is in the
                        // message. The previous list is kept rather than
                        // cleared — an empty journal is a worse lie than a
                        // slightly stale one.
                        Log.w(TAG, "Follows listener failed", snapshot.error)
                        _isLoading.value = false
                    }
                }
            }
        }
    }

    private fun stop() {
        watchedUserId = null
        notifier.dismissAll(_follows.value.map { it.signalId })
        _follows.value = emptyList()
        _isLoading.value = false
        scope.launch { writeLastKnown(emptyMap()) }
    }

    suspend fun follow(signal: Signal) {
        val userId = watchedUserId ?: return
        runCatching { followRepository.follow(signal, userId) }
            .onSuccess {
                // Recorded *without* announcing. The member is looking at the
                // signal right now; they do not need to be told what it already
                // says.
                //
                // The card is deliberately not posted from here. The follow
                // document has not come back through the listener yet, so there
                // is nothing to build one from -- `reconcile` puts it up the
                // moment it does, which is a single code path rather than two
                // that can disagree.
                putLastKnown(signal.id, signal.status.stored)
            }
            .onFailure { Log.w(TAG, "Could not follow ${signal.id}", it) }
    }

    suspend fun unfollow(signalId: String) {
        val userId = watchedUserId ?: return
        runCatching { followRepository.unfollow(signalId, userId) }
            .onSuccess {
                clearLastKnown(signalId)
                notifier.dismiss(signalId)
            }
            .onFailure { Log.w(TAG, "Could not unfollow $signalId", it) }
    }

    suspend fun recordOutcome(
        signalId: String,
        outcome: FollowOutcome,
        pips: Double?,
        amountMinor: Long?,
        currency: String?,
        note: String?
    ) {
        val userId = watchedUserId ?: return
        followRepository.recordOutcome(
            signalId = signalId,
            userId = userId,
            outcome = outcome,
            pips = pips,
            amountMinor = amountMinor,
            currency = currency,
            note = note
        )
        clearLastKnown(signalId)
        notifier.dismiss(signalId)
    }

    /**
     * Compares each followed signal against the status it was last seen at, and
     * acts on anything that moved.
     *
     * **Never announce a status change you have not seen change.** The last
     * known status is persisted per signal, so the first sight of a signal we
     * already follow — a cold start, or a follow created on another device —
     * records the status without announcing it. Without that, every relaunch
     * re-announces every result the member has already been told about.
     */
    private suspend fun reconcile(follows: List<SignalFollow>, signals: List<Signal>) {
        if (watchedUserId == null || follows.isEmpty()) return

        val byId = signals.associateBy { it.id }
        val known = readLastKnown().toMutableMap()

        for (follow in follows.filter { it.isActive }) {
            val signal = byId[follow.signalId] ?: continue
            val previous = known[follow.signalId]
            val current = signal.status.stored
            known[follow.signalId] = current

            // The alert is driven by the *transition*, and only by a transition
            // this device has actually watched happen. A null `previous` is
            // first sight -- a cold start, or a follow created on another
            // device -- and announcing there would re-tell the member every
            // result they have already been told about, once per launch.
            val changed = previous != null && previous != current
            if (changed) notifier.alertStatusChange(signal, follow)

            // The card is driven by *state*, not by the transition.
            //
            // These have to be separate, and conflating them was a bug: posting
            // the card only when the status moved meant a trade the member had
            // just started following never got one at all, because `follow()`
            // records the status it followed at, so the very next pass sees no
            // change. Reposting is free -- same notification id, silent, and
            // `onlyAlertOnce` -- so the card is simply asserted on every pass
            // for anything still running, which also restores it after a reboot.
            when {
                signal.status.isOngoing -> notifier.show(signal, follow)

                // Finished. Replace the pinned card with a dismissible one
                // carrying the result, but only on the pass that saw it finish
                // -- otherwise every closed-but-unrecorded follow re-posts its
                // result on every cold start.
                changed -> notifier.show(signal, follow)
            }
        }

        writeLastKnown(known)
    }

    // ── Last-known status, persisted ──────────────────────────────────────
    //
    // Stored as one JSON blob under a single preference key rather than a key
    // per signal. It is read and written whole on every reconcile, it is never
    // queried by part, and a key per signal leaves an entry behind for every
    // trade the member has ever followed.

    private suspend fun readLastKnown(): Map<String, String> {
        val raw = context.appPreferences.data.first()[LAST_KNOWN_KEY] ?: return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            json.keys().asSequence().associateWith { json.getString(it) }
        }.getOrElse { emptyMap() }
    }

    private suspend fun writeLastKnown(values: Map<String, String>) {
        val raw = JSONObject(values as Map<*, *>).toString()
        context.appPreferences.edit { it[LAST_KNOWN_KEY] = raw }
    }

    private suspend fun putLastKnown(signalId: String, status: String) {
        writeLastKnown(readLastKnown() + (signalId to status))
    }

    private suspend fun clearLastKnown(signalId: String) {
        writeLastKnown(readLastKnown() - signalId)
    }

    private companion object {
        const val TAG = "FollowedSignalTracker"
        val LAST_KNOWN_KEY = stringPreferencesKey("signalFollows.lastKnownStatus")
    }
}
