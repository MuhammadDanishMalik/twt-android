package com.talkswithtanha.twt.features.media

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import com.talkswithtanha.twt.core.designsystem.Haptics
import com.talkswithtanha.twt.core.designsystem.IosColors

/**
 * The channel, as `YouTubeHubView.swift` lays it out: the latest live session,
 * the most-viewed analysis, and the shorts.
 *
 * The video list is hardcoded here exactly as it is on iOS. That is not a
 * placeholder — these are real ids on Tanha's channel, and the two apps are
 * meant to show the same shelf. The one thing to keep an eye on is the view
 * counts, which are fixed strings on both platforms and will drift; if they
 * ever start to matter, they should come from the YouTube Data API rather than
 * from a literal.
 */
private data class HubVideo(
    val id: String,
    val title: String,
    val views: String,
    val url: String
) {
    val thumbnail: String get() = "https://img.youtube.com/vi/$id/hqdefault.jpg"
}

private const val CHANNEL_URL = "https://www.youtube.com/@Tradewithtanha001"

private val latestLive = HubVideo(
    id = "020N8oIgUdE",
    title = "Gold Outlook - Live Trading Session",
    views = "Live Replay",
    url = "https://www.youtube.com/watch?v=020N8oIgUdE"
)

private val topVideos = listOf(
    HubVideo("JNvhbJNB0u8", "Top Forex Strategies 2026", "120K Views", "https://www.youtube.com/watch?v=JNvhbJNB0u8"),
    HubVideo("aunDkAPX9I0", "Mastering XAUUSD Breakouts", "85K Views", "https://www.youtube.com/watch?v=aunDkAPX9I0"),
    HubVideo("CA-UJk51IkQ", "Risk Management Masterclass", "72K Views", "https://www.youtube.com/watch?v=CA-UJk51IkQ")
)

private val shorts = listOf(
    HubVideo("2u5BbJQ5SVY", "Gold Trade Update", "1M Views", "https://www.youtube.com/shorts/2u5BbJQ5SVY"),
    HubVideo("rTYL2p58_0w", "Quick Tip: Entries", "850K Views", "https://www.youtube.com/shorts/rTYL2p58_0w"),
    HubVideo("E7YUR-tBsnY", "Stop Loss Secrets", "620K Views", "https://www.youtube.com/shorts/E7YUR-tBsnY"),
    HubVideo("34dACxqRLGk", "Live Profit Snippet", "500K Views", "https://www.youtube.com/shorts/34dACxqRLGk"),
    HubVideo("W2pMJoZYEz4", "Morning Outlook", "450K Views", "https://www.youtube.com/shorts/W2pMJoZYEz4")
)

@Composable
fun MediaHubScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    fun open(url: String) {
        Haptics.tap(haptics)
        // Handed to the YouTube app when it is installed, which is a better
        // player than anything worth embedding here.
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(IosColors.Background)
            .statusBarsPadding()
    ) {
        LazyColumn(
            // Clears the floating back button, which sits over the list rather
            // than in a bar of its own.
            contentPadding = PaddingValues(top = 52.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(IosColors.Accent),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "TWT",
                            color = Color.Black,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Trade with Tanha",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "@Tradewithtanha001",
                            color = IosColors.TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(Color(0xFFFF3B30))
                            .clickable { open(CHANNEL_URL) }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            "Subscribe",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            item {
                SectionTitle("Latest Live Session")
                Spacer(Modifier.height(8.dp))
                LargeVideoCard(latestLive, live = true) { open(latestLive.url) }
            }

            item {
                SectionTitle("Most Viewed Analysis")
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(topVideos, key = { it.id }) { video ->
                        Column(Modifier.width(280.dp)) {
                            Thumbnail(
                                video = video,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                            ) { open(video.url) }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                video.title,
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(video.views, color = IosColors.TextSecondary, fontSize = 14.sp)
                        }
                    }
                }
            }

            item {
                SectionTitle("Quick Insights (Shorts)")
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(shorts, key = { it.id }) { video ->
                        Column(Modifier.width(140.dp)) {
                            Thumbnail(
                                video = video,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    // Shorts are portrait; a 16:9 frame would
                                    // crop the subject's head off every one.
                                    .aspectRatio(9f / 16f)
                            ) { open(video.url) }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                video.title,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(video.views, color = IosColors.TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        Box(
            Modifier
                .padding(start = 16.dp, top = 4.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0x99000000))
                .clickable {
                    Haptics.tap(haptics)
                    onBack()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

@Composable
private fun LargeVideoCard(video: HubVideo, live: Boolean, onClick: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Thumbnail(
            video = video,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                // The live session wears a red rim, which is the only colour on
                // the screen that means "now".
                .then(
                    if (live) Modifier.border(
                        2.dp,
                        Color(0xFFFF3B30),
                        RoundedCornerShape(14.dp)
                    ) else Modifier
                ),
            onClick = onClick
        )
        Spacer(Modifier.height(10.dp))
        Text(
            video.title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(video.views, color = IosColors.TextSecondary, fontSize = 15.sp)
    }
}

@Composable
private fun Thumbnail(
    video: HubVideo,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF141414))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = video.thumbnail,
            contentDescription = video.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.75f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
