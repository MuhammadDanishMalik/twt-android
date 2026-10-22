package com.talkswithtanha.twt.features.home.components

import com.talkswithtanha.twt.core.designsystem.edgeShine
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
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
import java.util.Date
import kotlin.math.absoluteValue

/** Every page in this carousel is this tall — the access card's own height. */
private val HeroHeight = 242.dp

/** And this shape, so a banner reads as another card rather than a picture. */
private val HeroCorner = 24.dp

/**
 * The top of the home screen: the member's access card and the promotional
 * banners, as one row.
 *
 * They used to be stacked — the card, then a banner strip under it at half the
 * height with a sliver of the next banner showing. Two different objects at two
 * different sizes, and the sliver made the second one look like a mistake.
 * Here they are pages of one carousel at one size: whatever is on screen is the
 * only thing on screen, and the dots say how many more there are.
 *
 * ### The card never slides away on its own
 *
 * Auto-advance only runs once the member has swiped to a banner, and it cycles
 * between banners rather than returning to the card. The card is who they are
 * and how long they have left; a home screen that pulls it out from under
 * somebody reading it is a home screen that is pleased with itself.
 */
@Composable
fun HomeHeroCarousel(
    holderName: String?,
    expiresAt: Date?,
    grantedAt: Date?,
    accessCode: String?,
    banners: List<Banner>,
    onContactSupport: () -> Unit,
    onBannerTap: (Banner) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    // Page 0 is the access card, page 1 the live desk, banners after them.
    val firstBanner = 2
    val pageCount = firstBanner + banners.size
    val pager = rememberPagerState(pageCount = { pageCount })
    val dragging by pager.interactionSource.collectIsDraggedAsState()

    // ── Never park between two pages ─────────────────────────────────────
    //
    // A pager normally snaps when the finger lifts. It does not when the drag is
    // *cancelled* rather than released — which happens here because the carousel
    // sits inside a vertically scrolling list and a diagonal drag can be taken
    // away mid-gesture. The symptom is two half-pages side by side, sitting
    // there, which is what this screen looked like before this effect existed.
    LaunchedEffect(dragging, pager.isScrollInProgress) {
        if (!dragging && !pager.isScrollInProgress && pager.currentPageOffsetFraction != 0f) {
            pager.animateScrollToPage(pager.currentPage)
        }
    }

    LaunchedEffect(pager.currentPage, dragging, banners.size) {
        // The card and the live desk are read, not glanced at: nothing
        // automatic happens on either. Banners cycle among themselves.
        if (dragging || banners.size < 2 || pager.currentPage < firstBanner) return@LaunchedEffect
        delay(5_000)
        val next = pager.currentPage + 1
        pager.animateScrollToPage(if (next >= pageCount) firstBanner else next)
    }

    Column(modifier.fillMaxWidth()) {
        // No contentPadding on the pager, and the inset applied to each page
        // instead: contentPadding is what reveals the neighbouring page, and
        // the whole point here is that only one is ever visible.
        HorizontalPager(state = pager) { page ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .graphicsLayer {
                        // The page leaving shrinks a touch, so the swipe has
                        // depth rather than being a flat slide.
                        val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction)
                            .absoluteValue.coerceIn(0f, 1f)
                        val scale = lerp(1f, 0.94f, offset)
                        scaleX = scale
                        scaleY = scale
                        alpha = lerp(1f, 0.6f, offset)
                    }
            ) {
                if (page == 0) {
                    TwtAccessCard(
                        holderName = holderName,
                        expiresAt = expiresAt,
                        grantedAt = grantedAt,
                        accessCode = accessCode,
                        onContactSupport = onContactSupport,
                        // The tilt is a drag gesture and would swallow the
                        // swipe that moves the pager.
                        tiltEnabled = false
                    )
                } else if (page == 1) {
                    LiveDeskCard(height = HeroHeight, corner = HeroCorner)
                } else {
                    BannerPage(
                        banner = banners[page - firstBanner],
                        onTap = {
                            Haptics.tap(haptics)
                            onBannerTap(banners[page - firstBanner])
                        }
                    )
                }
            }
        }

        if (pageCount > 1) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
            ) {
                repeat(pageCount) { index ->
                    val selected = pager.currentPage == index
                    val width by animateDpAsState(
                        targetValue = if (selected) 16.dp else 6.dp,
                        animationSpec = spring(dampingRatio = 0.75f),
                        label = "heroDot"
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

@Composable
private fun BannerPage(
    banner: Banner,
    onTap: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(HeroHeight)
            .clip(RoundedCornerShape(HeroCorner))
            .background(Color(0xFF141414))
            .border(0.5.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(HeroCorner))
            .edgeShine(RoundedCornerShape(HeroCorner))
            // `detectTapGestures` rather than `clickable`: a tap opens the
            // banner, and anything that turns into a drag is left for the pager
            // underneath rather than being swallowed here.
            .pointerInput(banner.id, banner.action) {
                if (banner.action == Banner.Action.NONE) return@pointerInput
                detectTapGestures { onTap() }
            }
    ) {
        AsyncImage(
            // Asked for at the card's proportions rather than the old 2:1 strip,
            // so the crop happens on Cloudinary's side where it can pick the
            // subject, instead of cutting the sides off here.
            model = Cloudinary.transformed(banner.imageUrl, "c_fill,g_auto,ar_3:2,w_900,f_auto,q_auto"),
            contentDescription = banner.title,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        if (banner.title.isNotBlank() || banner.subtitle.isNotBlank()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.45f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.78f)
                        )
                    )
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                if (banner.title.isNotBlank()) {
                    Text(
                        text = banner.title,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (banner.subtitle.isNotBlank()) {
                    Text(
                        text = banner.subtitle,
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
