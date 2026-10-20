package com.talkswithtanha.twt.features.academy

import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import com.talkswithtanha.twt.core.designsystem.staggeredAppear
import com.talkswithtanha.twt.core.designsystem.Haptics
import com.talkswithtanha.twt.core.data.YouTubeFeedRepository
import com.talkswithtanha.twt.core.data.ChannelVideo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import android.text.format.DateUtils
import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.talkswithtanha.twt.core.data.AcademyRepository
import com.talkswithtanha.twt.core.data.Snapshot
import com.talkswithtanha.twt.core.designsystem.Radius
import com.talkswithtanha.twt.core.designsystem.Spacing
import com.talkswithtanha.twt.core.designsystem.TwtColors
import com.talkswithtanha.twt.core.designsystem.components.EmptyState
import com.talkswithtanha.twt.core.designsystem.components.Shimmer
import com.talkswithtanha.twt.core.designsystem.components.TwtCard
import com.talkswithtanha.twt.core.designsystem.components.TwtChip
import com.talkswithtanha.twt.core.designsystem.components.TwtScreen
import com.talkswithtanha.twt.core.model.AcademyVideo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class AcademyUiState(
    /** Lessons published from the admin panel. */
    val lessons: List<AcademyVideo> = emptyList(),
    val isLoading: Boolean = true,
    /** Tanha's latest uploads, straight from his channel. */
    val videos: List<ChannelVideo> = emptyList(),
    val shorts: List<ChannelVideo> = emptyList(),
    val isLoadingFeed: Boolean = true,
    val feedFailed: Boolean = false
)

@HiltViewModel
class AcademyViewModel @Inject constructor(
    repository: AcademyRepository,
    private val feed: YouTubeFeedRepository
) : ViewModel() {

    private val channel = MutableStateFlow(Triple(emptyList<ChannelVideo>(), true, false))

    val state: StateFlow<AcademyUiState> = combine(repository.observeVideos(), channel) { snapshot, (uploads, loading, failed) ->
        AcademyUiState(
            lessons = (snapshot as? Snapshot.Data)?.value.orEmpty(),
            isLoading = false,
            videos = uploads.filterNot { it.isShort }.withoutEmptyTwins(),
            shorts = uploads.filter { it.isShort },
            isLoadingFeed = loading,
            feedFailed = failed
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AcademyUiState())

    init { refresh() }

    fun refresh(force: Boolean = false) {
        channel.update { it.copy(second = true, third = false) }
        viewModelScope.launch {
            feed.latest(force)
                .onSuccess { channel.value = Triple(it, false, false) }
                .onFailure { channel.update { c -> c.copy(second = false, third = true) } }
        }
    }
}

/**
 * The Academy: Tanha's lessons and his latest uploads, played in the app so
 * notes can be pinned to moments in them.
 *
 * Two sources. Lessons published from the admin panel come first when there
 * are any. Below them, always, the channel's newest videos and Shorts, read
 * live from YouTube — so the screen is never empty while he is posting, and
 * never shows a list that stopped changing months ago.
 */
@Composable
fun AcademyScreen(
    onBack: () -> Unit,
    /** Plays a video in-app, where notes can be taken on it. */
    onOpenVideo: (videoId: String, title: String) -> Unit,
    viewModel: AcademyViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    fun openChannel() = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, YouTubeFeedRepository.CHANNEL_URL.toUri()))
    }

    TwtScreen {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            contentPadding = PaddingValues(vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = Spacing.lg)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TwtColors.TextPrimary
                        )
                    }
                    Text(
                        text = "Academy",
                        style = MaterialTheme.typography.headlineLarge,
                        color = TwtColors.TextPrimary
                    )
                }
            }

            if (state.lessons.isNotEmpty()) {
                item { SectionHeader("Lessons", "From Tanha's course") }
                items(state.lessons, key = { "lesson-${it.id}" }) { video ->
                    Box(Modifier.padding(horizontal = Spacing.lg)) {
                        VideoCard(video) {
                            Haptics.tap(haptics)
                            onOpenVideo(video.youtubeId, video.title)
                        }
                    }
                }
            }

            when {
                state.isLoadingFeed && state.videos.isEmpty() -> {
                    item { SectionHeader("Latest videos", "Fresh from the channel") }
                    items(2) {
                        Shimmer(
                            Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
                            height = 220.dp,
                            cornerRadius = Radius.card
                        )
                    }
                }

                state.feedFailed && state.videos.isEmpty() && state.shorts.isEmpty() -> item {
                    EmptyState(
                        icon = Icons.Outlined.WifiOff,
                        title = "Couldn't load videos",
                        message = "Check your connection, or watch on YouTube instead.",
                        action = {
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                TwtChip("Try again", modifier = Modifier.clickable { viewModel.refresh(force = true) })
                                TwtChip("Open YouTube", modifier = Modifier.clickable { openChannel() })
                            }
                        }
                    )
                }

                else -> {
                    if (state.videos.isNotEmpty()) {
                        item { SectionHeader("Latest videos", "Fresh from the channel") }
                        itemsIndexed(state.videos, key = { _, v -> "video-${v.id}" }) { index, video ->
                            Box(
                                Modifier
                                    .padding(horizontal = Spacing.lg)
                                    .staggeredAppear(index)
                            ) {
                                ChannelVideoCard(video) {
                                    Haptics.tap(haptics)
                                    onOpenVideo(video.id, video.title)
                                }
                            }
                        }
                    }
                    if (state.shorts.isNotEmpty()) {
                        item { SectionHeader("Shorts", "Quick tips in under a minute") }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = Spacing.lg),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                            ) {
                                itemsIndexed(state.shorts, key = { _, v -> "short-${v.id}" }) { index, short ->
                                    Box(Modifier.staggeredAppear(index)) {
                                        ShortCard(short) {
                                            Haptics.tap(haptics)
                                            onOpenVideo(short.id, short.title)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                                .clip(RoundedCornerShape(Radius.card))
                                .background(TwtColors.SurfaceElevated)
                                .clickable {
                                    Haptics.tap(haptics)
                                    openChannel()
                                }
                                .padding(Spacing.lg),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.PlayCircle,
                                contentDescription = null,
                                tint = Color(0xFFFF0033),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.width(Spacing.md))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "More on YouTube",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TwtColors.TextPrimary
                                )
                                Text(
                                    "@Tradewithtanha001 — every video and live stream",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TwtColors.TextSecondary
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Outlined.OpenInNew,
                                contentDescription = null,
                                tint = TwtColors.TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(Modifier.padding(horizontal = Spacing.lg).padding(top = Spacing.sm)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = TwtColors.TextPrimary)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TwtColors.TextSecondary)
    }
}

