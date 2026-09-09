package com.talkswithtanha.twt.features.academy

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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class AcademyUiState(
    val videos: List<AcademyVideo> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class AcademyViewModel @Inject constructor(
    repository: AcademyRepository
) : ViewModel() {
    val state: StateFlow<AcademyUiState> = repository.observeVideos()
        .map { snapshot ->
            when (snapshot) {
                is Snapshot.Data -> AcademyUiState(snapshot.value, isLoading = false)
                is Snapshot.Failed -> AcademyUiState(isLoading = false)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AcademyUiState())
}

/**
 * Lessons, played on YouTube.
 *
 * Handing off to the YouTube app rather than embedding a WebView player. An
 * embedded player needs the IFrame API in a WebView, breaks when a video is
 * marked "playback on other websites disabled", and gives a worse full-screen
 * experience than the app the member already has installed.
 */
@Composable
fun AcademyScreen(
    onBack: () -> Unit,
    viewModel: AcademyViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    TwtScreen {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            contentPadding = PaddingValues(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
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

            when {
                state.isLoading -> items(3) {
                    Shimmer(Modifier.fillMaxWidth(), height = 180.dp, cornerRadius = Radius.card)
                }

                state.videos.isEmpty() -> item {
                    EmptyState(
                        icon = Icons.Outlined.School,
                        title = "No lessons yet",
                        message = "Tanha's lessons will appear here as he publishes them."
                    )
                }

                else -> items(state.videos, key = { it.id }) { video ->
                    VideoCard(video) {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, video.watchUrl.toUri())
                            )
                        }
                    }
                }
            }
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
