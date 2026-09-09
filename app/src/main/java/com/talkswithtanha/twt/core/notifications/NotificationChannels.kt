package com.talkswithtanha.twt.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService

/**
 * The app's notification channels.
 *
 * Three, not one, because Android lets a member turn each off separately and
 * these are genuinely different things to want. Somebody who mutes the community
 * chatter must still hear that their stop loss was hit.
 */
object NotificationChannels {

    /** A followed trade moved: a target hit, a stop taken. Deliberately noisy. */
    const val SIGNAL_ALERTS = "signal_alerts"

    /** The ongoing card for a followed trade. Silent by design — it updates
     *  often, and a buzz on every price move is a reason to uninstall. */
    const val FOLLOWED_SIGNAL = "followed_signal"

    /** New signals published, and community replies. */
    const val GENERAL = "general"

    fun register(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService<NotificationManager>() ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                SIGNAL_ALERTS,
                "Signal alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Targets and stop losses on trades you are following."
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                FOLLOWED_SIGNAL,
                "Followed trade",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "The live card for a trade you are following."
                setShowBadge(false)
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                GENERAL,
                "New signals and messages",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "New signals from Tanha, and replies in the community."
            }
        )
    }
}
