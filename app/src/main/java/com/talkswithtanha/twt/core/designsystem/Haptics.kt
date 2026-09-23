package com.talkswithtanha.twt.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Haptics, for use **inside shared components rather than at call sites**.
 *
 * This is the whole rule from the design brief and it is worth stating plainly:
 * a flow where four buttons buzz and the fifth does not is worse than one where
 * none of them do. Putting the feedback inside [TwtButton], [TwtCard] and the
 * bottom bar means a screen cannot forget it, and a new screen gets it for free.
 *
 * Nothing outside this package should be calling `LocalHapticFeedback` directly.
 */
object Haptics {

    // ── Taps ────────────────────────────────────────────────────────────
    //
    // The lightest thing in the vocabulary, and the most used. Anything
    // heavier here would make ordinary navigation feel like an event.

    /** A tap that did something: a button, a card, a tab. */
    @Composable
    fun rememberTap(): () -> Unit {
        val haptics = LocalHapticFeedback.current
        return { haptics.performHapticFeedback(HapticFeedbackType.ContextClick) }
    }

    fun tap(haptics: HapticFeedback) =
        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)

    /** A press-and-hold that opened something. */
    @Composable
    fun rememberLongPress(): () -> Unit {
        val haptics = LocalHapticFeedback.current
        return { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
    }

    /** One digit of a code. Lighter than a tap, because six land in a row. */
    fun key(haptics: HapticFeedback) =
        haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)

    // ── State ───────────────────────────────────────────────────────────
    //
    // A switch has two distinct feelings on purpose. Following a trade and
    // unfollowing it are opposite decisions, and a member who taps the bell by
    // accident should be able to tell which way it went without looking.

    fun toggleOn(haptics: HapticFeedback) =
        haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)

    fun toggleOff(haptics: HapticFeedback) =
        haptics.performHapticFeedback(HapticFeedbackType.ToggleOff)

    fun toggle(haptics: HapticFeedback, on: Boolean) =
        if (on) toggleOn(haptics) else toggleOff(haptics)

    // ── Surfaces ────────────────────────────────────────────────────────

    /** A sheet or dialog arriving. */
    fun sheetOpen(haptics: HapticFeedback) =
        haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)

    /** The same surface leaving. */
    fun sheetClose(haptics: HapticFeedback) =
        haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)

    // ── Movement ────────────────────────────────────────────────────────

    /**
     * A detent: a deck card snapping into place, a crop hitting its limit.
     *
     * The one piece of feedback here that is about the gesture rather than the
     * outcome — it is what makes a drag feel like it is moving against
     * something rather than through nothing.
     */
    fun tick(haptics: HapticFeedback) =
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)

    // ── Outcomes ────────────────────────────────────────────────────────
    //
    // The heavy end, and deliberately rare. These fire when money moves or
    // access changes — if they fire on ordinary taps they stop meaning
    // anything, and then nothing in the app can signal that something real
    // just happened.

    /** Something completed — a deal confirmed, a code redeemed, a result recorded. */
    fun success(haptics: HapticFeedback) =
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)

    /** Something was refused — a wrong code, a rejected payment. */
    fun failure(haptics: HapticFeedback) =
        haptics.performHapticFeedback(HapticFeedbackType.Reject)
}

/** Set to false in previews and screenshot tests, where a buzz is noise. */
val LocalHapticsEnabled = staticCompositionLocalOf { true }
