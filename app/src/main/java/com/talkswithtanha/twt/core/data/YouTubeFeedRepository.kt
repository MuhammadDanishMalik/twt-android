package com.talkswithtanha.twt.core.data

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** One upload from Tanha's channel. */
data class ChannelVideo(
    val id: String,
    val title: String,
    val publishedAt: Date,
    /** Null when YouTube did not report a count — a premiere, or a new upload. */
    val views: Long?,
    val isShort: Boolean
) {
    val thumbnailUrl: String get() = "https://i.ytimg.com/vi/$id/hqdefault.jpg"
}

/**
 * Tanha's latest uploads, read from the channel's public feed.
 *
 * `youtube.com/feeds/videos.xml` is the channel's RSS: the newest 15 uploads,
 * with titles, dates and view counts, and Shorts linked as `/shorts/`. It needs
 * no API key and no quota, and it updates the moment he publishes — unlike the
 * fixed list of video ids the YouTube hub started with, which went stale the
 * week it was written.
 */
@Singleton
class YouTubeFeedRepository @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    private var cache: Pair<Long, List<ChannelVideo>>? = null

    /** Newest first. Served from memory for [CACHE_MS] between calls. */
    suspend fun latest(force: Boolean = false): Result<List<ChannelVideo>> = withContext(Dispatchers.IO) {
        cache?.let { (at, videos) ->
            if (!force && System.currentTimeMillis() - at < CACHE_MS) return@withContext Result.success(videos)
        }
        runCatching {
            val request = Request.Builder().url(FEED_URL).build()
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "YouTube feed ${response.code}" }
                parse(response.body!!.byteStream())
            }
        }.onSuccess { cache = System.currentTimeMillis() to it }
    }

    private fun parse(stream: java.io.InputStream): List<ChannelVideo> {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(stream, null)
        }
        val videos = mutableListOf<ChannelVideo>()
        var inEntry = false
        var id = ""
        var title = ""
        var published = ""
        var link = ""
        var views: Long? = null

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "entry" -> {
                        inEntry = true
                        id = ""; title = ""; published = ""; link = ""; views = null
                    }
                    "yt:videoId" -> if (inEntry) id = parser.nextText().trim()
                    "title" -> if (inEntry && title.isEmpty()) title = parser.nextText().trim()
                    "published" -> if (inEntry) published = parser.nextText().trim()
                    "link" -> if (inEntry) link = parser.getAttributeValue(null, "href").orEmpty()
                    "media:statistics" -> if (inEntry) {
                        views = parser.getAttributeValue(null, "views")?.toLongOrNull()?.takeIf { it > 0 }
                    }
                }
                XmlPullParser.END_TAG -> if (parser.name == "entry" && inEntry) {
                    inEntry = false
                    if (id.isNotEmpty() && title.isNotEmpty()) {
                        videos += ChannelVideo(
                            id = id,
                            title = title,
                            publishedAt = parseDate(published),
                            views = views,
                            isShort = "/shorts/" in link
                        )
                    }
                }
            }
        }
        return videos
    }

    private fun parseDate(value: String): Date = runCatching {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .parse(value)
    }.getOrNull() ?: Date()

    companion object {
        /** @Tradewithtanha001 */
        const val CHANNEL_ID = "UCpQy4o-hFyY799BQ4uT-mxw"
        const val CHANNEL_URL = "https://www.youtube.com/@Tradewithtanha001"
        private const val FEED_URL = "https://www.youtube.com/feeds/videos.xml?channel_id=$CHANNEL_ID"
        private const val CACHE_MS = 10 * 60 * 1000L
    }
}
