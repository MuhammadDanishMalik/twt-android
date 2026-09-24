package com.talkswithtanha.twt.features.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.talkswithtanha.twt.R
import androidx.compose.material3.LocalTextStyle
import com.talkswithtanha.twt.core.designsystem.AnimatedNumber
import com.talkswithtanha.twt.core.designsystem.Haptics
import com.talkswithtanha.twt.core.designsystem.components.MemberAvatar
import com.talkswithtanha.twt.core.designsystem.Motion
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.model.ExchangeRate
import com.talkswithtanha.twt.features.home.components.HomeShortcuts
import com.talkswithtanha.twt.features.home.components.TwtAccessCard
import com.talkswithtanha.twt.features.signals.SignalTicketCard
import androidx.compose.ui.platform.LocalHapticFeedback
import java.util.Calendar

/** How far the bottom scrim reaches up behind the floating tab bar. */
private val BottomScrimHeight = 160.dp

/**
 * The home screen, over the photograph.
 *
 * The background is the reason the screen works: the cards are near-black and
 * the image behind them is what stops that reading as a grey void. It is dimmed
 * by a vertical scrim rather than a flat wash, so the top stays rich where the
 * card sits and the bottom goes quiet where dense text lives.
 *
 * Reading order answers, in turn: who am I and what tier · is Tanha live right
 * now · where do I want to go · what happened today.
 */
@Composable
fun HomeScreen(
    onOpenSignal: (String) -> Unit,
    onOpenSignals: () -> Unit,
    onWatchLive: () -> Unit,
    onOpenAcademy: () -> Unit,
    onOpenMarketplace: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSupport: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rate by viewModel.rate.collectAsStateWithLifecycle()
    val followed by viewModel.followedSignalIds.collectAsStateWithLifecycle()
    val layout by viewModel.shortcutsLayout.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.home_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.15f),
                            Color.Black.copy(alpha = 0.55f),
                            Color.Black.copy(alpha = 0.88f)
                        )
                    )
                )
        )

        // A second, shorter scrim behind the status bar.
        //
        // The photograph is lit from the top centre, which is exactly where the
        // clock and the battery sit — white system icons on the brightest part
        // of the image, which is the one place the app cannot restyle them. The
        // screen-wide scrim above starts at 15% so the top stays rich; this one
        // is local to the status bar and fades out within it, so the icons have
        // something to sit on without dulling the light below them.
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)
                    )
                )
        )

        // The matching scrim at the bottom.
        //
        // The floating tab bar sits over the feed, and a card scrolling behind
        // it used to slide under a hard edge. This gives it something to fade
        // into instead, and darkens the gesture bar the same way the top scrim
        // darkens the clock. Taller than the inset it covers, because the tab
        // bar floats above the inset rather than sitting in it.
        Box(
            Modifier
                .fillMaxWidth()
                .height(BottomScrimHeight)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                    )
                )
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(top = 4.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                StaggeredAppear(0) {
                    HomeHeader(
                        firstName = user?.firstName ?: "Trader",
                        fullName = user?.fullName,
                        photoUrl = user?.profilePhoto,
                        onOpenSettings = onOpenSettings
                    )
                }
            }

            item {
                StaggeredAppear(1) {
                    TwtAccessCard(
                        holderName = user?.fullName,
                        expiresAt = user?.membershipExpiresAt,
                        grantedAt = user?.accessGrantedAt,
                        accessCode = user?.accessCode,
                        onContactSupport = onOpenSupport,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            item {
                StaggeredAppear(2) {
                    HomeShortcuts(
                        layout = layout,
                        onWatchLive = onWatchLive,
                        onOpenMarketplace = onOpenMarketplace,
                        onOpenAcademy = onOpenAcademy,
                        exchangeSubtitle = rate?.let {
                            "1 USD = ${ExchangeRate.formatPaisa(it.buyPaisa)} PKR"
                        } ?: "USD / PKR",
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            item {
                StaggeredAppear(3) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Today's Signals",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.size(8.dp))
                        // The count arrives a moment after the heading, and
                        // changes again whenever Tanha posts or closes a trade,
                        // so it fades in once and rolls thereafter.
                        androidx.compose.animation.AnimatedVisibility(
                            visible = !state.isLoading,
                            enter = androidx.compose.animation.fadeIn(Motion.gentle()) +
                                androidx.compose.animation.scaleIn(Motion.gentle(), initialScale = 0.7f),
                            exit = androidx.compose.animation.fadeOut(Motion.quick())
                        ) {
                            Box(
                                Modifier
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                AnimatedNumber(
                                    value = state.signals.size.toString(),
                                    style = LocalTextStyle.current.copy(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "See All",
                            color = IosColors.Accent,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickableNoRipple(onOpenSignals)
                        )
                    }
                }
            }

            item {
                StaggeredAppear(4) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (state.isLoading) {
                            items(2) {
                                com.talkswithtanha.twt.core.designsystem.components.SignalCardSkeleton(
                                    Modifier.width(330.dp)
                                )
                            }
                        }
                        items(state.signals, key = { it.id }) { signal ->
                            SignalTicketCard(
                                signal = signal,
                                isFollowing = signal.id in followed,
                                width = 330.dp,
                                onOpen = { onOpenSignal(signal.id) },
                                onFollow = { viewModel.follow(signal) },
                                onRequestUnfollow = { viewModel.unfollow(signal.id) }
                            )
                        }
                    }
                }
            }
        }
        // The matching scrim at the bottom, drawn *over* the feed.
        //
        // Behind the list it did nothing: the photograph is already black down
        // there and the cards scroll in front of it. The cards are the point —
        // the floating tab bar sits over the feed, and one sliding under it hit
        // a hard edge. Now it fades out first, the way the top scrim gives the
        // clock something to sit on. Decorative only: no pointer input, so taps
        // still reach the tab bar and the feed beneath it.
        Box(
            Modifier
                .fillMaxWidth()
                .height(BottomScrimHeight)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f))
                    )
                )
        )

    }
}

@Composable
private fun HomeHeader(
    firstName: String,
    fullName: String?,
    photoUrl: String?,
    onOpenSettings: () -> Unit
) {
    val haptics = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = greeting(),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 15.sp
            )
            Text(
                text = firstName,
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }

        MemberAvatar(
            photoUrl = photoUrl,
            name = fullName,
            size = 38.dp,
            modifier = Modifier.clickable {
                Haptics.tap(haptics)
                onOpenSettings()
            }
        )
    }
}

/**
 * The entrance. Each section rises and fades in a beat after the one above it,
 * so the screen assembles top-down instead of appearing all at once.
 */
@Composable
private fun StaggeredAppear(
    index: Int,
    content: @Composable () -> Unit
) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(index * 70L)
        appeared = true
    }
    val progress by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 260f),
        label = "appear"
    )
    Box(
        Modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * 18f * density
        }
    ) {
        content()
    }
}

private fun greeting(): String =
    when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 0..11 -> "Good morning,"
        in 12..16 -> "Good afternoon,"
        else -> "Good evening,"
    }

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this.clickable(
        interactionSource = interaction,
        indication = null,
        onClick = onClick
    )
}
