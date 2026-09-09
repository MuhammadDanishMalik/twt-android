package com.talkswithtanha.twt.core.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.talkswithtanha.twt.MainActivity
import com.talkswithtanha.twt.R
import com.talkswithtanha.twt.core.session.SessionRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Push, which is the half of alerting that covers a phone with the app closed.
 *
 * The in-app half lives in `FollowedSignalTracker` and is complete on its own —
 * this only has to deliver the same payload rather than duplicate any of the
 * logic. Payloads are sent as **data messages** rather than `notification`
 * blocks so this runs for them in every app state; a `notification` block is
 * swallowed by the system while the app is backgrounded and never reaches code
 * that could decide whether this member actually follows the trade.
 */
@AndroidEntryPoint
class TwtMessagingService : FirebaseMessagingService() {

    @Inject lateinit var session: SessionRepository
    @Inject lateinit var scope: CoroutineScope

    /**
     * Fires when FCM mints a token: first launch, an app data clear, a restore
     * onto a new phone.
     *
     * Registration is `arrayUnion` onto `users/{uid}.fcmTokens`, so one phone
     * holding two accounts adds a token to each rather than the second account
     * stealing it from the first.
     */
    override fun onNewToken(token: String) {
        scope.launch { session.registerPushToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val signalId = data[KEY_SIGNAL_ID]

        val title = data[KEY_TITLE] ?: message.notification?.title ?: return
        val body = data[KEY_BODY] ?: message.notification?.body.orEmpty()

        // New-signal broadcasts go to everybody; a status change is only ever
        // sent to the members the fan-out found in `signalFollows`, so anything
        // arriving here with a signal id is already meant for this person.
        val channel = when (data[KEY_TYPE]) {
            TYPE_STATUS_CHANGE -> NotificationChannels.SIGNAL_ALERTS
            else -> NotificationChannels.GENERAL
        }

        post(channel, title, body, signalId)
    }

    private fun post(channel: String, title: String, body: String, signalId: String?) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
        ) return

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            signalId?.let { putExtra(FollowedSignalNotifier.EXTRA_SIGNAL_ID, it) }
        }
        val pending = PendingIntent.getActivity(
            this,
            (signalId ?: title).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_stat_signal)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        NotificationManagerCompat.from(this)
            .notify((signalId ?: title).hashCode(), notification)
    }

    private companion object {
        const val KEY_TYPE = "type"
        const val KEY_TITLE = "title"
        const val KEY_BODY = "body"
        const val KEY_SIGNAL_ID = "signalId"
        const val TYPE_STATUS_CHANGE = "signalStatusChange"
    }
}