/**
 * Drops an upload that has no views yet when another with the same title does.
 * A live stream that was scheduled twice, or re-uploaded, otherwise shows as
 * two identical cards side by side.
 */
private fun List<ChannelVideo>.withoutEmptyTwins(): List<ChannelVideo> = filterNot { video ->
    video.views == null && any { it.id != video.id && it.title == video.title && it.views != null }
}

/** "3.9K views · 2 days ago". */
private fun ChannelVideo.meta(): String {
    val ago = DateUtils.getRelativeTimeSpanString(
        publishedAt.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
    ).toString()
    val count = views?.let {
        when {
            it >= 1_000_000 -> "%.1fM views".format(it / 1_000_000.0)
            it >= 1_000 -> "%.1fK views".format(it / 1_000.0)
            else -> "$it views"
        }.replace(".0", "")
    }
    return listOfNotNull(count, ago).joinToString(" · ")
}

@Composable
private fun ChannelVideoCard(video: ChannelVideo, onClick: () -> Unit) {
    TwtCard(modifier = Modifier.fillMaxWidth(), onClick = onClick, contentPadding = Spacing.md) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(Radius.chip)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
        }
        Spacer(Modifier.height(Spacing.md))
        Text(
            text = video.title,
            style = MaterialTheme.typography.titleMedium,
            color = TwtColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(video.meta(), style = MaterialTheme.typography.bodyMedium, color = TwtColors.TextSecondary)
    }
}

@Composable
private fun ShortCard(short: ChannelVideo, onClick: () -> Unit) {
    Column(Modifier.width(140.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(Radius.card))
                .background(TwtColors.SurfaceElevated)
                .clickable(onClick = onClick)
        ) {
            // The hq thumbnail is 4:3 with the upright video centred in it;
            // cropping to 9:16 keeps exactly the video.
            AsyncImage(
                model = short.thumbnailUrl,
                contentDescription = short.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))
                    )
            )
            Text(
                short.title.replace(Regex("#\\S+"), "").trim(),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)
            )
        }
        short.views?.let {
            Spacer(Modifier.height(6.dp))
            Text(short.meta().substringBefore(" ·"), style = MaterialTheme.typography.bodyMedium, color = TwtColors.TextSecondary)
        }
    }
}

@Composable
private fun VideoCard(video: AcademyVideo, onClick: () -> Unit) {
    TwtCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = Spacing.md
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(Radius.chip)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(TwtColors.Background.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = TwtColors.Gold,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(Modifier.height(Spacing.md))

        Text(
            text = video.title,
            style = MaterialTheme.typography.titleMedium,
            color = TwtColors.TextPrimary
        )

        video.description?.let {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = TwtColors.TextSecondary,
                maxLines = 2
            )
        }

        Spacer(Modifier.height(Spacing.sm))

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            video.level?.let { TwtChip(it) }
            video.category?.let { TwtChip(it) }
            video.durationLabel?.let { TwtChip(it) }
        }
    }
}
