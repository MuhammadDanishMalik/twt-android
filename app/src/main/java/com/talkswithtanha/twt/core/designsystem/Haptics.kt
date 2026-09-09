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

    /** A tap that did something: a button, a card, a tab. */
    @Composable
    fun rememberTap(): () -> Unit {
        val haptics = LocalHapticFeedback.current
        return { haptics.performHapticFeedback(HapticFeedbackType.ContextClick) }
    }

    /** A press-and-hold that opened something. */
    @Composable
    fun rememberLongPress(): () -> Unit {
        val haptics = LocalHapticFeedback.current
        return { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
    }

    fun tap(haptics: HapticFeedback) =
        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)

    /** Something completed — a code redeemed, a result recorded. */
    fun success(haptics: HapticFeedback) =
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)

    /** Something was refused. */
    fun failure(haptics: HapticFeedback) =
        haptics.performHapticFeedback(HapticFeedbackType.Reject)
}

/** Set to false in previews and screenshot tests, where a buzz is noise. */
val LocalHapticsEnabled = staticCompositionLocalOf { true }
