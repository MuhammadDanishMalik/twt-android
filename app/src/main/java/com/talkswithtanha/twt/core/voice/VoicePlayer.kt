package com.talkswithtanha.twt.core.voice

import android.media.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one voice note that is playing.
 *
 * A single player for the whole app, deliberately. Each card owning its own
 * `MediaPlayer` is how a feed ends up with three people talking over each other
 * — tap a second note and the first keeps going, out of sight, with no way to
 * stop it short of leaving the screen.
 *
 * Playback state is published rather than pushed at a listener, so a card that
 * scrolls away and comes back picks up wherever the note has got to.
 */
object VoicePlayer {

    data class State(
        /** The note being played, by its URL. Null when nothing is. */
        val url: String? = null,
        val isPlaying: Boolean = false,
        val positionMs: Int = 0,
        val durationMs: Int = 0,
        val isPreparing: Boolean = false
    ) {
        fun progressFor(url: String): Float =
            if (this.url != url || durationMs <= 0) 0f
            else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var player: MediaPlayer? = null

    /** Play this note, pause it if it is the one already playing. */
    fun toggle(url: String) {
        if (_state.value.url == url) {
            val current = player ?: return play(url)
            if (current.isPlaying) {
                runCatching { current.pause() }
                _state.value = _state.value.copy(isPlaying = false)
            } else {
                runCatching { current.start() }
                _state.value = _state.value.copy(isPlaying = true)
            }
            return
        }
        play(url)
    }

    private fun play(url: String) {
        release()
        _state.value = State(url = url, isPreparing = true)

        val instance = MediaPlayer()
        player = instance
        runCatching {
            instance.setDataSource(url)
            instance.setOnPreparedListener {
                // Guard against a note the member moved on from while this one
                // was still buffering: the old player must not start talking.
                if (player !== instance) return@setOnPreparedListener
                it.start()
                _state.value = State(
                    url = url,
                    isPlaying = true,
                    durationMs = it.duration,
                    isPreparing = false
                )
            }
            instance.setOnCompletionListener {
                if (player !== instance) return@setOnCompletionListener
                _state.value = _state.value.copy(isPlaying = false, positionMs = 0)
            }
            instance.setOnErrorListener { _, _, _ ->
                if (player === instance) {
                    release()
                    _state.value = State()
                }
                true
            }
            instance.prepareAsync()
        }.onFailure {
            release()
            _state.value = State()
        }
    }

    /** Moves the playhead. [fraction] is 0..1 of the note's length. */
    fun seek(fraction: Float) {
        val instance = player ?: return
        val duration = _state.value.durationMs
        if (duration <= 0) return
        val target = (duration * fraction.coerceIn(0f, 1f)).toInt()
        runCatching { instance.seekTo(target) }
        _state.value = _state.value.copy(positionMs = target)
    }

    /** Called on a timer by whatever is on screen; the player has no tick. */
    fun refreshPosition() {
        val instance = player ?: return
        if (!_state.value.isPlaying) return
        runCatching {
            _state.value = _state.value.copy(positionMs = instance.currentPosition)
        }
    }

    fun stop() {
        release()
        _state.value = State()
    }

    private fun release() {
        val instance = player
        player = null
        runCatching { instance?.stop() }
        runCatching { instance?.release() }
    }
}

/** `0:07`, `1:42` — how long a note is, and how far into it the member is. */
fun formatVoiceDuration(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    return "${safe / 60}:${(safe % 60).toString().padStart(2, '0')}"
}
