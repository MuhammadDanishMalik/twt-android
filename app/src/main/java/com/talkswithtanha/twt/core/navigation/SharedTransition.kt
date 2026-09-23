package com.talkswithtanha.twt.core.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

/**
 * The two scopes a shared element needs, carried through the tree.
 *
 * Passing them down as parameters means every composable between the nav host
 * and a card has to declare two arguments it does not use — and a signal card
 * appears on the home carousel, the signals tab and inside the detail screen
 * itself. Locals keep the card's signature about signals.
 *
 * Null when there is no transition in flight, which is also what makes these
 * safe in previews and tests: a card outside a nav graph simply draws itself.
 */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Marks this element as the same object as the one with [key] on the screen
 * being opened, so it flies and resizes into place rather than one screen
 * cross-fading into another.
 *
 * `sharedBounds` rather than `sharedElement`: the card and the detail hero are
 * *different* compositions of the same signal — the detail one is wider and
 * drops the follow button — so what carries across is the bounds, with the
 * contents cross-fading inside them. `sharedElement` would insist the two are
 * the same content and stretch one into the other.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.sharedCard(key: String): Modifier = composed {
    val shared = LocalSharedTransitionScope.current
    val visibility = LocalNavAnimatedVisibilityScope.current

    if (shared == null || visibility == null) return@composed this

    with(shared) {
        sharedBounds(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = visibility,
            // Matched to the sheet's own slide, so the card arrives exactly as
            // the screen it is becoming settles.
            boundsTransform = { _, _ -> tween(durationMillis = 320) },
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds
        )
    }
}

/** The key both ends of a signal's transition agree on. */
fun signalCardKey(signalId: String): String = "signal-card-$signalId"
