package com.talkswithtanha.twt.core.designsystem

import androidx.compose.ui.unit.dp

/**
 * The spacing scale. Every gap and inset in the app comes from here.
 *
 * A fixed scale rather than free numbers because the alternative is what the
 * scaffold had: 12 in one card, 14 in the next, and a layout that never quite
 * lines up without anyone being able to say why.
 */
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 40.dp
    val huge = 48.dp
}

/** Corner radii, which are as load-bearing as the spacing scale. */
object Radius {
    /** Cards. */
    val card = 20.dp
    /** Grouped settings cards — slightly tighter, so a settings list does not
     *  read as a stack of content cards. */
    val grouped = 18.dp
    val chip = 12.dp
    val sheet = 28.dp
    /** The floating bottom bar. Half its 64dp height, so it is a true pill. */
    val pill = 32.dp
}
