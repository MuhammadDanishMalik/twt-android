package com.talkswithtanha.twt.core.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.talkswithtanha.twt.core.model.Signal
import com.talkswithtanha.twt.core.model.SignalFollow
import com.talkswithtanha.twt.core.model.User
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app's half of the widgets: turns what the stores already hold into a
 * [WidgetSnapshot], saves it, and asks Glance to redraw.
 *
 * Called from `FollowedSignalTracker`, which is already the one place watching
 * the feed, the member's follows and the signed-in account together — so the
 * widget is never telling a different story from the app.
 *
 * ### Only when something changed
 *
 * The signals listener fires on every document touch, including ones that change
 * nothing a widget shows (a follower count, say). Redrawing a widget is cheap
 * but not free, and a redraw loop on a home screen is a battery complaint, so
 * the snapshot is compared and Glance is only woken for a real difference.
 */
@Singleton
class WidgetPublisher @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    /** How many signals the widgets are handed. The list widget shows four. */
    private val limit = 8

    suspend fun publish(user: User?, signals: List<Signal>, follows: List<SignalFollow>) {
        if (user == null) {
            // Signing out clears it. A phone can be handed on, and the next
            // person must not find the last one's trades on the home screen.
            if (WidgetStore.save(context, WidgetSnapshot.SIGNED_OUT)) redraw()
            return
        }

        val followed = follows.filter { it.isActive }.map { it.signalId }.toSet()
        val ongoing = signals.filter { it.status.isOngoing }

        // Followed live trades → the rest of the live feed → the latest closed.
        val ordered = ongoing.filter { it.id in followed } +
            ongoing.filterNot { it.id in followed } +
            signals.filterNot { it.status.isOngoing }

        val snapshot = WidgetSnapshot(
            signals = ordered.take(limit).map { signal ->
                WidgetSignal(
                    id = signal.id,
                    pair = signal.pair,
                    direction = signal.type.stored,
                    entry = signal.entryPrice,
                    stopLoss = signal.stopLoss,
                    targets = signal.takeProfits.map {
                        WidgetTarget(label = it.label, price = it.price, isHit = it.isHit)
                    },
                    status = signal.status.stored,
                    pips = signal.pipsGained,
                    postedAt = signal.timestamp,
                    isFollowed = signal.id in followed
                )
            },
            access = WidgetAccess(
                holderName = user.fullName,
                expiresAt = user.membershipExpiresAt,
                grantedAt = user.accessGrantedAt
            ),
            isSignedIn = true
        )

        if (WidgetStore.save(context, snapshot)) redraw()
    }

    private suspend fun redraw() {
        // Both widgets read the same snapshot, so both are refreshed. Which of
        // them is actually on a home screen is the launcher's business.
        SignalsWidget().updateAll(context)
        AccessCardWidget().updateAll(context)
    }
}
