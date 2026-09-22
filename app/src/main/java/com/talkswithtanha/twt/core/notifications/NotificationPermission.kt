package com.talkswithtanha.twt.core.notifications

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri

/**
 * Asking for notifications, once, at a moment that makes sense.
 *
 * Android gives an app **one** prompt: decline it and the system refuses to
 * show it again, and the only route back is a trip into system settings that
 * almost nobody makes. So the prompt is spent deliberately rather than fired at
 * launch, when a member has not yet seen anything worth being told about.
 *
 * There are two good moments, and the app uses both:
 *
 *  - **Following a trade.** The member has just asked to be told what happens
 *    to it, so the prompt answers a question they just raised.
 *  - **The Notifications row in Settings**, where asking is the obvious
 *    meaning of a tap.
 *
 * Once the system will no longer show the dialog, the only honest thing left is
 * to open the app's notification settings, which is what [openSettings] does.
 */
object NotificationPermission {

    /** Below 33 the permission is granted at install time. */
    val isRuntimePermission: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun isGranted(context: Context): Boolean =
        !isRuntimePermission || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Whether the system will still show the dialog.
     *
     * `shouldShowRequestPermissionRationale` is false both before the first ask
     * and after a permanent denial, so it cannot be used alone. Pairing it with
     * "have we asked before" — which the app records itself — separates the two.
     */
    fun canAsk(activity: Activity, hasAskedBefore: Boolean): Boolean = when {
        !isRuntimePermission -> false
        isGranted(activity) -> false
        !hasAskedBefore -> true
        else -> ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.POST_NOTIFICATIONS
        )
    }

    /** The app's own notification settings, for when the dialog is spent. */
    fun openSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        runCatching { context.startActivity(intent) }
            .onFailure {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData("package:${context.packageName}".toUri())
                    )
                }
            }
    }
}

/**
 * A request you can fire from a composable.
 *
 * Returns a lambda that asks if the system will still show the dialog, and
 * falls back to opening settings when it will not — so a caller never has to
 * work out which of those two it should be doing.
 */
@Composable
fun rememberNotificationPermissionRequest(
    onResult: (granted: Boolean) -> Unit = {}
): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> onResult(granted) }

    return remember(context) {
        {
            when {
                NotificationPermission.isGranted(context) -> onResult(true)
                NotificationPermission.isRuntimePermission ->
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else -> NotificationPermission.openSettings(context)
            }
        }
    }
}
