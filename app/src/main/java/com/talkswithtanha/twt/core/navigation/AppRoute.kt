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

    data object RecordResult : AppRoute("record/{signalId}") {
        const val ARG = "signalId"
        fun of(signalId: String) = "record/$signalId"
    }

    data object ChatRoom : AppRoute("chat/{roomId}?seller={seller}") {
        const val ARG = "roomId"
        const val SELLER_ARG = "seller"

        /**
         * [seller] switches the room to the marketplace presentation — the
         * verified header and the rate quick-replies. It is the same underlying
         * thread: `support_{uid}` is the only per-member private room the
         * security rules allow, so the exchange conversation and the support
         * conversation are one conversation, which is also how Tanha sees it in
         * the admin panel's queue.
         */
        fun of(roomId: String, seller: Boolean = false) = "chat/$roomId?seller=$seller"
    }

    data object Academy : AppRoute("academy")

    /** Tanha's YouTube channel, which the home screen's Watch button opens. */
    data object MediaHub : AppRoute("mediaHub")
    data object Marketplace : AppRoute("marketplace")

    // ── The settings family, presented as sheets on iOS ──────────────────
    data object Settings : AppRoute("settings")
    data object EditProfile : AppRoute("editProfile")
    data object MySignals : AppRoute("mySignals")
    data object MyDeals : AppRoute("myDeals")
    data object NewDeal : AppRoute("newDeal")

    data object DealDetail : AppRoute("deal/{dealId}") {
        const val ARG = "dealId"
        fun of(dealId: String) = "deal/$dealId"
    }
    data object Appearance : AppRoute("appearance")
    data object ContactSupport : AppRoute("contactSupport")

    companion object {
        /** The three the bottom bar shows. Anything else hides it. */
        val bottomBarRoutes: Set<String> by lazy {
            setOf(Home.route, Signals.route, ChatList.route)
        }
    }
}
