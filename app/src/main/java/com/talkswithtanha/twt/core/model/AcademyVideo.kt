package com.talkswithtanha.twt.core.model

import java.util.Date

/** A lesson in the academy. Hosted on YouTube; the app only stores the id. */
data class AcademyVideo(
    val id: String,
    val title: String,
    val description: String? = null,
    val youtubeId: String,
    val category: String? = null,
    val level: String? = null,
    val durationSeconds: Int? = null,
    val order: Int = 0,
    val createdAt: Date = Date()
) {
    /**
     * Derived rather than stored. YouTube serves a thumbnail for every video at a
     * predictable address, and a `thumbnailURL` field that the admin panel has to
     * remember to fill in is a field that is sometimes empty.
     */
    val thumbnailUrl: String get() = "https://img.youtube.com/vi/$youtubeId/hqdefault.jpg"

    val watchUrl: String get() = "https://www.youtube.com/watch?v=$youtubeId"

    val durationLabel: String?
        get() = durationSeconds?.let {
            val minutes = it / 60
            val seconds = it % 60
            "%d:%02d".format(minutes, seconds)
        }
}
