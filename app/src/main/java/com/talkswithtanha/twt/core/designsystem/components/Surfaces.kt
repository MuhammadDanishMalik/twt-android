package com.talkswithtanha.twt.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.talkswithtanha.twt.core.designsystem.Haptics
import com.talkswithtanha.twt.core.designsystem.pressScale
import com.talkswithtanha.twt.core.designsystem.LocalHapticsEnabled
import com.talkswithtanha.twt.core.designsystem.Radius
import com.talkswithtanha.twt.core.designsystem.Spacing
import com.talkswithtanha.twt.core.designsystem.TwtColors

/**
 * The screen ground: the near-black vertical wash every full screen sits on.
 */
@Composable
fun TwtScreen(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TwtColors.ScreenGradient),
        content = content
    )
}

/**
 * The card.
 *
 * Raised by luminance and a hairline rim rather than by a drop shadow. A shadow
 * on a near-black ground is invisible; the ~7% white border is what actually
 * draws the edge, and it is the single most repeated detail in the app.
 *
 * The tap haptic lives in here rather than at the call site — see [Haptics].
 */
@Composable
fun TwtCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: RoundedCornerShape = RoundedCornerShape(Radius.card),
    contentPadding: Dp = Spacing.lg,
    border: BorderStroke? = BorderStroke(0.5.dp, TwtColors.Hairline),
    content: @Composable ColumnScope.() -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val hapticsEnabled = LocalHapticsEnabled.current
    val interaction = remember { MutableInteractionSource() }

    val base = modifier
        .then(if (onClick != null) Modifier.pressScale(interaction) else Modifier)
        .clip(shape)
        .background(MaterialTheme.colorScheme.surface)
        .let { if (border != null) it.border(border, shape) else it }
        .let { chain ->
            if (onClick == null) chain else chain.clickable(
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(color = TwtColors.Gold)
            ) {
                if (hapticsEnabled) Haptics.tap(haptics)
                onClick()
            }
        }

    androidx.compose.foundation.layout.Column(
        modifier = base.padding(contentPadding),
        content = content
    )
}
