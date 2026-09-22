package com.talkswithtanha.twt.core.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.os.Build
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
 * ### What the server actually sends
 *
 * The admin panel's `/api/notify-signal` route posts through `send-push.ts`,
 * which sends a `notification` block *and* a `data` block of
 * `{ signalId, kind, status }`, where `kind` is `published`, `status` or
 * `updated`. Those key names are the contract; they are read here and nowhere
 * else in the app.
 *
 * A message carrying a `notification` block is handled by the system tray
 * whenever the app is backgrounded or dead — this class is not called at all in
 * that case, which is why the manifest carries the default icon, colour and
 * channel. [onMessageReceived] therefore runs only in the foreground, and its
 * job is to post the same notification the tray would have, in the right
 * channel, rather than letting a foreground arrival pass silently.
 */
@AndroidEntryPoint
class TwtMessagingService : FirebaseMessagingService() {

    @Inject lateinit var session: SessionRepository
    @Inject lateinit var scope: CoroutineScope

    /**
     * Fires when FCM mints a token: first launch, an app data clear, a restore
     * onto a new phone. Registration is `arrayUnion` onto `users/{uid}.fcmTokens`,
     * which is the array `tokensFor()` reads on the server.
     *
     * A signed-out device has nobody to register against; `PushTokenRegistrar`
     * covers that case at the next sign-in.
     */
    override fun onNewToken(token: String) {
        scope.launch { session.registerPushToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val signalId = data[KEY_SIGNAL_ID]

        val title = message.notification?.title ?: data[KEY_TITLE] ?: return
        val body = message.notification?.body ?: data[KEY_BODY].orEmpty()

        // A new signal goes to everyone with a live code; a status change or an
        // edit is only ever sent to the members the fan-out found in
        // `signalFollows`, so anything arriving with those kinds is already
        // meant for this person.
        val channel = when (data[KEY_KIND]) {
            KIND_STATUS, KIND_UPDATED -> NotificationChannels.SIGNAL_ALERTS
            else -> NotificationChannels.GENERAL
        }

        post(channel, title, body, signalId)
    }

    private fun post(channel: String, title: String, body: String, signalId: String?) {
        if (!canPost()) return

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            // The same key the system tray puts on the intent when it handles
            // one of these itself, so a tap opens the signal either way.
            signalId?.let { putExtra(KEY_SIGNAL_ID, it) }
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
            .setPriority(
                if (channel == NotificationChannels.SIGNAL_ALERTS) {
                    NotificationCompat.PRIORITY_HIGH
                } else {
                    NotificationCompat.PRIORITY_DEFAULT
                }
            )
            .setContentIntent(pending)
            .build()

        // Checked above, and caught here anyway: the permission can be revoked
        // between the two, and the throw would land on FCM's delivery thread.
        try {
            NotificationManagerCompat.from(this)
                .notify((signalId ?: title).hashCode(), notification)
        } catch (e: SecurityException) {
            Log.i(TAG, "Notifications are not permitted; dropping this push", e)
        }
    }

    private fun canPost(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TAG = "TwtMessagingService"

        /** The keys `send-push.ts` writes. Changing either side breaks both. */
        const val KEY_SIGNAL_ID = "signalId"
        const val KEY_KIND = "kind"
        const val KEY_TITLE = "title"
        const val KEY_BODY = "body"

        const val KIND_STATUS = "status"
        const val KIND_UPDATED = "updated"
    }
}
