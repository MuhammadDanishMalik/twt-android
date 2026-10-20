package com.talkswithtanha.twt.core.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.talkswithtanha.twt.MainActivity
import com.talkswithtanha.twt.R
import com.talkswithtanha.twt.core.model.Signal
import com.talkswithtanha.twt.core.model.SignalFollow
import com.talkswithtanha.twt.core.model.SignalStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Android answer to the Dynamic Island.
 *
 * iOS puts a followed trade in a Live Activity. There is no equivalent here and
 * reproducing one would be a bad imitation, so the idiom is an **ongoing
 * notification**: pair, direction, entry, stop, target and current status, sat
 * at the top of the shade for as long as the trade is open, updated from the
 * same status-change logic that fires the alerts.
 *
 * It is a plain ongoing notification rather than a foreground service. A
 * foreground service would need `dataSync` on Android 14+, a permanent
 * "TWT is running" entry, and a justification at review time — all to keep a
 * card up to date that only changes when Firestore pushes a change to a listener
 * the app already has. The card is refreshed whenever the app sees a change and
 * by the push handler when it does not.
 */
@Singleton
class FollowedSignalNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    private val manager = NotificationManagerCompat.from(context)

    /**
     * Puts up (or refreshes) the card for one followed trade.
     *
     * The notification id is derived from the signal id so an update replaces
     * the card in place rather than stacking a second one behind it.
     */
    fun show(signal: Signal, follow: SignalFollow) {
        if (!canPost()) return

        val progress = TradeProgress.of(signal)
        val ongoing = signal.status.isOngoing
        val builder = NotificationCompat.Builder(context, NotificationChannels.FOLLOWED_SIGNAL)
            .setSmallIcon(R.drawable.ic_stat_signal)
            .setContentTitle(
                if (ongoing) "Following ${signal.pair} · ${signal.type.stored}"
                else "${signal.pair} · ${signal.type.stored} · ${signal.status.stored}"
            )
            .setContentText(progress.headline(signal))
            .setSubText(progress.stepLabel)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setColor(progress.color)
            .setContentIntent(openSignal(signal.id))

        if (android.os.Build.VERSION.SDK_INT >= 36) {
            // Android 16 Live Update: a segmented track with a stop at Entry and
            // at every target, filled as targets are hit — the Android answer to
            // the iPhone's Live Activity. The short chip sits in the status bar.
            val style = NotificationCompat.ProgressStyle()
                .setStyledByProgress(false)
                .setProgress(progress.value)
            progress.segments.forEach { style.addProgressSegment(it) }
            progress.points.forEach { style.addProgressPoint(it) }
            builder.setStyle(style)
                .setRequestPromotedOngoing(ongoing)
                .setShortCriticalText(progress.chip(signal))
        } else {
            builder
                .setStyle(NotificationCompat.BigTextStyle().bigText(detail(signal, follow)))
                .setProgress(progress.max, progress.value, false)
        }

        post(notificationId(signal.id), builder.build())
    }

    /**
     * One alert per status change, and only for a signal this member follows.
     *
     * Separate from [show] on purpose: the ongoing card is silent and replaces
     * itself, this one is meant to be felt and each status gets its own entry so
     * a member can scroll back through what happened.
     */
    fun alertStatusChange(signal: Signal, follow: SignalFollow) {
        if (!canPost()) return

        val notification = NotificationCompat.Builder(context, NotificationChannels.SIGNAL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_signal)
            .setContentTitle("${signal.pair} · ${signal.status.stored}")
            .setContentText(alertBody(signal, follow))
            .setStyle(NotificationCompat.BigTextStyle().bigText(alertBody(signal, follow)))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openSignal(signal.id))
            .build()

        // A distinct id per status, so "TP1 HIT" and the later "CLOSED ✓" are
        // two entries rather than one overwriting the other.
        post(notificationId("${signal.id}:${signal.status.stored}"), notification)
    }

    /**
     * Posts, and swallows the refusal.
     *
     * [canPost] is checked before building anything, but a permission can be
     * revoked between that check and this call — the member pulls down the
     * shade and turns notifications off while a trade is running — and the
     * throw lands on whichever coroutine happened to be reconciling. There is
     * nothing useful to do about it: the member has just said they do not want
     * these, and the in-app journal is unaffected.
     */
    private fun post(id: Int, notification: android.app.Notification) {
        try {
            manager.notify(id, notification)
        } catch (e: SecurityException) {
            android.util.Log.i(TAG, "Notifications are not permitted; skipping", e)
        }
    }

    fun dismiss(signalId: String) {
        manager.cancel(notificationId(signalId))
    }

    fun dismissAll(signalIds: Collection<String>) {
        signalIds.forEach { dismiss(it) }
    }

    private fun summary(signal: Signal): String {
        val target = signal.takeProfits.firstOrNull { !it.isHit }?.price
            ?: signal.takeProfits.lastOrNull()?.price
        return buildString {
            append(signal.status.stored)
            append(" · in ").append(signal.entryPrice)
            target?.let { append(" · tp ").append(it) }
            append(" · sl ").append(signal.stopLoss)
        }
    }

    private fun detail(signal: Signal, follow: SignalFollow): String = buildString {
        append("Status: ").append(signal.status.stored).append('\n')
        append("You entered at ").append(follow.entryPrice).append('\n')
        append("Stop loss ").append(signal.stopLoss)
        signal.takeProfits.forEach { tp ->
            append('\n').append(tp.label).append(' ').append(tp.price)
            if (tp.isHit) append(" ✓")
        }
        signal.pipsGained?.let { append("\n").append(it.toInt()).append(" pips") }
    }

    private fun alertBody(signal: Signal, follow: SignalFollow): String = when (signal.status) {
        SignalStatus.LOST ->
            "Stop loss hit at ${signal.stopLoss}. You followed this at ${follow.entryPrice}."
        SignalStatus.WON -> {
            val pips = signal.pipsGained?.let { " ${it.toInt()} pips." }.orEmpty()
            "Closed in profit.$pips Tap to record your result."
        }
        SignalStatus.TP1_HIT, SignalStatus.TP2_HIT ->
            "${signal.status.stored}. Entry was ${follow.entryPrice}."
        else ->
            "This signal you are following has moved to ${signal.status.stored}."
    }

    private fun openSignal(signalId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_SIGNAL_ID, signalId)
        }
        return PendingIntent.getActivity(
            context,
            signalId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * On Android 13+ posting anything without POST_NOTIFICATIONS throws. The
     * permission is asked for at the moment a member follows their first trade —
     * the first point at which they have an obvious reason to say yes, having
     * just asked to be told what happens to it.
     */
    private fun canPost(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED ||
            android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU

    private fun notificationId(key: String): Int = key.hashCode()

    companion object {
        /**
         * Deliberately the bare key the server uses, not a namespaced one.
         *
         * When the system tray handles a push itself — which it does whenever
         * the app is backgrounded — it copies each `data` entry onto the launch
         * intent under its own name. Matching that here means one extra to read
         * whether the notification came from the tray, from the push handler or
         * from the in-app tracker.
         */
        const val EXTRA_SIGNAL_ID = "signalId"
        private const val TAG = "FollowedSignalNotifier"
    }
}

/**
 * Where a followed trade is on its way from entry to its last target, for the
 * progress track: one step per take-profit, filled as each is hit, full and
 * red when the stop is hit.
 */
internal class TradeProgress private constructor(
    val max: Int,
    val value: Int,
    val color: Int,
    val stepLabel: String,
    val segments: List<NotificationCompat.ProgressStyle.Segment>,
    val points: List<NotificationCompat.ProgressStyle.Point>
) {
    fun headline(signal: Signal): String {
        val next = signal.takeProfits.firstOrNull { !it.isHit }
        return when {
            signal.status == SignalStatus.LOST -> "Stop loss hit at ${signal.stopLoss}"
            signal.status == SignalStatus.WON ->
                "Closed in profit" + (signal.pipsGained?.let { " · ${it.toInt()} pips" } ?: "")
            next != null -> "Next: ${next.label} ${next.price} · Stop ${signal.stopLoss}"
            else -> "Entry ${signal.entryPrice} · Stop ${signal.stopLoss}"
        }
    }

    /** Status-bar chip on Android 16: "TP1", "TP2", "WIN", "SL", "LIVE". */
    fun chip(signal: Signal): String = when (signal.status) {
        SignalStatus.TP1_HIT -> "TP1"
        SignalStatus.TP2_HIT -> "TP2"
        SignalStatus.WON -> "WIN"
        SignalStatus.LOST -> "SL"
        SignalStatus.PENDING -> "WAIT"
        SignalStatus.ACTIVE -> "LIVE"
    }

    companion object {
        private const val STEP = 100
        private const val GREEN = 0xFF33C759.toInt()
        private const val RED = 0xFFF04D4D.toInt()
        private const val TRACK = 0xFF5A5A5A.toInt()

        fun of(signal: Signal): TradeProgress {
            val tps = signal.takeProfits
            val steps = tps.size.coerceAtLeast(1)
            val max = steps * STEP
            val hit = tps.count { it.isHit }
            val (value, color) = when (signal.status) {
                SignalStatus.LOST -> max to RED
                SignalStatus.WON -> max to GREEN
                // Hit targets fill their step; a live trade shows a sliver so
                // the track reads as started rather than empty.
                else -> (hit * STEP).coerceAtLeast(STEP / 8) to GREEN
            }
            val label = when (signal.status) {
                SignalStatus.LOST -> "Stopped out"
                SignalStatus.WON -> "Closed ✓"
                else -> if (tps.isEmpty()) "Running" else "$hit of ${tps.size} targets"
            }
            val segments = List(steps) {
                NotificationCompat.ProgressStyle.Segment(STEP)
                    .setColor(if (signal.status == SignalStatus.LOST) RED else if (it < hit) GREEN else TRACK)
            }
            // A marker at each target, green once reached.
            val points = tps.mapIndexed { i, tp ->
                NotificationCompat.ProgressStyle.Point((i + 1) * STEP)
                    .setColor(if (tp.isHit) GREEN else TRACK)
            }
            return TradeProgress(max, value.coerceAtMost(max), color, label, segments, points)
        }
    }
}

