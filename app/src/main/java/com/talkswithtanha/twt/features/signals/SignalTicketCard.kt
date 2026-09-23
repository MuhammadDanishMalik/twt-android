package com.talkswithtanha.twt.features.signals

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.SouthEast
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.LocalTextStyle
import com.talkswithtanha.twt.core.designsystem.AnimatedNumber
import com.talkswithtanha.twt.core.designsystem.Haptics
import com.talkswithtanha.twt.core.designsystem.pressScale
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.designsystem.brand.PairFlagBadge
import com.talkswithtanha.twt.core.navigation.sharedCard
import com.talkswithtanha.twt.core.navigation.signalCardKey
import com.talkswithtanha.twt.core.model.Signal
import com.talkswithtanha.twt.core.navigation.sharedCard
import com.talkswithtanha.twt.core.navigation.signalCardKey
import com.talkswithtanha.twt.core.model.SignalType
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * One signal, as a trade ticket. The same card on the home feed and the Signals
 * screen — two cards that drift apart is how a set ends up looking assembled,
 * so there is one.
 *
 * Reading order, ported from `SignalTicketCard.swift`:
 *
 *  1. When, and what state it is in — top row.
 *  2. What it is — the pair, its flags, direction and timeframe.
 *  3. How it is doing — the big number on the right. Pips once there is a
 *     result; the risk/reward while it is still running, because a live trade
 *     has no result yet and inventing one would be a claim.
 *  4. The two numbers you act on — entry and target.
 *
 * There is no "current price". This app has no live quote feed, and a price
 * column that is quietly made up is worse than a column that is not there.
 *
 * Colour is semantic only: the glow and the big number are green for a buy or a
 * winning trade, red for a sell or a losing one. Everything else is grey.
 */
@Composable
fun SignalTicketCard(
    signal: Signal,
    isFollowing: Boolean,
    modifier: Modifier = Modifier,
    width: Dp? = null,
    showsFollowButton: Boolean = true,
    onOpen: (() -> Unit)? = null,
    onFollow: () -> Unit = {},
    onRequestUnfollow: () -> Unit = {}
) {
    val haptics = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current
    val interaction = remember { MutableInteractionSource() }
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(1600)
            copied = false
        }
    }

    val tint = signalTint(signal)

    Column(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .then(if (onOpen != null) Modifier.pressScale(interaction, 0.985f) else Modifier)
            // The same object on both screens: tapping this card flies it into
            // the detail hero rather than replacing one screen with another.
            .sharedCard(signalCardKey(signal.id))
            .clip(RoundedCornerShape(22.dp))
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color(0xFF1C1C1C), Color(0xFF0D0D0D))
                    )
                )
                // One pool of semantic colour rising from the lower left.
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(tint.copy(alpha = 0.26f), Color.Transparent),
                        center = Offset(size.width * 0.15f, size.height * 1.05f),
                        radius = 240f * density
                    )
                )
            }
            .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
            .then(
                if (onOpen == null) Modifier else Modifier.clickable(
                    interactionSource = interaction,
                    indication = null
                ) {
                    Haptics.tap(haptics)
                    onOpen()
                }
            )
            .padding(14.dp)
    ) {
        // ── Top row ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = utcTimestamp(signal.timestamp.time),
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )

            TicketIconButton(
                icon = if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                contentDescription = "Copy signal"
            ) {
                clipboard.setText(AnnotatedString(signal.shareText()))
                Haptics.success(haptics)
                copied = true
            }

            // Following only makes sense while the trade can still move.
            if (showsFollowButton && signal.status.isOngoing) {
                TicketIconButton(
                    icon = if (isFollowing) Icons.Filled.Notifications
                    else Icons.Outlined.Notifications,
                    contentDescription = if (isFollowing) "Following — tap to stop"
                    else "Follow this signal",
                    isOn = isFollowing
                ) {
                    if (isFollowing) onRequestUnfollow() else onFollow()
                }
            }

            Box(
                Modifier
                    .height(28.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.09f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.10f), CircleShape)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = signal.status.stored,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Identity + performance ───────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(Modifier.weight(1f)) {
                PairFlagBadge(pair = signal.pair, size = 30.dp)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = signal.pair,
                    color = Color.White,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = "${signal.type.stored} · ${signal.timeframe}",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isUpward(signal)) Icons.Filled.NorthEast
                        else Icons.Filled.SouthEast,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    // Rolls rather than blinking: this is the number that
                    // changes while a member is looking at the card, and the
                    // direction it rolls says which way before the digits do.
                    AnimatedNumber(
                        value = headline(signal),
                        style = LocalTextStyle.current.copy(
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = if (signal.pipsGained == null) Color.White else tint,
                        increasing = isUpward(signal)
                    )
                }
                Text(
                    text = if (signal.pipsGained == null) "risk / reward" else "pips",
                    color = if (signal.pipsGained == null) Color.White.copy(alpha = 0.45f) else tint,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Levels ───────────────────────────────────────────────────────
        Row(modifier = Modifier.fillMaxWidth()) {
            TicketLevel("ENTRY PRICE", signal.entryPrice, Modifier.weight(1f))
            TicketLevel(
                "TARGET",
                signal.takeProfits.lastOrNull()?.price ?: "—",
                Modifier.weight(1f),
                Alignment.CenterHorizontally
            )
            TicketLevel("STOP", signal.stopLoss, Modifier.weight(1f), Alignment.End)
        }
    }
}

