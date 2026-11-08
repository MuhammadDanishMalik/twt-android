package com.talkswithtanha.twt.features.home

import com.talkswithtanha.twt.features.access.ProLockCard
import com.talkswithtanha.twt.features.access.rememberIsPro
import com.talkswithtanha.twt.core.designsystem.WebFonts
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
import com.talkswithtanha.twt.features.home.components.HomeHeroCarousel
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
    /** The Chat tab, for a banner that points there. */
    onOpenChat: () -> Unit = {},
    /** The exchange desk, for staff who hold the marketplace. */
    onOpenDesk: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val desk by viewModel.deskSummary.collectAsStateWithLifecycle()
    // The dealer's account is the desk: it opens on the desk once per launch,
    // and Back from there is the ordinary app.
    var deskOpened by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(desk?.deskOnly) {
        if (desk?.deskOnly == true && !deskOpened) {
            deskOpened = true
            onOpenDesk()
        }
    }
    val banners by viewModel.activeBanners.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val user by viewModel.user.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rate by viewModel.rate.collectAsStateWithLifecycle()
    val followed by viewModel.followedSignalIds.collectAsStateWithLifecycle()
    val layout by viewModel.shortcutsLayout.collectAsStateWithLifecycle()
    val support by viewModel.support.collectAsStateWithLifecycle()
    // "Tap to renew": the member picks how to reach the desk.
    var renewing by remember { mutableStateOf(false) }

    if (renewing) {
        RenewSheet(
            whatsAppNumber = support.whatsAppNumber,
            onWhatsApp = {
                renewing = false
                runCatching {
                    context.startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(support.whatsAppUrl("Assalam o Alaikum, I would like to renew / upgrade my TWT access."))
                        )
                    )
                }
            },
            onChat = {
                renewing = false
                onOpenSupport()
            },
            onDismiss = { renewing = false }
        )
    }

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

        val isPro = rememberIsPro()
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

            // The access card and the banners, as one row at one size — see
            // HomeHeroCarousel. They were two stacked blocks at two different
            // heights, with a sliver of the next banner showing.
            item {
                StaggeredAppear(1) {
                    HomeHeroCarousel(
                        holderName = user?.fullName,
                        expiresAt = user?.membershipExpiresAt,
                        grantedAt = user?.accessGrantedAt,
                        accessCode = user?.accessCode,
                        banners = banners,
                        onContactSupport = { renewing = true },
                        onBannerTap = { banner ->
                            viewModel.bannerTapped(banner.id)
                            when (banner.action) {
                                com.talkswithtanha.twt.core.data.Banner.Action.ACADEMY -> onOpenAcademy()
                                com.talkswithtanha.twt.core.data.Banner.Action.SIGNALS -> onOpenSignals()
                                com.talkswithtanha.twt.core.data.Banner.Action.CHAT -> onOpenChat()
                                com.talkswithtanha.twt.core.data.Banner.Action.URL -> runCatching {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse(banner.url)
                                        )
                                    )
                                }
                                com.talkswithtanha.twt.core.data.Banner.Action.NONE -> Unit
                            }
                        }
                    )
                }
            }


            desk?.let { summary ->
                item {
                    StaggeredAppear(1) {
                        DeskCard(summary, onOpenDesk, Modifier.padding(horizontal = 16.dp))
                    }
                }
            }

            // Free members: what a code opens, and the way to it.
            if (isPro == false && desk?.deskOnly != true) {
                item {
                    StaggeredAppear(2) {
                        ProLockCard(
                            feature = "Unlock the live calls",
                            blurb = "You're on the free plan: analysis, results, markets and the academy are open. Live trades, the feed and the member rooms need an access code.",
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
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
                            fontFamily = WebFonts.Display,
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

            // Gold and the day's heatmap, from the website's Markets page. A
            // LazyColumn only composes an item as it scrolls into view, so the
            // cards animate in when the member reaches them, not before.
            item(key = "markets") {
                com.talkswithtanha.twt.features.home.components.HomeMarketsSection(
                    onOpenNews = {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(com.talkswithtanha.twt.core.data.MarketsApi.GOLD_NEWS_URL)
                                )
                            )
                        }
                    },
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
        // The fade into the floating tab bar is drawn by RootScreen, over every tab.

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
                fontFamily = WebFonts.Display,
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

/**
 * Renewing or upgrading access: the two ways to reach the desk. WhatsApp, on
 * the number the panel sets (Settings → Support links), or Tanha's private
 * chat in the app.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun RenewSheet(whatsAppNumber: String, onWhatsApp: () -> Unit, onChat: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF1C1C1E)) {
        androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
            androidx.compose.material3.Text("Renew or upgrade", color = Color.White, fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            androidx.compose.material3.Text(
                "Talk to customer support the way you prefer.",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )
            RenewOption("WhatsApp support", "+${whatsAppNumber.chunkedPhone()}", Color(0xFF25D366), onWhatsApp)
            androidx.compose.foundation.layout.Spacer(Modifier.height(10.dp))
            RenewOption("Chat with Tanha in the app", "Your private chat, answered by Tanha and his team", Color(0xFF0A84FF), onChat)
        }
    }
}

@Composable
private fun RenewOption(title: String, subtitle: String, tint: Color, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .background(Color(0xFF2C2C2E))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)
    ) {
        Box(Modifier.size(12.dp).clip(androidx.compose.foundation.shape.CircleShape).background(tint))
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            androidx.compose.material3.Text(title, color = Color.White, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            androidx.compose.material3.Text(subtitle, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        }
        androidx.compose.material3.Text("›", color = Color.White.copy(alpha = 0.5f), fontSize = 22.sp)
    }
}

/** `923087050116` → `92 308 7050116`, as people read a Pakistani mobile. */
private fun String.chunkedPhone(): String =
    if (length == 12 && startsWith("92")) "${substring(0, 2)} ${substring(2, 5)} ${substring(5)}" else this

/** The exchange desk at a glance, for the dealer and staff who hold the marketplace. */
@Composable
private fun DeskCard(summary: DeskSummary, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val waiting = summary.requests + summary.needsYou + summary.unreadChats
    androidx.compose.foundation.layout.Row(
        modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
            .background(if (waiting > 0) Color(0xFF14361D) else Color(0xFF1C1C1E))
            .clickable(onClick = onOpen)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            androidx.compose.material3.Text("EXCHANGE DESK", color = Color(0xFF7BD88F), fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            androidx.compose.material3.Text(
                when {
                    waiting == 0 -> "All caught up"
                    else -> listOfNotNull(
                        summary.requests.takeIf { it > 0 }?.let { "$it new request${if (it == 1) "" else "s"}" },
                        summary.needsYou.takeIf { it > 0 }?.let { "$it need you" },
                        summary.unreadChats.takeIf { it > 0 }?.let { "$it unread chat${if (it == 1) "" else "s"}" }
                    ).joinToString(" · ")
                },
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            androidx.compose.material3.Text("Accept deals, confirm payments, send funds, chat", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        }
        androidx.compose.material3.Text("Open", color = Color.White, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
    }
}
