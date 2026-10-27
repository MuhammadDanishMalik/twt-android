package com.talkswithtanha.twt.core.navigation

import com.talkswithtanha.twt.features.membership.IbRenewalReminder
import androidx.compose.foundation.layout.statusBarsPadding
import com.talkswithtanha.twt.features.marketplace.DealPulseOverlay
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.talkswithtanha.twt.core.designsystem.brand.TabIcons
import com.talkswithtanha.twt.core.designsystem.components.BottomBarItem
import com.talkswithtanha.twt.core.designsystem.components.TwtBottomBar
import com.talkswithtanha.twt.core.designsystem.components.TwtScreen

private val bottomBarItems = listOf(
    BottomBarItem("Home", AppRoute.Home.route) { tint, modifier ->
        TabIcons.Home(modifier, tint)
    },
    BottomBarItem("Signals", AppRoute.Signals.route) { tint, modifier ->
        TabIcons.Signals(modifier, tint)
    },
    BottomBarItem("Markets", AppRoute.Markets.route) { tint, modifier ->
        TabIcons.Markets(modifier, tint)
    },
    BottomBarItem("News", AppRoute.News.route) { tint, modifier ->
        TabIcons.News(modifier, tint)
    },
    // The feed took this tab from the chats, which now sit behind the icon in
    // its top right corner. It is what people open the app for; the
    // conversations are one tap away.
    BottomBarItem("Feed", AppRoute.Feed.route) { tint, modifier ->
        TabIcons.Feed(modifier, tint)
    }
)

/**
 * The shell: the nav host, the floating bottom bar, and the gate.
 *
 * ### Why the gate is here rather than in the graph
 *
 * Access can be revoked while the app is open — Tanha turns a code off in the
 * admin panel and the profile listener delivers that within a second. If the
 * gate were only a start destination, that member would keep browsing signals
 * until they next launched. Watching [RootViewModel.gate] and *navigating* on
 * change is what makes revocation take effect immediately.
 */
/** How far the blur reaches up behind the floating tab bar. */
private val BottomBlurHeight = 210.dp

