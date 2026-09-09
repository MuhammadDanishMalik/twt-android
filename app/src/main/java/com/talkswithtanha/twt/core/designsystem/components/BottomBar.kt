package com.talkswithtanha.twt.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talkswithtanha.twt.core.designsystem.Haptics
import com.talkswithtanha.twt.core.designsystem.LocalHapticsEnabled
import com.talkswithtanha.twt.core.designsystem.Radius
import com.talkswithtanha.twt.core.designsystem.Spacing
import com.talkswithtanha.twt.core.designsystem.TwtColors

data class BottomBarItem(
    val label: String,
    val icon: ImageVector,
    val route: String
)

/**
 * The floating pill: 270 × 64dp, radius 32, 8dp above the system bar, 24dp
 * icons, 14sp labels.
 *
 * Three items — Home, Signals, Chat — and that count is part of the design
 * rather than an accident of what exists. The scaffold this replaced had four,
 * with Markets and Profile competing for the same space; the marketplace and
 * profile are reached from the home screen instead, which keeps the pill narrow
 * enough to actually float.
 *
 * A fixed width rather than `fillMaxWidth` with padding, because the pill is
 * meant to read as an object sitting on the content, not as a bar attached to
 * the bottom of the screen.
 */
@Composable
fun TwtBottomBar(
    items: List<BottomBarItem>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = modifier
            // 8dp above the gesture pill or the button bar, whichever the phone
            // has. Reading the inset rather than guessing is what keeps it clear
            // of both.
            .padding(bottom = bottomInset + Spacing.sm)
            .width(270.dp)
            .height(64.dp)
            .clip(RoundedCornerShape(Radius.pill))
            .background(TwtColors.BackgroundElevated)
            .border(0.5.dp, TwtColors.HairlineStrong, RoundedCornerShape(Radius.pill)),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                BottomBarTab(
                    item = item,
                    selected = currentRoute == item.route,
                    onClick = { onNavigate(item.route) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun BottomBarTab(
    item: BottomBarItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val hapticsEnabled = LocalHapticsEnabled.current

    val tint by animateColorAsState(
        if (selected) TwtColors.Gold else TwtColors.TextTertiary,
        label = "tint"
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.92f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.chip))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                // Re-selecting the tab you are on should not buzz. A haptic is a
                // confirmation that something happened, and nothing did.
                enabled = !selected
            ) {
                if (hapticsEnabled) Haptics.tap(haptics)
                onClick()
            }
            .padding(vertical = Spacing.sm)
            .scale(scale),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp),
            color = tint
        )
    }
}
