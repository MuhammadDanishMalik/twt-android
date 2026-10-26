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
import com.talkswithtanha.twt.core.data.DealRepository
import com.talkswithtanha.twt.core.data.Snapshot
import com.talkswithtanha.twt.core.model.Deal
import com.talkswithtanha.twt.core.model.DealStatus
import com.talkswithtanha.twt.core.model.progress
import com.talkswithtanha.twt.core.session.SessionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A live card in the shade for each open deal, the same idea as the followed
 * trade's: four steps — paid, confirmed, sending, sent — filled as the desk
 * moves the deal on, with the clock when somebody is on a deadline.
 *
 * The card is silent; the deal watcher's push is what buzzes. When a deal
 * finishes the card becomes a plain one that can be swiped away ("Funds
 * sent"), and a cancelled deal's card is simply removed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class DealTracker @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val session: SessionRepository,
    private val deals: DealRepository
) {
    // One at a time: the listener and refresh() both reconcile.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val manager = NotificationManagerCompat.from(context)
    private var started = false

    /** Status last drawn per deal, so an unchanged snapshot does not repost. */
    private val drawn = mutableMapOf<String, DealStatus>()

    /** The latest deals, for [refresh]. */
    private var last: List<Deal> = emptyList()

    fun start() {
        if (started) return
        started = true
        scope.launch {
            session.currentUser
                .flatMapLatest { u ->
                    // Loading and errors keep the cards as they are; signing out clears them.
                    if (u == null) flowOf(null) else deals.observeDeals(u.id).mapNotNull { (it as? Snapshot.Data)?.value }
                }
                .collect { list -> if (list == null) clear() else reconcile(list) }
        }
    }

    /**
     * Draws every open deal's card again: after the member turns notifications
     * on, and whenever a deal screen opens, so a card swiped away or never
     * posted comes back.
     */
    fun refresh() {
        scope.launch {
            drawn.clear()
            reconcile(last)
        }
    }

    private fun reconcile(list: List<Deal>) {
        last = list
        // Nothing can be posted: draw nothing and remember nothing, so the
        // cards appear the moment permission is given.
        if (!canPost()) return
        for (deal in list) {
            val before = drawn[deal.id]
            if (before == deal.status) continue
            when {
                // Only cards for deals that were open while the app was
                // watching: a deal completed last month gets no card.
                deal.status == DealStatus.COMPLETED -> if (before != null) show(deal)
                deal.status == DealStatus.CANCELLED -> manager.cancel(id(deal.id))
                else -> show(deal)
            }
            drawn[deal.id] = deal.status
        }
    }

    private fun clear() {
        drawn.keys.forEach { manager.cancel(id(it)) }
        drawn.clear()
    }

    private fun show(deal: Deal) {
        if (!canPost()) return
        val p = deal.progress()
        val step = deal.status.stepIndex
        val done = deal.status == DealStatus.COMPLETED
        val color = if (deal.status == DealStatus.DISPUTED) AMBER else GREEN

        val builder = NotificationCompat.Builder(context, NotificationChannels.DEAL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_signal)
            .setContentTitle(if (done) "Deal ${deal.reference} · Funds sent ✓" else "Deal ${deal.reference} · ${deal.status.title}")
            .setContentText(p.headline)
            .setSubText("${deal.payLabel()} → ${deal.receiveLabel()}")
            .setStyle(NotificationCompat.BigTextStyle().bigText("${p.headline}\n${p.next}"))
            .setOngoing(!done)
            .setAutoCancel(done)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setColor(color)
            .setContentIntent(openDeal(deal.id))

        // Whose clock is running, counting down in the shade.
        p.deadline?.takeIf { !done && it.time > System.currentTimeMillis() }?.let {
            builder.setWhen(it.time).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
        }

        val value = ((step ?: 0) * STEP).coerceAtLeast(STEP / 8)
        if (android.os.Build.VERSION.SDK_INT >= 36) {
            val style = NotificationCompat.ProgressStyle()
                .setStyledByProgress(false)
                .setProgress(value)
            repeat(STEPS) { i ->
                style.addProgressSegment(
                    NotificationCompat.ProgressStyle.Segment(STEP).setColor(if (step != null && i < step) color else TRACK)
                )
                style.addProgressPoint(
                    NotificationCompat.ProgressStyle.Point((i + 1) * STEP).setColor(if (step != null && i < step) color else TRACK)
                )
            }
            builder.setStyle(style)
                .setRequestPromotedOngoing(!done)
                .setShortCriticalText(chip(deal.status))
        } else {
            builder.setProgress(STEPS * STEP, value, false)
        }

        try {
            manager.notify(id(deal.id), builder.build())
        } catch (e: SecurityException) {
            android.util.Log.i(TAG, "Notifications are not permitted; skipping", e)
        }
    }

    private fun chip(status: DealStatus) = when (status) {
        DealStatus.AWAITING_PAYMENT -> "PAY"
        DealStatus.PAYMENT_SENT -> "CHECK"
        DealStatus.PAYMENT_APPROVED -> "PAID"
        DealStatus.RELEASING -> "SEND"
        DealStatus.COMPLETED -> "DONE"
        DealStatus.CANCELLED -> "OFF"
        DealStatus.DISPUTED -> "REVIEW"
    }

    private fun openDeal(dealId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            // The same extra the deal watcher's push carries.
            putExtra("dealId", dealId)
        }
        return PendingIntent.getActivity(
            context, id(dealId), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun canPost(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED ||
            android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU

    private fun id(dealId: String) = "deal:$dealId".hashCode()

    private companion object {
        const val TAG = "DealTracker"
        const val STEPS = 4
        const val STEP = 100
        const val GREEN = 0xFF33C759.toInt()
        const val AMBER = 0xFFFF9F0A.toInt()
        const val TRACK = 0xFF5A5A5A.toInt()
    }
}
