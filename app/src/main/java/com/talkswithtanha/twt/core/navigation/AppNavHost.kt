package com.talkswithtanha.twt.core.navigation

import android.content.Intent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.talkswithtanha.twt.core.designsystem.IosColors
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.talkswithtanha.twt.core.firebase.FirestorePaths
import com.talkswithtanha.twt.features.academy.AcademyScreen
import com.talkswithtanha.twt.features.access.AccessGateScreen
import com.talkswithtanha.twt.features.auth.AuthMode
import com.talkswithtanha.twt.features.auth.AuthScreen
import com.talkswithtanha.twt.features.auth.OnboardingScreen
import com.talkswithtanha.twt.features.auth.SplashScreen
import com.talkswithtanha.twt.features.chat.ChatListScreen
import com.talkswithtanha.twt.features.chat.ChatRoomScreen
import com.talkswithtanha.twt.features.home.HomeScreen
import com.talkswithtanha.twt.features.marketplace.MarketplaceScreen
import com.talkswithtanha.twt.features.profile.EditProfileScreen
import com.talkswithtanha.twt.features.settings.AppearanceScreen
import com.talkswithtanha.twt.features.settings.ContactSupportScreen
import com.talkswithtanha.twt.features.settings.MyDealsScreen
import com.talkswithtanha.twt.features.settings.MySignalsScreen
import com.talkswithtanha.twt.features.settings.RecordResultScreen
import com.talkswithtanha.twt.features.settings.SettingsScreen
import com.talkswithtanha.twt.features.signals.SignalDetailScreen
import com.talkswithtanha.twt.features.signals.SignalsScreen
import com.talkswithtanha.twt.features.signals.MySignalsViewModel

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    fun openUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
    }

    fun openSupportThread() {
        navController.navigate(AppRoute.ChatList.route)
    }

    NavHost(
        navController = navController,
        startDestination = AppRoute.Splash.route,
        modifier = modifier
    ) {
        // ── Pre-auth ─────────────────────────────────────────────────────
        composable(AppRoute.Splash.route) { SplashScreen() }

        composable(AppRoute.Onboarding.route) {
            OnboardingScreen(
                onSignIn = { navController.navigate(AppRoute.SignIn.route) },
                onCreateAccount = { navController.navigate(AppRoute.SignUp.route) }
            )
        }

        composable(AppRoute.SignIn.route) {
            AuthScreen(
                mode = AuthMode.SignIn,
                onSwitchMode = {
                    navController.navigate(AppRoute.SignUp.route) { launchSingleTop = true }
                }
            )
        }

        composable(AppRoute.SignUp.route) {
            AuthScreen(
                mode = AuthMode.SignUp,
                onSwitchMode = {
                    navController.navigate(AppRoute.SignIn.route) { launchSingleTop = true }
                }
            )
        }

        composable(AppRoute.AccessGate.route) { AccessGateScreen() }

        // ── Bottom bar ───────────────────────────────────────────────────
        composable(AppRoute.Home.route) {
            HomeScreen(
                onOpenSignal = { navController.navigate(AppRoute.SignalDetail.of(it)) },
                onOpenSignals = { navController.navigate(AppRoute.Signals.route) },
                onOpenAcademy = { navController.navigate(AppRoute.Academy.route) },
                onOpenMarketplace = { navController.navigate(AppRoute.Marketplace.route) },
                onOpenSettings = { navController.navigate(AppRoute.Settings.route) },
                onOpenSupport = { openSupportThread() }
            )
        }

        composable(AppRoute.Signals.route) {
            SignalsScreen(onOpenSignal = { navController.navigate(AppRoute.SignalDetail.of(it)) })
        }

        composable(AppRoute.ChatList.route) {
            ChatListScreen(
                onOpenRoom = { navController.navigate(AppRoute.ChatRoom.of(it)) },
                onOpenMarketplace = { navController.navigate(AppRoute.Marketplace.route) }
            )
        }

        // ── Pushed ───────────────────────────────────────────────────────
        composable(
            route = AppRoute.SignalDetail.route,
            arguments = listOf(navArgument(AppRoute.SignalDetail.ARG) { type = NavType.StringType })
        ) {
            SignalDetailScreen(
                onBack = { navController.popBackStack() },
                onRecordResult = { navController.navigate(AppRoute.RecordResult.of(it)) }
            )
        }

        composable(
            route = AppRoute.ChatRoom.route,
            arguments = listOf(navArgument(AppRoute.ChatRoom.ARG) { type = NavType.StringType })
        ) {
            ChatRoomScreen(onBack = { navController.popBackStack() })
        }

        composable(AppRoute.Academy.route) {
            AcademyScreen(onBack = { navController.popBackStack() })
        }

        composable(AppRoute.Marketplace.route) {
            MarketplaceScreen(
                onBack = { navController.popBackStack() },
                onOpenWhatsApp = ::openUrl
            )
        }

        // ── Settings family ──────────────────────────────────────────────
        //
        // Presented as sheets on iOS, so they rise from the bottom here rather
        // than sliding in from the side. Same gesture, same mental model.
        sheetRoute(AppRoute.Settings.route) {
            SettingsScreen(
                onClose = { navController.popBackStack() },
                onEditProfile = { navController.navigate(AppRoute.EditProfile.route) },
                onMySignals = { navController.navigate(AppRoute.MySignals.route) },
                onMyDeals = { navController.navigate(AppRoute.MyDeals.route) },
                onAppearance = { navController.navigate(AppRoute.Appearance.route) },
                onContactSupport = { navController.navigate(AppRoute.ContactSupport.route) }
            )
        }

        sheetRoute(AppRoute.EditProfile.route) {
            EditProfileScreen(onBack = { navController.popBackStack() })
        }

        sheetRoute(AppRoute.MySignals.route) {
            MySignalsScreen(
                onClose = { navController.popBackStack() },
                onRecord = { navController.navigate(AppRoute.RecordResult.of(it.signalId)) }
            )
        }

        sheetRoute(AppRoute.MyDeals.route) {
            MyDealsScreen(onClose = { navController.popBackStack() })
        }

        sheetRoute(AppRoute.Appearance.route) {
            AppearanceScreen(onClose = { navController.popBackStack() })
        }

        sheetRoute(AppRoute.ContactSupport.route) {
            val viewModel: com.talkswithtanha.twt.features.chat.ContactSupportViewModel =
                hiltViewModel()
            ContactSupportScreen(
                onClose = { navController.popBackStack() },
                onSend = viewModel::send
            )
        }

        sheetRoute(
            route = AppRoute.RecordResult.route,
            arguments = listOf(navArgument(AppRoute.RecordResult.ARG) { type = NavType.StringType })
        ) { entry ->
            val signalId = entry.arguments?.getString(AppRoute.RecordResult.ARG).orEmpty()
            val viewModel: MySignalsViewModel = hiltViewModel()

            // Collected, not read off `.value`. The follows listener may not
            // have delivered yet when this opens from a cold start, and reading
            // the current value in composition would show "not found" once and
            // never recompose when the record actually arrives.
            val follows by viewModel.follows.collectAsStateWithLifecycle()
            val follow = follows.firstOrNull { it.signalId == signalId }

            if (follow != null) {
                RecordResultScreen(
                    follow = follow,
                    onClose = { navController.popBackStack() },
                    onSave = { outcome, pips, amountMinor, currency, note ->
                        viewModel.record(signalId, outcome, pips, amountMinor, currency, note)
                        navController.popBackStack()
                    }
                )
            } else {
                // Still loading. Deliberately a blank sheet rather than a
                // `popBackStack()` here: navigating during composition fights
                // the transition that is still running.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(IosColors.Background)
                )
            }
        }
    }
}

/**
 * A destination that rises from the bottom, the way a sheet does on iOS.
 */
private fun androidx.navigation.NavGraphBuilder.sheetRoute(
    route: String,
    arguments: List<androidx.navigation.NamedNavArgument> = emptyList(),
    content: @Composable (androidx.navigation.NavBackStackEntry) -> Unit
) {
    composable(
        route = route,
        arguments = arguments,
        enterTransition = { slideInVertically(tween(320)) { it } + fadeIn(tween(220)) },
        exitTransition = { fadeOut(tween(160)) },
        popEnterTransition = { fadeIn(tween(160)) },
        popExitTransition = { slideOutVertically(tween(280)) { it } + fadeOut(tween(220)) },
        content = { entry -> content(entry) }
    )
}
