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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talkswithtanha.twt.core.designsystem.Haptics

data class BottomBarItem(
    val label: String,
    val route: String,
    /** Drawn, not an `ImageVector` — see [TabIcons]. */
    val icon: @Composable (Color, Modifier) -> Unit
)

/**
 * The floating tab bar.
 *
 * Matched to the iOS one: a dark pill hovering above the content, three items,
 * and the selected one wearing its own lighter pill behind the icon and label.
 * The whole bar is sized to its contents rather than stretched across the
 * screen, because it is meant to read as an object sitting on the content.
 */
@Composable
fun TwtBottomBar(
    items: List<BottomBarItem>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Row(
        modifier = modifier
            // Clear of the gesture pill or the button bar, whichever the phone
            // has. Reading the inset rather than guessing keeps it off both.
            .padding(bottom = bottomInset + 8.dp)
            .height(64.dp)
            .clip(CircleShape)
            .background(Color(0xFF1C1C1E).copy(alpha = 0.96f))
            .border(0.5.dp, Color.White.copy(alpha = 0.10f), CircleShape)
            // Inset past the pill's own corner radius. At 8dp the selected
            // chip's corners poked out through the rounded end of the bar,
            // because the bar's edge curves away faster than a straight inset
            // accounts for.
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            BottomBarTab(
                item = item,
                selected = currentRoute == item.route,
                onClick = { onNavigate(item.route) }
            )
        }
    }
}

@Composable
private fun BottomBarTab(
    item: BottomBarItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current

    val tint by animateColorAsState(
        if (selected) Color.White else Color.White.copy(alpha = 0.45f),
        label = "tint"
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.94f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    Column(
        modifier = Modifier
            // A capsule, like the bar it sits in. A rounded rectangle inside a
            // pill reads as a button that does not quite fit.
            .clip(CircleShape)
            .background(
                if (selected) Color.White.copy(alpha = 0.10f) else Color.Transparent
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                // Re-selecting the tab you are on should not buzz. A haptic is a
                // confirmation that something happened, and nothing did.
                enabled = !selected
            ) {
                Haptics.tap(haptics)
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 7.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        item.icon(tint, Modifier.size(24.dp))
        Text(
            text = item.label,
            fontSize = 11.sp,
            color = tint
        )
    }
}
