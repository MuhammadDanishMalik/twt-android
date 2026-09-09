package com.talkswithtanha.twt.core.model

import java.util.Date

/**
 * A lesson.
 *
 * The academy is YouTube-only — nothing is uploaded — so [youtubeId] is the
 * whole of the video, and the thumbnail is derived from it rather than stored in
 * a second field that has to be kept in sync.
 */
data class AcademyVideo(
    val id: String,
    val title: String,
    val description: String? = null,
    /** Stored in a field called `videoUrl`. See `AcademyVideoField.VIDEO_ID`. */
    val youtubeId: String,
    val category: String? = null,
    val level: String? = null,
    /** Seconds. */
    val duration: Int = 0,
    val xpReward: Int = 0,
    val createdAt: Date = Date()
) {
    val thumbnailUrl: String get() = "https://img.youtube.com/vi/$youtubeId/hqdefault.jpg"

    val watchUrl: String get() = "https://www.youtube.com/watch?v=$youtubeId"

    val durationLabel: String?
        get() = duration.takeIf { it > 0 }?.let { "%d:%02d".format(it / 60, it % 60) }
}
