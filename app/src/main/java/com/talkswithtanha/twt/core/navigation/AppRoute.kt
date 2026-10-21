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

    /** Confirming the address with a six-digit code, straight after sign-up. */
    data object VerifyEmail : AppRoute("verifyEmail")

    /** Recovering an account through the inbox. */
    data object ResetPassword : AppRoute("resetPassword")

    /** The gate the whole app turns on. Signed in, no live access code. */
    data object ProfileSetup : AppRoute("profileSetup")
    data object AccessGate : AppRoute("accessGate")

    // ── Bottom bar ───────────────────────────────────────────────────────
    data object Home : AppRoute("home")
    data object Signals : AppRoute("signals")
    data object ChatList : AppRoute("chatList")

    /**
     * Tanha's feed. It takes the third tab; the chats it replaced are one tap
     * away behind the icon in its top right corner.
     */
    data object Feed : AppRoute("feed")
    data object Markets : AppRoute("markets")
    data object News : AppRoute("news")

    data object FeedPost : AppRoute("feed/{postId}") {
        const val ARG = "postId"
        fun of(postId: String) = "feed/$postId"
    }

    data object NewFeedPost : AppRoute("feed/new")

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

    /** A lesson played in-app, with the member's notes for it. [START_ARG]
     *  opens it at a moment — used when a shared note is tapped. */
    data object Lesson : AppRoute("lesson/{videoId}?title={title}&start={start}") {
        const val ARG = "videoId"
        const val TITLE_ARG = "title"
        const val START_ARG = "start"
        fun of(videoId: String, title: String, startSeconds: Int = 0) =
            "lesson/$videoId?title=${android.net.Uri.encode(title)}&start=$startSeconds"
    }

    /** Tanha's YouTube channel, which the home screen's Watch button opens. */
    data object MediaHub : AppRoute("mediaHub")
    data object Marketplace : AppRoute("marketplace")

    // ── The settings family, presented as sheets on iOS ──────────────────
    data object Settings : AppRoute("settings")
    /** Read-only "View Profile"; editing is [EditProfile]. */
    data object AccountDetails : AppRoute("accountDetails")
    data object EditProfile : AppRoute("editProfile")
    data object MySignals : AppRoute("mySignals")
    data object MyDeals : AppRoute("myDeals")
    data object NewDeal : AppRoute("newDeal")

    data object DealDetail : AppRoute("deal/{dealId}") {
        const val ARG = "dealId"
        fun of(dealId: String) = "deal/$dealId"
    }
    // ── Support tickets ──────────────────────────────────────────────────
    /** The member's tickets — and, for staff holding the area, the queue. */
    data object SupportTickets : AppRoute("supportTickets")
    data object NewTicket : AppRoute("newTicket")

    data object SupportTicket : AppRoute("ticket/{ticketId}") {
        const val ARG = "ticketId"
        fun of(ticketId: String) = "ticket/$ticketId"
    }

    // ── Admin, for staff only ────────────────────────────────────────────
    //
    // Hidden from everybody else, and refused by the security rules regardless
    // of what the UI shows. Which of these a staff account may actually use
    // depends on the areas Tanha has given them — see `StaffArea`.
    data object AdminHome : AppRoute("admin")
    data object AdminPostSignal : AppRoute("admin/postSignal")
    data object AdminSignals : AppRoute("admin/signals")
    data object AdminDeals : AppRoute("admin/deals")
    data object AdminBanners : AppRoute("admin/banners")
    data object AdminChats : AppRoute("admin/chats")

    data object Appearance : AppRoute("appearance")
    data object ContactSupport : AppRoute("contactSupport")

    companion object {
        /** The three the bottom bar shows. Anything else hides it. */
        val bottomBarRoutes: Set<String> by lazy {
            setOf(Home.route, Signals.route, Markets.route, News.route, Feed.route)
        }
    }
}
