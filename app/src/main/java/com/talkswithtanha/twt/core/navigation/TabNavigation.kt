package com.talkswithtanha.twt.core.navigation

import androidx.navigation.NavController

/**
 * Switches to one of the bottom-bar tabs. Everything that opens a tab — the bar
 * itself, "See All" on Home, the support shortcut — goes through here, so there
 * is one set of back-stack rules rather than several that disagree.
 *
 * ─── Why Home is special ─────────────────────────────────────────────────────
 *
 * The other tabs use the usual pattern: pop back to Home saving what was above
 * it, then restore the target tab's own saved stack. Applying that same pattern
 * to Home itself is the bug this function exists to fix. `popUpTo(Home) {
 * saveState = true }` files the popped Signals stack under *Home's* key, and
 * `restoreState = true` on the way to Home then restores exactly that — so
 * tapping Home put Signals straight back, and once the saved state went stale
 * it restored nothing at all and left a black screen under the tab bar.
 *
 * Home is the root, so going to it is just popping back to it.
 */
fun NavController.navigateToTab(route: String) {
    if (route == AppRoute.Home.route) {
        // False when Home is not on the stack at all, which should not happen
        // once the gate has let the member in; rebuild the stack from Home
        // rather than leave them on a screen with no way back.
        if (!popBackStack(AppRoute.Home.route, inclusive = false)) {
            navigate(AppRoute.Home.route) {
                popUpTo(graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
        return
    }

    navigate(route) {
        popUpTo(AppRoute.Home.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
