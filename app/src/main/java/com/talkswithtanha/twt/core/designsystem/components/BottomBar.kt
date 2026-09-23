package com.talkswithtanha.twt.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.talkswithtanha.twt.core.designsystem.Motion

data class BottomBarItem(
    val label: String,
    val route: String,
    /** Drawn, not an `ImageVector` — see `TabIcons`. */
    val icon: @Composable (Color, Modifier) -> Unit
)

/** Width of one tab, and of the indicator that slides between them. */
private val TabWidth = 92.dp

/**
 * The floating tab bar.
 *
 * The selected state is **one indicator that slides**, not a background that
 * appears and disappears on three separate chips. Moving a single object is
 * what tells you the two tabs are the same control in different positions;
 * cross-fading two highlights reads as one thing vanishing and another arriving.
 */
@Composable
fun TwtBottomBar(
    items: List<BottomBarItem>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val selectedIndex = items.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)

    val indicatorOffset by animateDpAsState(
        targetValue = TabWidth * selectedIndex,
        animationSpec = Motion.snappy(),
        label = "indicator"
    )

    Box(
        modifier = modifier
            // Clear of the gesture pill or the button bar, whichever the phone
            // has. Reading the inset rather than guessing keeps it off both.
            .padding(bottom = bottomInset + 8.dp)
            .height(64.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(Color(0xFF1C1C1E).copy(alpha = 0.96f))
            .border(0.5.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(32.dp))
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .offset(x = indicatorOffset)
                .width(TabWidth)
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Color.White.copy(alpha = 0.10f))
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            items.forEachIndexed { index, item ->
                BottomBarTab(
                    item = item,
                    selected = index == selectedIndex,
                    onClick = { onNavigate(item.route) }
                )
            }
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
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val tint by animateColorAsState(
        targetValue = if (selected) Color.White else Color.White.copy(alpha = 0.45f),
        animationSpec = Motion.quick(),
        label = "tint"
    )
    // A small lift on the selected tab, and a dip under a finger. Both spring,
    // so tapping mid-animation interrupts rather than queues.
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.92f
            selected -> 1f
            else -> 0.94f
        },
        animationSpec = Motion.snappy(),
        label = "scale"
    )

    Column(
        modifier = Modifier
            .width(TabWidth)
            .clip(RoundedCornerShape(26.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                // Re-selecting the tab you are on should not buzz. A haptic is a
                // confirmation that something happened, and nothing did.
                enabled = !selected
            ) {
                Haptics.tap(haptics)
                onClick()
            }
            .padding(vertical = 7.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        item.icon(tint, Modifier.size(24.dp))
        Text(text = item.label, fontSize = 11.sp, color = tint)
    }
}
