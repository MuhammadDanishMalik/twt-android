package com.talkswithtanha.twt

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.google.firebase.FirebaseApp
import com.talkswithtanha.twt.core.designsystem.Spacing
import com.talkswithtanha.twt.core.designsystem.TwtColors
import com.talkswithtanha.twt.core.designsystem.TwtTheme
import com.talkswithtanha.twt.core.designsystem.components.TwtScreen
import com.talkswithtanha.twt.core.notifications.FollowedSignalNotifier
import com.talkswithtanha.twt.core.navigation.RootScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * The signal a notification was tapped to open, if any.
     *
     * Held as state rather than read once, because the activity is
     * `singleTop`-launched from the notification: a member with the app already
     * open gets [onNewIntent] rather than a fresh [onCreate], and reading the
     * intent only at creation would make the tap do nothing at all in exactly
     * the case where the app was already in front of them.
     */
    private var pendingSignalId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        pendingSignalId = intent.signalId()

        // Every view model below this point depends on Firebase, so a missing
        // `google-services.json` has to be caught before the first one is
        // constructed -- otherwise the app dies inside Dagger with a stack trace
        // that names neither Firebase nor the file that is actually missing.
        val configured = FirebaseApp.getApps(this).isNotEmpty()

        setContent {
            TwtTheme {
                if (configured) {
                    RootScreen(
                        pendingSignalId = pendingSignalId,
                        onPendingSignalHandled = { pendingSignalId = null }
                    )
                } else {
                    NotConfiguredScreen()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingSignalId = intent.signalId()
    }

    private fun Intent.signalId(): String? =
        getStringExtra(FollowedSignalNotifier.EXTRA_SIGNAL_ID)?.takeIf { it.isNotBlank() }
}

/**
 * What a developer sees when the app is built without Firebase credentials.
 *
 * Deliberately a real screen rather than a crash. This build is meant to compile
 * and install for anybody who clones the repository, whether or not they have
 * been added to the Firebase project yet, and "it crashes on launch" is a much
 * worse way to learn that one file is missing.
 */
@Composable
private fun NotConfiguredScreen() {
    TwtScreen {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(Spacing.xl),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Firebase is not configured",
                style = MaterialTheme.typography.headlineMedium,
                color = TwtColors.TextPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                text = "app/google-services.json is missing.\n\n" +
                    "Firebase console → project twt-database-9be63 → Project settings → " +
                    "Add app → Android, with the package name com.talkswithtanha.twt. " +
                    "Download the file into app/ and rebuild.",
                style = MaterialTheme.typography.bodyLarge,
                color = TwtColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.md)
            )
        }
    }
}
