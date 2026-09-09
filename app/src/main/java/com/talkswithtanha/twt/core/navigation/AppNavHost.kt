package com.talkswithtanha.twt.core.navigation

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
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
import com.talkswithtanha.twt.features.profile.ProfileScreen
import com.talkswithtanha.twt.features.signals.MySignalsScreen
import com.talkswithtanha.twt.features.signals.SignalDetailScreen
import com.talkswithtanha.twt.features.signals.SignalsScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    fun openUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
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
                onOpenMySignals = { navController.navigate(AppRoute.MySignals.route) },
                onOpenProfile = { navController.navigate(AppRoute.Profile.route) }
            )
        }

        composable(AppRoute.Signals.route) {
            SignalsScreen(
                onOpenSignal = { navController.navigate(AppRoute.SignalDetail.of(it)) }
            )
        }

        composable(AppRoute.ChatList.route) {
            ChatListScreen(
                onOpenRoom = { navController.navigate(AppRoute.ChatRoom.of(it)) }
            )
        }

        // ── Pushed ───────────────────────────────────────────────────────
        composable(
            route = AppRoute.SignalDetail.route,
            arguments = listOf(navArgument(AppRoute.SignalDetail.ARG) { type = NavType.StringType })
        ) {
            SignalDetailScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = AppRoute.ChatRoom.route,
            arguments = listOf(navArgument(AppRoute.ChatRoom.ARG) { type = NavType.StringType })
        ) {
            ChatRoomScreen(onBack = { navController.popBackStack() })
        }

        composable(AppRoute.MySignals.route) {
            MySignalsScreen(
                onBack = { navController.popBackStack() },
                onOpenSignal = { navController.navigate(AppRoute.SignalDetail.of(it)) }
            )
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

        composable(AppRoute.Profile.route) {
            ProfileScreen(
                onBack = { navController.popBackStack() },
                onEditProfile = { navController.navigate(AppRoute.EditProfile.route) },
                onOpenMySignals = { navController.navigate(AppRoute.MySignals.route) }
            )
        }

        composable(AppRoute.EditProfile.route) {
            EditProfileScreen(onBack = { navController.popBackStack() })
        }
    }
}
