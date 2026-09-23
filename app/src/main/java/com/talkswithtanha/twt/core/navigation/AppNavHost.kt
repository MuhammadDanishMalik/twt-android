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
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.talkswithtanha.twt.core.firebase.FirestorePaths
import com.talkswithtanha.twt.features.academy.AcademyScreen
import com.talkswithtanha.twt.features.access.AccessGateScreen
import com.talkswithtanha.twt.features.auth.AuthMode
import com.talkswithtanha.twt.features.auth.AuthScreen
import com.talkswithtanha.twt.features.auth.ResetPasswordScreen
import com.talkswithtanha.twt.features.auth.VerifyEmailScreen
import com.talkswithtanha.twt.features.auth.OnboardingScreen
import com.talkswithtanha.twt.features.auth.SplashScreen
import com.talkswithtanha.twt.features.chat.ChatListScreen
import com.talkswithtanha.twt.features.chat.ChatRoomScreen
import com.talkswithtanha.twt.features.home.HomeScreen
import com.talkswithtanha.twt.features.marketplace.DealDetailScreen
import com.talkswithtanha.twt.features.marketplace.MarketplaceScreen
import com.talkswithtanha.twt.features.marketplace.MyDealsScreen
import com.talkswithtanha.twt.features.marketplace.NewDealScreen
import com.talkswithtanha.twt.features.media.MediaHubScreen
import com.talkswithtanha.twt.features.profile.AccountDetailsScreen
import com.talkswithtanha.twt.features.profile.EditProfileScreen
import com.talkswithtanha.twt.features.settings.AppearanceScreen
import com.talkswithtanha.twt.features.settings.ContactSupportScreen
import com.talkswithtanha.twt.features.settings.MySignalsScreen
import com.talkswithtanha.twt.features.settings.RecordResultScreen
import com.talkswithtanha.twt.features.settings.SettingsScreen
import com.talkswithtanha.twt.features.signals.SignalDetailScreen
import com.talkswithtanha.twt.features.signals.SignalsScreen
import com.talkswithtanha.twt.features.signals.MySignalsViewModel

