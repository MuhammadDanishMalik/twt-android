package com.talkswithtanha.twt.core.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.talkswithtanha.twt.core.session.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Makes sure the account that is signed in right now owns this device's FCM
 * token.
 *
 * `TwtMessagingService.onNewToken` is not enough on its own, and the gap is easy
 * to miss: it fires when FCM *mints* a token — first launch, an app data clear,
 * a restore onto a new phone — and at no other time. So the common case of a
 * second account signing in on a device that already has a token registers
 * nothing at all, and that member simply never receives a push. On a phone that
 * holds two accounts, which the `fcmTokens` array exists to support, that is the
 * normal case rather than an edge one.
 *
 * Watching the signed-in user and asserting the token at each change covers both
 * paths.
 */
@Singleton
class PushTokenRegistrar @Inject constructor(
    private val messaging: FirebaseMessaging,
    private val session: SessionRepository,
    private val scope: CoroutineScope
) {

    fun start() {
        scope.launch {
            session.currentUser
                .filterNotNull()
                // Keyed on the uid, not the whole user. The profile listener
                // fires on every change to the document — including the write
                // this makes — and reacting to all of them would be a loop.
                .distinctUntilChanged { old, new -> old.id == new.id }
                .collect { user ->
                    val token = runCatching { messaging.token.await() }
                        .onFailure { Log.w(TAG, "Could not read the FCM token", it) }
                        .getOrNull()
                        ?: return@collect

                    // `arrayUnion` is idempotent, so re-writing an existing
                    // token is harmless — but it is still a write against a
                    // document a listener is watching, so it is skipped when
                    // the token is already there.
                    if (token in user.fcmTokens) return@collect

                    session.registerPushToken(token)
                }
        }
    }

    private companion object {
        const val TAG = "PushTokenRegistrar"
    }
}
