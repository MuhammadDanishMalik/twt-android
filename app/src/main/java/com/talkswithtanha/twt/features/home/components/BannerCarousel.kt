package com.talkswithtanha.twt.features.home.components

import com.talkswithtanha.twt.core.designsystem.edgeShine
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import coil3.compose.AsyncImage
import com.talkswithtanha.twt.core.data.Banner
import com.talkswithtanha.twt.core.designsystem.Haptics
import com.talkswithtanha.twt.core.images.Cloudinary
import kotlinx.coroutines.delay
import kotlin.math.absoluteValue

/**
 * The promotional banners from the admin panel, as a swipeable 2:1 carousel.
 *
 * Draws nothing at all when there are no active banners, so the home screen
 * looks exactly as it did before any were added. It moves on by itself every
 * few seconds, pauses while a finger is on it, and the neighbouring card
 * peeks in from the edge so it reads as swipeable.
 */
@Composable
fun BannerCarousel(
    banners: List<Banner>,
    onTap: (Banner) -> Unit,
    modifier: Modifier = Modifier
) {
    if (banners.isEmpty()) return
    val haptics = LocalHapticFeedback.current
    val pager = rememberPagerState(pageCount = { banners.size })
    val dragInteraction = remember { MutableInteractionSource() }
    val dragging by pager.interactionSource.collectIsDraggedAsState()

    LaunchedEffect(pager.currentPage, dragging, banners.size) {
        if (dragging || banners.size < 2) return@LaunchedEffect
        delay(5_000)
        pager.animateScrollToPage((pager.currentPage + 1) % banners.size)
    }

    Column(modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 10.dp,
            key = { banners[it].id }
        ) { page ->
            val banner = banners[page]
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f)
                    .graphicsLayer {
                        // The card leaving shrinks a touch, so the swipe has depth.
                        val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction)
                            .absoluteValue.coerceIn(0f, 1f)
                        val s = lerp(1f, 0.94f, offset)
                        scaleX = s
                        scaleY = s
                    }
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF141414))
                    .border(0.5.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(20.dp))
                    .edgeShine(RoundedCornerShape(20.dp))
                    .clickable(
                        interactionSource = dragInteraction,
                        indication = null,
                        enabled = banner.action != Banner.Action.NONE
                    ) {
                        Haptics.tap(haptics)
                        onTap(banner)
                    }
            ) {
                AsyncImage(
                    model = Cloudinary.transformed(banner.imageUrl, "c_fill,g_auto,ar_2:1,w_900,f_auto,q_auto"),
                    contentDescription = banner.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (banner.title.isNotBlank() || banner.subtitle.isNotBlank()) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0.4f to Color.Transparent,
                                    1f to Color.Black.copy(alpha = 0.78f)
                                )
                            )
                    )
                    Column(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        if (banner.title.isNotBlank()) {
                            Text(
                                banner.title,
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (banner.subtitle.isNotBlank()) {
                            Text(
                                banner.subtitle,
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        if (banners.size > 1) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
            ) {
                repeat(banners.size) { i ->
                    val selected = pager.currentPage == i
                    val width by animateDpAsState(
                        if (selected) 16.dp else 6.dp,
                        spring(dampingRatio = 0.75f),
                        label = "bannerDot"
                    )
                    Box(
                        Modifier
                            .width(width)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(if (selected) Color.White else Color.White.copy(alpha = 0.25f))
                    )
                }
            }
        }
    }
}
