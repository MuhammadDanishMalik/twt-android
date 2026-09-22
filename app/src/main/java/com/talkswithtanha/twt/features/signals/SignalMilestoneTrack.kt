package com.talkswithtanha.twt.features.signals

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.model.Signal
import com.talkswithtanha.twt.core.model.SignalStatus

/**
 * Stop, entry and every target on one track, in the order price would reach
 * them, with the run between entry and the furthest thing hit drawn in.
 *
 * Ported from `SignalMilestoneTrack.swift`.
 */
@Composable
fun SignalMilestoneTrack(signal: Signal) {
    var drawn by remember(signal.id) { mutableStateOf(false) }
    LaunchedEffect(signal.id) {
        kotlinx.coroutines.delay(150)
        drawn = true
    }

    val stops = remember(signal) { milestones(signal) }

    DetailCard(
        title = "Milestones",
        accessory = {
            Text(
                text = summary(signal),
                color = summaryTint(signal),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    ) {
        val node = 22.dp

        // Each stop owns an equal slice of the width and sits centred in it, so
        // the last node cannot fall off the right edge and a long price under
        // the first one has room to sit under it rather than being clipped.
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(node + 56.dp)
        ) {
            val cell = maxWidth / stops.size
            val firstCentre = cell / 2
            val lastCentre = maxWidth - cell / 2

            // The unreached track, drawn between the outer two nodes.
            Box(
                Modifier
                    .offset(x = firstCentre, y = node / 2 - 1.5.dp)
                    .width(lastCentre - firstCentre)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.10f))
            )

            // The run that actually happened: entry to the furthest thing hit.
            val entryIndex = stops.indexOfFirst { it.kind == Kind.ENTRY }.coerceAtLeast(0)
            val reached = if (signal.status.isLost) {
                stops.indexOfFirst { it.kind == Kind.STOP }
            } else {
                stops.indexOfLast { it.state == State.HIT && it.kind == Kind.TARGET }
            }

            if (reached >= 0 && reached != entryIndex) {
                val from = cell * minOf(entryIndex, reached) + cell / 2
                val to = cell * maxOf(entryIndex, reached) + cell / 2
                val width by animateFloatAsState(
                    targetValue = if (drawn) (to - from).value else 0f,
                    animationSpec = tween(700),
                    label = "progress"
                )
                Box(
                    Modifier
                        .offset(x = from, y = node / 2 - 1.5.dp)
                        .width(width.dp)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(
                            if (signal.status.isLost) IosColors.SellRed else IosColors.BuyGreen
                        )
                )
            }

            stops.forEachIndexed { index, stop ->
                val scale by animateFloatAsState(
                    targetValue = if (drawn) 1f else 0.4f,
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 320f),
                    label = "node"
                )
                Column(
                    modifier = Modifier
                        .offset(x = cell * index)
                        .width(cell),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        Modifier
                            .size(node)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                alpha = if (drawn) 1f else 0f
                            }
                            .clip(CircleShape)
                            .background(stop.fill)
                            .border(1.5.dp, stop.ring, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        stop.glyph?.let {
                            Icon(
                                it,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                    Text(
                        text = stop.label,
                        color = stop.labelTint,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = stop.price,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private enum class Kind { STOP, ENTRY, TARGET }
private enum class State { PENDING, REACHED, HIT }

private data class Milestone(
    val kind: Kind,
    val label: String,
    val price: String,
    val state: State
) {
    val fill: Color
        get() = when {
            kind == Kind.STOP && state == State.HIT -> IosColors.SellRed
            kind == Kind.TARGET && state == State.HIT -> IosColors.BuyGreen
            kind == Kind.ENTRY && state == State.REACHED -> Color.White
            else -> Color(0xFF1A1A1A)
        }

    val ring: Color
        get() = when {
            kind == Kind.STOP -> IosColors.SellRed.copy(alpha = if (state == State.HIT) 1f else 0.55f)
            kind == Kind.TARGET && state == State.HIT -> IosColors.BuyGreen
            kind == Kind.ENTRY && state == State.REACHED -> Color.White
            else -> Color.White.copy(alpha = 0.25f)
        }

    val glyph: androidx.compose.ui.graphics.vector.ImageVector?
        get() = when {
            kind == Kind.TARGET && state == State.HIT -> Icons.Filled.Check
            kind == Kind.STOP && state == State.HIT -> Icons.Filled.Close
            else -> null
        }

    val labelTint: Color
        get() = when {
            kind == Kind.STOP -> IosColors.SellRed
            kind == Kind.TARGET && state == State.HIT -> IosColors.BuyGreen
            kind == Kind.ENTRY -> Color.White
            else -> Color.White.copy(alpha = 0.55f)
        }
}

private fun milestones(signal: Signal): List<Milestone> = buildList {
    add(
        Milestone(
            kind = Kind.STOP,
            label = "SL",
            price = signal.stopLoss,
            state = if (signal.status.isLost) State.HIT else State.PENDING
        )
    )
    add(
        Milestone(
            kind = Kind.ENTRY,
            label = "ENTRY",
            price = signal.entryPrice,
            state = if (signal.status == SignalStatus.PENDING) State.PENDING else State.REACHED
        )
    )
    signal.takeProfits.forEachIndexed { index, tp ->
        add(
            Milestone(
                kind = Kind.TARGET,
                label = tp.label.ifEmpty { "TP${index + 1}" },
                price = tp.price,
                state = if (isHit(signal, index)) State.HIT else State.PENDING
            )
        )
    }
}

/**
 * A target counts as hit if it says so, or if the signal's status implies it —
 * `TP2 HIT` means the first one went too, and a won trade means all of them.
 */
private fun isHit(signal: Signal, index: Int): Boolean {
    if (signal.takeProfits[index].isHit) return true
    return when (signal.status) {
        SignalStatus.WON -> true
        SignalStatus.TP2_HIT -> index <= 1
        SignalStatus.TP1_HIT -> index == 0
        else -> false
    }
}

private fun hitCount(signal: Signal): Int =
    signal.takeProfits.indices.count { isHit(signal, it) }

private fun summary(signal: Signal): String {
    if (signal.status.isLost) return "Stopped out"
    val total = signal.takeProfits.size
    val hit = hitCount(signal)
    if (hit == 0) return if (signal.status.isOngoing) "Running · no target yet" else "Closed"
    return if (hit == total) "All $total targets hit" else "$hit of $total targets hit"
}

private fun summaryTint(signal: Signal): Color = when {
    signal.status.isLost -> IosColors.SellRed
    hitCount(signal) > 0 -> IosColors.BuyGreen
    else -> Color.White.copy(alpha = 0.6f)
}