@Composable
fun RootScreen(
    pendingSignalId: String? = null,
    onPendingSignalHandled: () -> Unit = {},
    pendingDealId: String? = null,
    onPendingDealHandled: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
    viewModel: RootViewModel = hiltViewModel()
) {
    val gate by viewModel.gate.collectAsStateWithLifecycle()
    val wasKicked by viewModel.wasKickedByOtherDevice.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    LaunchedEffect(currentRoute) { viewModel.screenShown(currentRoute) }

    // The gate moves the whole stack rather than pushing, so there is never a
    // back gesture from the access screen into the app a member has no code for.
    LaunchedEffect(gate) {
        val destination = when (gate) {
            RootViewModel.Gate.Undecided -> null
            RootViewModel.Gate.SignedOut -> AppRoute.Onboarding.route
            RootViewModel.Gate.NeedsEmailVerification -> AppRoute.VerifyEmail.route
            RootViewModel.Gate.NeedsProfileSetup -> AppRoute.ProfileSetup.route
            RootViewModel.Gate.NeedsAccessCode -> AppRoute.AccessGate.route
            RootViewModel.Gate.IbRenewal -> AppRoute.IbRenewal.of(expired = true)
            // Signed out first, then sent to the front door with a reason.
            RootViewModel.Gate.AccessExpired -> {
                viewModel.signOutForExpiredAccess()
                snackbars.showSnackbar(
                    "Your access code has expired, so you have been signed out. Sign in again with a new code."
                )
                AppRoute.Onboarding.route
            }
            RootViewModel.Gate.Allowed -> AppRoute.Home.route
        } ?: return@LaunchedEffect

        if (currentRoute == destination) return@LaunchedEffect

        navController.navigate(destination) {
            popUpTo(navController.graph.id) { inclusive = true }
            launchSingleTop = true
        }

        // Leaving the verify screen signs the member out, but they are not new
        // here: they just made an account. Sign In is where they want to be,
        // with onboarding underneath so back still behaves.
        if (gate == RootViewModel.Gate.SignedOut && currentRoute == AppRoute.VerifyEmail.route) {
            navController.navigate(AppRoute.SignIn.route) { launchSingleTop = true }
        }
    }

    // A notification tap, deferred until the gate has decided.
    //
    // Keyed on both, and that is the point: a tap while the app is dead arrives
    // long before the session has been restored, so navigating immediately would
    // push the signal on top of the splash screen and then have the gate wipe it
    // away again. Waiting for `Allowed` also means a member whose code has been
    // revoked does not get to walk through a stale notification into a screen
    // they can no longer read.
    LaunchedEffect(pendingSignalId, gate) {
        val signalId = pendingSignalId ?: return@LaunchedEffect
        if (gate != RootViewModel.Gate.Allowed) return@LaunchedEffect
        navController.navigate(AppRoute.SignalDetail.of(signalId)) {
            launchSingleTop = true
        }
        onPendingSignalHandled()
    }

    // A deal push, held for the gate the same way.
    LaunchedEffect(pendingDealId, gate) {
        val dealId = pendingDealId ?: return@LaunchedEffect
        if (gate != RootViewModel.Gate.Allowed) return@LaunchedEffect
        navController.navigate(AppRoute.DealDetail.of(dealId)) { launchSingleTop = true }
        onPendingDealHandled()
    }

    LaunchedEffect(wasKicked) {
        if (!wasKicked) return@LaunchedEffect
        snackbars.showSnackbar(
            "You have been signed out because this account was used on another device."
        )
        viewModel.acknowledgeKickedOut()
    }

    val haze = rememberHazeState()
    val showsBar = currentRoute in AppRoute.bottomBarRoutes

    TwtScreen {
        AppNavHost(
            navController = navController,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(haze)
        )

        // What scrolls under the floating tab bar melts into it rather than
        // meeting a hard edge: blurred more the lower it goes, and darkened a
        // little, the way Instagram's bar sits over the feed. Every tab gets
        // it from here, so no screen has to remember to draw its own.
        // Decorative only — no pointer input, so taps reach the content below.
        val badgesViewModel: com.talkswithtanha.twt.core.badges.BadgesViewModel = androidx.hilt.navigation.compose.hiltViewModel()
        val tabBadges by badgesViewModel.tabs.collectAsStateWithLifecycle()
        LaunchedEffect(currentRoute) { badgesViewModel.onRoute(currentRoute) }

        AnimatedVisibility(
            visible = showsBar,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(BottomBlurHeight)
                    .hazeBlur(
                        HazeInput.Sources(haze),
                        HazeBlurStyle {
                            blurRadius(36.dp)
                            backgroundColor(Color.Black)
                            colorEffects(listOf(HazeColorEffect.tint(Color.Black.copy(alpha = 0.38f))))
                            progressive(HazeProgressive.verticalGradient(startIntensity = 0f, endIntensity = 1f))
                        }
                    )
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.7f))))
            )
        }

        AnimatedVisibility(
            visible = showsBar,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            TwtBottomBar(
                items = bottomBarItems,
                badges = tabBadges,
                currentRoute = currentRoute,
                onNavigate = { route -> navController.navigateToTab(route) }
            )
        }

        // Open deals and their changes, over every screen once let in.
        if (gate == RootViewModel.Gate.Allowed) {
            DealPulseOverlay(
                currentRoute = currentRoute,
                currentDealId = backStackEntry?.arguments?.getString(AppRoute.DealDetail.ARG)
                    ?.takeIf { currentRoute == AppRoute.DealDetail.route },
                pillBottom = if (showsBar) 104.dp else 28.dp,
                onOpenDeal = { navController.navigate(AppRoute.DealDetail.of(it)) { launchSingleTop = true } },
                onOpenDeals = { navController.navigate(AppRoute.MyDeals.route) { launchSingleTop = true } }
            )
        }

        // An IB member's last three days: renew from here.
        if (gate == RootViewModel.Gate.Allowed && showsBar) {
            IbRenewalReminder(
                onOpen = { navController.navigate(AppRoute.IbRenewal.of(expired = false)) { launchSingleTop = true } },
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding()
            )
        }

        // Lifted clear of the floating pill. A snackbar at the default bottom
        // alignment lands underneath the bar, which is where the one message
        // this app shows -- "you have been signed out" -- would be least
        // readable.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (currentRoute in AppRoute.bottomBarRoutes) 96.dp else 0.dp)
        ) {
            SnackbarHost(hostState = snackbars)
        }
    }
}
