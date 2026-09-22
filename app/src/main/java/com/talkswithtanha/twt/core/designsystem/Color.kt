package com.talkswithtanha.twt.core.designsystem

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The palette.
 *
 * Dark-first: the app is designed dark and light mode is a courtesy, not the
 * intent. Separation between surfaces comes from **luminance and a half-point
 * rim**, not from colour — which is why there is exactly one accent here and two
 * semantic colours, and why the home entry cards are deliberately monochrome.
 *
 * Material You dynamic colour is deliberately **off**. It would repaint the
 * accent from the member's wallpaper, and gold is the brand.
 */
object TwtColors {

    // ── Surfaces ─────────────────────────────────────────────────────────
    /** The app ground. Near-black rather than black, so an OLED panel still
     *  shows the card edges sitting on top of it. */
    val Background = Color(0xFF0A0A0A)
    val BackgroundElevated = Color(0xFF111111)

    /** The vertical wash behind every full screen. */
    val ScreenGradient = Brush.verticalGradient(
        listOf(Color(0xFF111111), Color(0xFF0A0A0A))
    )

    val Surface = Color(0xFF121212)
    val SurfaceElevated = Color(0xFF181818)

    /** ~7% white. The hairline that separates a card from the ground — the
     *  entire reason cards read as raised without a drop shadow. */
    val Hairline = Color(0x12FFFFFF)
    val HairlineStrong = Color(0x1FFFFFFF)

    // ── Text ─────────────────────────────────────────────────────────────
    val TextPrimary = Color(0xFFF5F5F5)
    val TextSecondary = Color(0xFFA0A0A0)
    val TextTertiary = Color(0xFF6B6B6B)

    // ── Accent ───────────────────────────────────────────────────────────
    /**
     * Premium gold. **Used sparingly** — a selected tab, a primary button, a
     * single badge. The moment it appears on more than one thing per screen it
     * stops meaning anything.
     */
    val Gold = Color(0xFFFFD700)
    val GoldDim = Color(0xFFB89B00)
    val GoldWash = Color(0x1AFFD700)

    // ── Semantic ─────────────────────────────────────────────────────────
    /**
     * These mean **direction** — long and short — and must not be borrowed for
     * chrome. A green "save" button in this app reads as a buy.
     */
    val Buy = Color(0xFF33C759)
    val Sell = Color(0xFFF04D4D)
    val BuyWash = Color(0x1A33C759)
    val SellWash = Color(0x1AF04D4D)

    val Warning = Color(0xFFFFB020)

    // ── Light mode ───────────────────────────────────────────────────────
    // Present so the app is legible if somebody forces light, not because it is
    // the intended look.
    val LightBackground = Color(0xFFF7F7F8)
    val LightSurface = Color(0xFFFFFFFF)
    val LightTextPrimary = Color(0xFF121212)
    val LightTextSecondary = Color(0xFF6B6B6B)
    val LightHairline = Color(0x14000000)
}

/**
 * The iOS palette, matched value for value.
 *
 * The two apps are the same product, so these are ports of `AppColors.swift`
 * rather than an Android interpretation of it: a member who has the app on both
 * a phone and an iPad should not be able to tell which is which from a
 * screenshot.
 */
object IosColors {
    /** `AppColors.background` — true black, which is what the home photograph
     *  and every sheet sit on. */
    val Background = Color(0xFF000000)
    /** `secondarySystemBackground` in dark: the grouped-settings card fill. */
    val SecondaryBackground = Color(0xFF1C1C1E)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF8E8E93)

    /** `AppColors.primary` — the system accent. The blue on "See All" and
     *  "Add result". */
    val Accent = Color(0xFF0A84FF)

    val BuyGreen = Color(0xFF33C759)
    val SellRed = Color(0xFFF04D4D)
    val Warning = Color(0xFFFF9F0A)
    val Gold = Color(0xFFFFD700)

    /** The avatar and the profile tile: primary into premium violet. */
    val AvatarGradient = listOf(Accent, Color(0xFF6B29E0))

    /** Settings row icon tiles, from `SettingsIconColor`. */
    object SettingsIcon {
        val Account = Color(0xFF707076)
        val Signals = Color(0xFF3399F2)
        val Deals = Color(0xFF5E5CE6)
        val Notifications = Color(0xFFFF3B30)
        val Permissions = Color(0xFF33C759)
        val Appearance = Color(0xFFFF2E91)
        val Support = Color(0xFF0A85FF)
        val Rate = Color(0xFFFFCC00)
        val SignOut = Color(0xFF8E8E93)
        val YouTube = Color(0xFFFF0000)
        val Instagram = Color(0xFFE1306C)
        val WhatsApp = Color(0xFF25D366)
    }

    /** Card surfaces from the iOS components: `Color(white: 0.08)` and friends. */
    val CardSurface = Color(0xFF141414)
    val TicketTop = Color(0xFF1C1C1C)
    val TicketBottom = Color(0xFF0D0D0D)
    val EntrySurfaceTop = Color(0xFF1A1A1A)
    val EntrySurfaceBottom = Color(0xFF111111)
    val Rim = Color(0x14FFFFFF)
}