@Composable
private fun TicketIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    isOn: Boolean = false,
    onClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    Box(
        Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isOn) Color.White else Color.White.copy(alpha = 0.09f))
            // Its own click target, not a gesture on the card, so tapping it
            // does not also open the detail screen underneath.
            .clickable {
                Haptics.tap(haptics)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isOn) Color.Black else Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
private fun TicketLevel(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    alignment: Alignment.Horizontal = Alignment.Start
) {
    Column(modifier, horizontalAlignment = alignment) {
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = value,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
    }
}

/**
 * Green for a buy or a trade in profit, red for a sell or a trade in loss. A
 * result, once there is one, outranks the direction.
 */
fun signalTint(signal: Signal): Color {
    signal.pipsGained?.let { return if (it >= 0) IosColors.BuyGreen else IosColors.SellRed }
    if (signal.status.isLost) return IosColors.SellRed
    return if (signal.type == SignalType.BUY) IosColors.BuyGreen else IosColors.SellRed
}

private fun isUpward(signal: Signal): Boolean {
    signal.pipsGained?.let { return it >= 0 }
    return signal.type == SignalType.BUY
}

private fun headline(signal: Signal): String {
    val pips = signal.pipsGained ?: return signal.riskReward
    return "${if (pips >= 0) "+" else ""}${pips.toInt()}"
}

/**
 * `19:28 UTC 2 August, 2026` — UTC because that is the clock forex runs on, and
 * a signal timed in the reader's local zone is ambiguous the moment it is
 * forwarded to somebody in another one.
 */
private fun utcTimestamp(epochMillis: Long): String =
    SimpleDateFormat("HH:mm 'UTC' d MMMM, yyyy", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(java.util.Date(epochMillis))

/** Plain-text form of the signal, for copy and share. */
fun Signal.shareText(): String = buildList {
    add("$pair — ${type.stored}")
    add("Status: ${status.stored}")
    add("Entry: $entryPrice")
    add("Stop loss: $stopLoss")
    takeProfits.forEach { add("${it.label}: ${it.price}${if (it.isHit) " ✓" else ""}") }
    add("via Trade with Tanha")
}.joinToString("\n")

/** `https://www.tradingview.com/chart/?symbol=EURUSD`. */
fun Signal.tradingViewUrl(): String? {
    val symbol = pair.uppercase().filter { it.isLetterOrDigit() }
    return if (symbol.isEmpty()) null
    else "https://www.tradingview.com/chart/?symbol=$symbol"
}
