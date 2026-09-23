package com.talkswithtanha.twt.features.signals

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SouthEast
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.talkswithtanha.twt.core.designsystem.Haptics
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.designsystem.staggeredAppear
import com.talkswithtanha.twt.core.images.Cloudinary
import com.talkswithtanha.twt.core.model.Signal
import com.talkswithtanha.twt.core.model.SignalType

/**
 * Tanha's chart analysis, as a feed.
 *
 * The same signals the Trades tab lists, turned inside out: there the chart is
 * a detail you reach by opening a ticket, here it is the whole card and the
 * numbers are the caption. A member scrolling this is deciding whether they
 * believe the setup, which is a different question from "what are my levels",
 * and it wants the picture first.
 *
 * Only signals Tanha actually attached a chart to appear. A card with an empty
 * frame where the analysis should be is worse than one fewer card.
 */
@Composable
fun AnalysisFeed(
    signals: List<Signal>,
    followedIds: Set<String>,
    onOpenSignal: (String) -> Unit,
    onFollow: (Signal) -> Unit,
    onUnfollow: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth()) {
        signals.forEachIndexed { index, signal ->
            AnalysisCard(
                signal = signal,
                isFollowing = signal.id in followedIds,
                onOpen = { onOpenSignal(signal.id) },
                onFollow = { onFollow(signal) },
                onUnfollow = { onUnfollow(signal.id) },
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .staggeredAppear(index)
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AnalysisCard(
    signal: Signal,
    isFollowing: Boolean,
    onOpen: () -> Unit,
    onFollow: () -> Unit,
    onUnfollow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val images = signal.teamImages
    val tint = signalTint(signal)

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardFill)
            .border(0.5.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(20.dp))
    ) {
        // ── The chart ────────────────────────────────────────────────────
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .background(Color(0xFF0E0E0E))
                .clickable {
                    Haptics.tap(haptics)
                    onOpen()
                }
        ) {
            if (images.size > 1) {
                // Swipeable when Tanha attached more than one, because a setup
                // is often two timeframes and flattening them loses the point.
                val pager = rememberPagerState { images.size }
                HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                    AsyncImage(
                        model = Cloudinary.wide(images[page], width = 900),
                        contentDescription = "Chart ${page + 1} for ${signal.pair}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                PageDots(
                    count = images.size,
                    current = pager.currentPage,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp)
                )
            } else {
                AsyncImage(
                    model = Cloudinary.wide(images.first(), width = 900),
                    contentDescription = "Chart for ${signal.pair}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // A scrim under the pair badge, so white text survives a chart that
            // happens to be pale in that corner.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)
                        )
                    )
            )

            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = signal.pair,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(tint.copy(alpha = 0.22f))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        if (signal.type == SignalType.BUY) Icons.Filled.NorthEast
                        else Icons.Filled.SouthEast,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(9.dp)
                    )
                    Text(
                        text = signal.type.stored,
                        color = tint,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // ── The caption ──────────────────────────────────────────────────
        Column(Modifier.padding(14.dp)) {
            signal.notes?.takeIf { it.isNotBlank() }?.let { note ->
                Text(
                    text = note,
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLines = 3
                )
                Spacer(Modifier.height(12.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Level("ENTRY", signal.entryPrice, Modifier.weight(1f))
                Level("TARGET", signal.takeProfits.lastOrNull()?.price ?: "—", Modifier.weight(1f))
                Level("STOP", signal.stopLoss, Modifier.weight(1f))
            }

            Spacer(Modifier.height(14.dp))

            FollowButton(
                isFollowing = isFollowing,
                enabled = signal.status.isOngoing,
                onClick = {
                    Haptics.toggle(haptics, on = !isFollowing)
                    if (isFollowing) onUnfollow() else onFollow()
                }
            )
        }
    }
}

@Composable
private fun Level(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.38f),
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.7.sp
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

/**
 * Follow, from the feed.
 *
 * The whole reason this tab exists as more than a gallery: a member who is
 * convinced by the chart should not have to go and find the ticket to act on
 * it. Closed trades show the state and refuse the tap — there is nothing left
 * to be told about.
 */
@Composable
private fun FollowButton(isFollowing: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val background = when {
        !enabled -> Color.White.copy(alpha = 0.05f)
        isFollowing -> IosColors.BuyGreen.copy(alpha = 0.15f)
        else -> IosColors.Accent
    }
    val content = when {
        !enabled -> Color.White.copy(alpha = 0.35f)
        isFollowing -> IosColors.BuyGreen
        else -> Color.White
    }

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = when {
                !enabled -> "Closed"
                isFollowing -> "Following"
                else -> "Follow this trade"
            },
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "follow"
        ) { label ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (enabled) {
                    Icon(
                        if (isFollowing) Icons.Filled.Check else Icons.Filled.Notifications,
                        contentDescription = null,
                        tint = content,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Text(
                    text = label,
                    color = content,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(count) { index ->
            Box(
                Modifier
                    .size(if (index == current) 6.dp else 5.dp)
                    .clip(CircleShape)
                    .background(
                        Color.White.copy(alpha = if (index == current) 0.95f else 0.4f)
                    )
            )
        }
    }
}

/**
 * Nothing posted yet.
 *
 * Says where the charts come from rather than just that there are none, so a
 * member does not read an empty tab as a broken one.
 */
@Composable
fun AnalysisEmpty(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Insights,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "No analysis yet",
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "When Tanha attaches a chart to a signal, the breakdown shows up here.",
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center
        )
    }
}

private val CardFill = Color(0xFF141414)