@OptIn(ExperimentalSharedTransitionApi::class)
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

    // One layout wrapping the whole graph, because a shared element has to be
    // measured against something both screens live inside.
    SharedTransitionLayout(modifier = modifier) {
        val sharedScope = this

        NavHost(
            navController = navController,
            startDestination = AppRoute.Splash.route
        ) {
        // ── Pre-auth ─────────────────────────────────────────────────────
            screen(sharedScope, AppRoute.Splash.route) { SplashScreen() }

            screen(sharedScope, AppRoute.Onboarding.route) {
            OnboardingScreen(
                onSignIn = { navController.navigate(AppRoute.SignIn.route) },
                onCreateAccount = { navController.navigate(AppRoute.SignUp.route) }
            )
        }

            screen(sharedScope, AppRoute.SignIn.route) {
            AuthScreen(
                mode = AuthMode.SignIn,
                onSwitchMode = {
                    navController.navigate(AppRoute.SignUp.route) { launchSingleTop = true }
                },
                onVerifyEmail = { navController.navigate(AppRoute.VerifyEmail.route) },
                onForgotPassword = { navController.navigate(AppRoute.ResetPassword.route) }
            )
        }

            screen(sharedScope, AppRoute.SignUp.route) {
            AuthScreen(
                mode = AuthMode.SignUp,
                onSwitchMode = {
                    navController.navigate(AppRoute.SignIn.route) { launchSingleTop = true }
                },
                onVerifyEmail = { navController.navigate(AppRoute.VerifyEmail.route) },
                onForgotPassword = { navController.navigate(AppRoute.ResetPassword.route) }
            )
        }

            screen(sharedScope, AppRoute.VerifyEmail.route) {
            VerifyEmailScreen(
                // No navigation here. Verifying flips the gate, and the gate
                // moves the whole stack — anything pushed from this screen
                // would be wiped a frame later. Signing out is the honest exit
                // for somebody who typed the wrong address: the account exists
                // and has to be abandoned, not backed out of.
                onVerified = {}
            )
        }

            screen(sharedScope, AppRoute.ResetPassword.route) {
            ResetPasswordScreen(
                onDone = {
                    navController.navigate(AppRoute.SignIn.route) {
                        popUpTo(AppRoute.SignIn.route) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

            screen(sharedScope, AppRoute.AccessGate.route) { AccessGateScreen() }

        // ── Bottom bar ───────────────────────────────────────────────────
            screen(sharedScope, AppRoute.Home.route) {
            HomeScreen(
                onOpenSignal = { navController.navigate(AppRoute.SignalDetail.of(it)) },
                onOpenSignals = { navController.navigate(AppRoute.Signals.route) },
                onWatchLive = { navController.navigate(AppRoute.MediaHub.route) },
                onOpenAcademy = { navController.navigate(AppRoute.Academy.route) },
                onOpenMarketplace = { navController.navigate(AppRoute.Marketplace.route) },
                onOpenSettings = { navController.navigate(AppRoute.Settings.route) },
                onOpenSupport = { openSupportThread() }
            )
        }

            screen(sharedScope, AppRoute.Signals.route) {
            SignalsScreen(onOpenSignal = { navController.navigate(AppRoute.SignalDetail.of(it)) })
        }

            screen(sharedScope, AppRoute.ChatList.route) {
            ChatListScreen(
                onOpenRoom = { navController.navigate(AppRoute.ChatRoom.of(it)) },
                // The marketplace row opens the same private thread in its
                // seller presentation, rather than the rate screen.
                onOpenSellerChat = { navController.navigate(AppRoute.ChatRoom.of(it, seller = true)) }
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
            arguments = listOf(
                navArgument(AppRoute.ChatRoom.ARG) { type = NavType.StringType },
                navArgument(AppRoute.ChatRoom.SELLER_ARG) {
                    type = NavType.StringType
                    defaultValue = "false"
                }
            )
        ) {
            ChatRoomScreen(onBack = { navController.popBackStack() })
        }

            screen(sharedScope, AppRoute.Academy.route) {
            AcademyScreen(onBack = { navController.popBackStack() })
        }

            screen(sharedScope, AppRoute.MediaHub.route) {
            MediaHubScreen(onBack = { navController.popBackStack() })
        }

            screen(sharedScope, AppRoute.Marketplace.route) {
            MarketplaceScreen(
                onBack = { navController.popBackStack() },
                onOpenWhatsApp = ::openUrl,
                onNewDeal = { navController.navigate(AppRoute.NewDeal.route) },
                onMyDeals = { navController.navigate(AppRoute.MyDeals.route) },
                onOpenDeal = { navController.navigate(AppRoute.DealDetail.of(it)) },
                // The seller thread, not the support one: this is a
                // conversation about an amount and a rate.
                onOpenSellerChat = {
                    navController.navigate(AppRoute.ChatRoom.of(it, seller = true))
                }
            )
        }

        // ── Settings family ──────────────────────────────────────────────
        //
        // Presented as sheets on iOS, so they rise from the bottom here rather
        // than sliding in from the side. Same gesture, same mental model.
            sheetRoute(sharedScope, AppRoute.Settings.route) {
            SettingsScreen(
                onClose = { navController.popBackStack() },
                onViewProfile = { navController.navigate(AppRoute.AccountDetails.route) },
                onEditProfile = { navController.navigate(AppRoute.EditProfile.route) },
                onMySignals = { navController.navigate(AppRoute.MySignals.route) },
                onMyDeals = { navController.navigate(AppRoute.MyDeals.route) },
                onAppearance = { navController.navigate(AppRoute.Appearance.route) },
                onContactSupport = { navController.navigate(AppRoute.ContactSupport.route) }
            )
        }

            sheetRoute(sharedScope, AppRoute.AccountDetails.route) {
            AccountDetailsScreen(onClose = { navController.popBackStack() })
        }

            sheetRoute(sharedScope, AppRoute.EditProfile.route) {
            EditProfileScreen(onBack = { navController.popBackStack() })
        }

            sheetRoute(sharedScope, AppRoute.MySignals.route) {
            MySignalsScreen(
                onClose = { navController.popBackStack() },
                onRecord = { navController.navigate(AppRoute.RecordResult.of(it.signalId)) }
            )
        }

            sheetRoute(sharedScope, AppRoute.MyDeals.route) {
            MyDealsScreen(
                onClose = { navController.popBackStack() },
                onOpenDeal = { navController.navigate(AppRoute.DealDetail.of(it)) },
                onNewDeal = { navController.navigate(AppRoute.NewDeal.route) }
            )
        }

            sheetRoute(sharedScope, AppRoute.NewDeal.route) {
            NewDealScreen(
                onClose = { navController.popBackStack() },
                onOpened = { dealId ->
                    // Straight into the deal, and the form is left behind: a
                    // member who backs out of the detail should not land on a
                    // filled-in form that would open a second deal.
                    navController.popBackStack()
                    navController.navigate(AppRoute.DealDetail.of(dealId))
                }
            )
        }

            sheetRoute(
            sharedScope,
            route = AppRoute.DealDetail.route,
            arguments = listOf(navArgument(AppRoute.DealDetail.ARG) { type = NavType.StringType })
        ) {
            DealDetailScreen(
                onClose = { navController.popBackStack() },
                onOpenChat = { navController.navigate(AppRoute.ChatList.route) }
            )
        }

            sheetRoute(sharedScope, AppRoute.Appearance.route) {
            AppearanceScreen(onClose = { navController.popBackStack() })
        }

            sheetRoute(sharedScope, AppRoute.ContactSupport.route) {
            val viewModel: com.talkswithtanha.twt.features.chat.ContactSupportViewModel =
                hiltViewModel()
            ContactSupportScreen(
                onClose = { navController.popBackStack() },
                onSend = viewModel::send
            )
        }

            sheetRoute(
            sharedScope,
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
}

/**
 * A destination, with the two scopes a shared element needs already in scope.
 *
 * Every screen provides them, not just the two ends of a transition: a card can
 * appear on any screen, and a card that only animates from some of them is
 * worse than one that never does.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
private fun NavGraphBuilder.screen(
    sharedScope: SharedTransitionScope,
    route: String,
    arguments: List<androidx.navigation.NamedNavArgument> = emptyList(),
    content: @Composable (androidx.navigation.NavBackStackEntry) -> Unit
) {
    composable(route = route, arguments = arguments) { entry ->
        CompositionLocalProvider(
            LocalSharedTransitionScope provides sharedScope,
            LocalNavAnimatedVisibilityScope provides this
        ) {
            content(entry)
        }
    }
}

/**
 * A destination that rises from the bottom, the way a sheet does on iOS.
 *
 * The slide is deliberately kept even where a shared element is flying at the
 * same time: the card carries the eye to the right place, and the sheet coming
 * up underneath is what says this is a new surface rather than the same screen
 * rearranging itself.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
private fun NavGraphBuilder.sheetRoute(
    sharedScope: SharedTransitionScope,
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
        popExitTransition = { slideOutVertically(tween(280)) { it } + fadeOut(tween(220)) }
    ) { entry ->
        CompositionLocalProvider(
            LocalSharedTransitionScope provides sharedScope,
            LocalNavAnimatedVisibilityScope provides this
        ) {
            content(entry)
        }
    }
}
