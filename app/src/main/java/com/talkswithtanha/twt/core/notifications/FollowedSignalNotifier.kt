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

        val notification = NotificationCompat.Builder(context, NotificationChannels.FOLLOWED_SIGNAL)
            .setSmallIcon(R.drawable.ic_stat_signal)
            .setContentTitle("${signal.pair} · ${signal.type.stored}")
            .setContentText(summary(signal))
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail(signal, follow)))
            .setOngoing(signal.status.isOngoing)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(openSignal(signal.id))
            .build()

        manager.notify(notificationId(signal.id), notification)
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
        manager.notify(
            notificationId("${signal.id}:${signal.status.stored}"),
            notification
        )
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
        const val EXTRA_SIGNAL_ID = "twt.signalId"
    }
}
