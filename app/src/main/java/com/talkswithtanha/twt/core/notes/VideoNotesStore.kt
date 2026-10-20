package com.talkswithtanha.twt.core.notes

import android.content.Context
import com.talkswithtanha.twt.core.model.formatVideoTime
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** A note taken at a moment in a lesson video. */
data class VideoNote(
    val id: String,
    /** The YouTube id. */
    val videoId: String,
    val videoTitle: String,
    /** Seconds into the video. */
    val timestamp: Double,
    val text: String,
    val createdAt: Long
) {
    val formattedTimestamp: String get() = formatVideoTime(timestamp)
}

/**
 * The member's lesson notes, kept on this phone — the Android half of iOS's
 * `NotesManager`, which keeps them in UserDefaults. Private study notes, so
 * they are never uploaded; sharing one into a chat copies it into the message.
 *
 * Unlike iOS, the video's title is stored with each note, so the share sheet
 * can name the lesson without a hard-coded list of titles.
 */
@Singleton
class VideoNotesStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("video_notes", Context.MODE_PRIVATE)

    private val _notes = MutableStateFlow(load())
    val notes: StateFlow<List<VideoNote>> = _notes.asStateFlow()

    fun notesFor(videoId: String): List<VideoNote> =
        _notes.value.filter { it.videoId == videoId }.sortedBy { it.timestamp }

    fun add(videoId: String, videoTitle: String, timestamp: Double, text: String) {
        val note = VideoNote(
            id = UUID.randomUUID().toString(),
            videoId = videoId,
            videoTitle = videoTitle,
            timestamp = timestamp,
            text = text.trim(),
            createdAt = System.currentTimeMillis()
        )
        _notes.update { it + note }
        save()
    }

    fun delete(id: String) {
        _notes.update { list -> list.filterNot { it.id == id } }
        save()
    }

    private fun save() {
        val array = JSONArray()
        _notes.value.forEach { n ->
            array.put(
                JSONObject()
                    .put("id", n.id)
                    .put("videoId", n.videoId)
                    .put("videoTitle", n.videoTitle)
                    .put("timestamp", n.timestamp)
                    .put("text", n.text)
                    .put("createdAt", n.createdAt)
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private fun load(): List<VideoNote> = runCatching {
        val array = JSONArray(prefs.getString(KEY, "[]"))
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            VideoNote(
                id = o.getString("id"),
                videoId = o.getString("videoId"),
                videoTitle = o.optString("videoTitle"),
                timestamp = o.getDouble("timestamp"),
                text = o.getString("text"),
                createdAt = o.optLong("createdAt")
            )
        }
    }.getOrDefault(emptyList())

    private companion object {
        const val KEY = "notes"
    }
}
