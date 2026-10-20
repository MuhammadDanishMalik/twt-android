package com.talkswithtanha.twt.core.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Recording a voice note.
 *
 * ## Why AAC in an MP4 container
 *
 * It is the one combination every Android since Jelly Bean can record, every
 * browser can play, and Cloudinary accepts without transcoding. The obvious
 * alternative — 3GPP/AMR — is half the size and sounds like a phone call from
 * 2005, which is the wrong impression for a note somebody is meant to trust
 * with their money.
 *
 * 64 kbps mono at 44.1 kHz: a minute costs about 480 KB, and speech gains
 * nothing from stereo or from a higher rate.
 *
 * ## Why the file lives in the cache
 *
 * It is a staging area, not a recording. Once Cloudinary has it the local copy
 * is deleted, and if the app dies mid-post the system can reclaim it — an
 * abandoned recording should not sit in a member's storage forever.
 */
class VoiceRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    val isRecording: Boolean get() = recorder != null

    /** Seconds recorded so far. Polled by the UI; the recorder has no callback. */
    val elapsedSeconds: Int
        get() = if (startedAt == 0L) 0 else ((System.currentTimeMillis() - startedAt) / 1000).toInt()

    fun start(): Boolean {
        stop()
        val target = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")

        @Suppress("DEPRECATION")
        val instance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }

        return runCatching {
            instance.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64_000)
                setAudioSamplingRate(44_100)
                setAudioChannels(1)
                setOutputFile(target.absolutePath)
                prepare()
                start()
            }
            recorder = instance
            file = target
            startedAt = System.currentTimeMillis()
            true
        }.getOrElse {
            runCatching { instance.release() }
            target.delete()
            false
        }
    }

    /**
     * Ends the recording and returns the file, or null if nothing usable came
     * of it.
     *
     * `stop()` throws when it is called before any audio was written — a tap
     * that lands on stop a few milliseconds after start — and the exception
     * leaves a zero-byte file behind. Both are handled here rather than by the
     * caller, which only wants to know whether there is a note or not.
     */
    fun stop(): Recording? {
        val instance = recorder ?: return null
        val target = file
        val seconds = elapsedSeconds

        recorder = null
        file = null
        startedAt = 0L

        val ended = runCatching { instance.stop() }.isSuccess
        runCatching { instance.release() }

        if (!ended || target == null || !target.exists() || target.length() == 0L) {
            target?.delete()
            return null
        }
        return Recording(target, seconds.coerceAtLeast(1))
    }

    /** Throws the recording away — the bin button, and every error path. */
    fun cancel() {
        stop()?.file?.delete()
    }

    data class Recording(val file: File, val seconds: Int)
}
