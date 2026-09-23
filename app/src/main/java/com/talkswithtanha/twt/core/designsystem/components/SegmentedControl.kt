package com.talkswithtanha.twt.core.designsystem.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.talkswithtanha.twt.core.designsystem.Haptics
import com.talkswithtanha.twt.core.designsystem.Motion

/**
 * The iOS segmented control: a track, and a pill that slides between segments.
 *
 * The pill moves rather than appearing, because the movement is what says the
 * two sides are one thing seen two ways. A control where the highlight cuts
 * from box to box reads as two buttons that happen to sit together, and then
 * nobody expects the content behind them to be related.
 *
 * Sized from the measured track rather than from a guess, so it stays right at
 * any width and in any language — a hardcoded segment width is how these end up
 * with the pill half-covering a word on a small screen.
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit
) {
    if (options.isEmpty()) return

    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    var trackWidth by remember { mutableIntStateOf(0) }

    val segmentWidth = with(density) { (trackWidth / options.size).toDp() }
    val offset by animateDpAsState(
        targetValue = segmentWidth * selectedIndex,
        animationSpec = Motion.snappy(),
        label = "segment"
    )

    Box(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Track)
            .padding(2.dp)
            .onSizeChanged { trackWidth = it.width }
    ) {
        if (trackWidth > 0) {
            Box(
                Modifier
                    .offset(x = offset)
                    .width(segmentWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Pill)
            )
        }

        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                // The unselected label is dimmed rather than a different
                // colour, so the pill is the only thing that changes hue.
                val alpha by animateFloatAsState(
                    targetValue = if (selected) 1f else 0.5f,
                    animationSpec = Motion.quick(),
                    label = "label"
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        // No indication: the pill already answers the tap, and
                        // a ripple underneath it fights the slide.
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (!selected) {
                                Haptics.tick(haptics)
                                onSelect(index)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = Color.White.copy(alpha = alpha),
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

/** The recessed groove the pill sits in. */
private val Track = Color(0xFF1C1C1E)

/** The pill itself, a shade above the track. */
private val Pill = Color(0xFF39393D)
