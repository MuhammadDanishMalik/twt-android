package com.talkswithtanha.twt.core.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.talkswithtanha.twt.R
import com.talkswithtanha.twt.core.data.PrefsRepository
import com.talkswithtanha.twt.core.session.SessionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the app sounds like when something needs the member, while it is open.
 *
 * Four sounds, by weight rather than by feature, so a member learns them:
 *
 *  - [Sound.IMPORTANT] — a rising three-note chime. A new signal, a deal that
 *    is waiting on them, the market opening.
 *  - [Sound.SUCCESS] — two bright notes up. A target hit, funds sent, a code
 *    accepted, a renewal approved.
 *  - [Sound.ALERT] — two firm low notes. A stop hit, two minutes left to pay,
 *    something refused.
 *  - [Sound.MESSAGE] — one soft blip. A reply in the chat.
 *
 * Played on the notification stream, so the phone's notification volume and
 * silent mode decide whether it is heard, exactly as for a push. Off entirely
 * when the member turns sounds off in Settings. When the app is in the
 * background nothing plays here — the push's own notification sound does that
 * job, and two sounds for one event is the thing that gets apps muted.
 */
@Singleton
class AppSounds @Inject constructor(
    @param:ApplicationContext private val context: Context,
    session: SessionRepository,
    prefs: PrefsRepository,
    scope: CoroutineScope
) {
    enum class Sound(val res: Int) {
        IMPORTANT(R.raw.snd_important),
        SUCCESS(R.raw.snd_success),
        ALERT(R.raw.snd_alert),
        MESSAGE(R.raw.snd_message)
    }

    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val ids = Sound.entries.associateWith { pool.load(context, it.res, 1) }

    @Volatile private var enabled = true
    @Volatile private var lastPlayedAt = 0L

    init {
        @OptIn(ExperimentalCoroutinesApi::class)
        scope.launch {
            session.currentUser
                .map { it?.id }
                .flatMapLatest { uid -> if (uid == null) flowOf(true) else prefs.observe(uid).map { it.sound } }
                .collect { enabled = it }
        }
    }

    /** True while the member has the app on screen. */
    val isForeground: Boolean
        get() = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    /**
     * Plays [sound] if sounds are on and the app is open. Several events
     * landing together (a burst of messages, a deal and its push) make one
     * sound, not a pile of them.
     */
    fun play(sound: Sound) {
        if (!enabled || !isForeground) return
        val now = System.currentTimeMillis()
        if (now - lastPlayedAt < 700) return
        lastPlayedAt = now
        ids[sound]?.let { pool.play(it, 1f, 1f, 1, 0, 1f) }
    }
}

/** For composables that need a sound but have no view model to carry one. */
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface AppSoundsEntryPoint {
    fun appSounds(): AppSounds
}

@androidx.compose.runtime.Composable
fun rememberAppSounds(): AppSounds {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    return androidx.compose.runtime.remember(context) {
        dagger.hilt.android.EntryPointAccessors.fromApplication(context, AppSoundsEntryPoint::class.java).appSounds()
    }
}
