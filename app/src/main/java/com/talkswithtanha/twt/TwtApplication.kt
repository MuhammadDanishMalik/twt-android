package com.talkswithtanha.twt

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.talkswithtanha.twt.core.notifications.NotificationChannels
import com.talkswithtanha.twt.core.notifications.PushTokenRegistrar
import com.talkswithtanha.twt.core.signals.FollowedSignalTracker
import com.talkswithtanha.twt.core.session.SessionRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent

@HiltAndroidApp
class TwtApplication : Application() {

    /**
     * The session is pulled out of the Hilt graph *here*, rather than being an
     * `@Inject lateinit var` field on this class.
     *
     * Field injection on an Application happens inside `super.onCreate()` —
     * before any line of the body below runs. `SessionRepository` depends on
     * `FirebaseAuth` and `FirebaseFirestore`, so injecting it as a field
     * constructs both of those before there is any chance to check whether
     * Firebase is configured at all. Without `google-services.json` that throws
     * out of `super.onCreate()`, and the app dies with a Dagger stack trace that
     * mentions neither Firebase nor the missing file.
     *
     * Resolving it through an entry point after the check keeps it exactly as
     * application-scoped as before — it is still the same `@Singleton`, still
     * outliving every screen — while letting the failure be a sentence instead
     * of a crash.
     */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SessionEntryPoint {
        fun session(): SessionRepository
        fun pushTokens(): PushTokenRegistrar
        fun followedSignals(): FollowedSignalTracker
    }

    override fun onCreate() {
        super.onCreate()

        // Returns null when there is no `google-services.json`: the plugin that
        // generates the string resources Firebase reads is only applied when
        // that file is present.
        if (FirebaseApp.initializeApp(this) == null) {
            Log.e(
                TAG,
                "Firebase is not configured: app/google-services.json is missing. " +
                    "Add the Android app `com.talkswithtanha.twt` in the Firebase console " +
                    "(project twt-database-9be63), download the file into app/, and rebuild. " +
                    "The app will run, but nothing that talks to the network will work."
            )
            return
        }

        NotificationChannels.register(this)

        // Attached for as long as the process lives. This is what notices a code
        // being redeemed or revoked from the admin panel; a listener owned by a
        // screen would die with that screen.
        val entryPoint =
            EntryPointAccessors.fromApplication(this, SessionEntryPoint::class.java)
        entryPoint.session().start()
        entryPoint.pushTokens().start()
        // Watches followed trades for the life of the process, so an alert does
        // not depend on the member having opened a signals screen this session.
        entryPoint.followedSignals().start()
    }

    private companion object {
        const val TAG = "TwtApplication"
    }
}
