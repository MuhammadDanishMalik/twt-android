package com.talkswithtanha.twt.core.navigation

/**
 * Every destination in the app.
 *
 * A sealed class rather than loose strings so a typo in a route is a compile
 * error rather than a screen that silently fails to open — the Navigation
 * Compose equivalent of iOS's `AppRoute` enum.
 */
sealed class AppRoute(val route: String) {

    // ── Pre-auth ─────────────────────────────────────────────────────────
    data object Splash : AppRoute("splash")
    data object Onboarding : AppRoute("onboarding")
    data object SignIn : AppRoute("signIn")
    data object SignUp : AppRoute("signUp")

    /** The gate the whole app turns on. Signed in, no live access code. */
    data object AccessGate : AppRoute("accessGate")

    // ── Bottom bar ───────────────────────────────────────────────────────
    data object Home : AppRoute("home")
    data object Signals : AppRoute("signals")
    data object ChatList : AppRoute("chatList")

    // ── Pushed ───────────────────────────────────────────────────────────
    data object SignalDetail : AppRoute("signal/{signalId}") {
        const val ARG = "signalId"
        fun of(signalId: String) = "signal/$signalId"
    }

    data object ChatRoom : AppRoute("chat/{roomId}") {
        const val ARG = "roomId"
        fun of(roomId: String) = "chat/$roomId"
    }

    data object MySignals : AppRoute("mySignals")
    data object Academy : AppRoute("academy")
    data object Marketplace : AppRoute("marketplace")
    data object Profile : AppRoute("profile")
    data object EditProfile : AppRoute("editProfile")

    companion object {
        /**
         * The three the bottom bar shows. Anything else hides it.
         *
         * `by lazy`, and not for performance. A sealed class's companion is
         * initialised as part of the *parent's* static init, which runs before
         * the nested `data object`s below it exist -- so building this set
         * eagerly reads `Home.route` off a null reference and the app dies in
         * `<clinit>` before drawing a frame. Deferring it to first access means
         * the objects are there by the time it runs.
         */
        val bottomBarRoutes: Set<String> by lazy {
            setOf(Home.route, Signals.route, ChatList.route)
        }
    }
}
