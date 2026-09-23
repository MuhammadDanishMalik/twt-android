package com.talkswithtanha.twt.features.home.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talkswithtanha.twt.core.designsystem.Haptics
import com.talkswithtanha.twt.core.designsystem.components.RedactedText
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.designsystem.brand.TwtWordmark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The member's card: who they are, that they are in, and how long they have
 * left.
 *
 * Drawn as a physical object — a black metal card — because that is what an
 * access code *is* to the member: something they were issued, with their name on
 * it and a date it runs out. Ported from `TWTAccessCard.swift`.
 *
 * Black, not gold. The colour lives only in the wordmark, the way foil sits on a
 * real metal card; a gold surface made the whole thing read as a promotion
 * rather than a possession.
 */
@Composable
fun TwtAccessCard(
    holderName: String?,
    expiresAt: Date?,
    grantedAt: Date?,
    accessCode: String?,
    onContactSupport: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Recomputed each minute so "3 hours left" does not sit on screen saying
    // three hours for the rest of the afternoon.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = System.currentTimeMillis()
        }
    }

    val state = remember(expiresAt, grantedAt, now) {
        AccessState(expiresAt, grantedAt, Date(now))
    }

    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    // Tilt, in the -1..1 range the iOS card uses.
    val tiltX = remember { Animatable(0f) }
    val tiltY = remember { Animatable(0f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            // 226 on iOS, where the type metrics are tighter. Android's line
            // heights push the remaining-time line past the bottom edge at that
            // height, so the card is a few points taller rather than clipped.
            .height(242.dp)
            .graphicsLayer {
                // A real 3D tilt rather than a skew: the card leans away from
                // the finger, which is what sells it as an object rather than a
                // picture of one.
                rotationX = -tiltY.value * 8f
                rotationY = tiltX.value * 8f
                cameraDistance = 14f * density
            }
            .clip(RoundedCornerShape(24.dp))
            .drawBehind {
                drawRect(
                    Brush.linearGradient(
                        listOf(Color(0xFF242424), Color(0xFF121212), Color(0xFF080808)),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height)
                    )
                )
                // A brushed diagonal sheen that slides with the tilt. This, the
                // ground above and the rim below are the whole "metal" effect —
                // no texture image, no colour.
                drawRect(
                    Brush.linearGradient(
                        colorStops = arrayOf(
                            0.30f to Color.Transparent,
                            0.45f to Color.White.copy(alpha = 0.06f),
                            0.62f to Color.Transparent
                        ),
                        start = Offset(tiltY.value * 0.3f * size.width, 0f),
                        end = Offset((1f + tiltY.value * 0.3f) * size.width, size.height)
                    )
                )
            }
            .border(
                0.75.dp,
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.22f),
                        Color.White.copy(alpha = 0.05f),
                        Color.White.copy(alpha = 0.12f)
                    )
                ),
                RoundedCornerShape(24.dp)
            )
            .pointerInput(state.needsRenewal) {
                detectTapGestures {
                    // Renewal is a conversation with Tanha, so a card near its
                    // end opens the support thread rather than anything
                    // resembling a purchase. A healthy card is not a button.
                    if (state.needsRenewal) {
                        Haptics.tap(haptics)
                        onContactSupport()
                    }
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        scope.launch { tiltX.animateTo(0f, spring()) }
                        scope.launch { tiltY.animateTo(0f, spring()) }
                    }
                ) { change, drag ->
                    change.consume()
                    scope.launch {
                        tiltX.snapTo((tiltX.value + drag.x / size.width * 2f).coerceIn(-1f, 1f))
                    }
                    scope.launch {
                        tiltY.snapTo((tiltY.value + drag.y / size.height * 2f).coerceIn(-1f, 1f))
                    }
                }
            }
            .padding(16.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TWT ACCESS CARD",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp
                )
                StatusPill(state)
            }

            Spacer(Modifier.height(12.dp))

            TwtWordmark(
                modifier = Modifier
                    .height(44.dp)
                    .width(44.dp * com.talkswithtanha.twt.core.designsystem.brand.TwtLetterforms.ASPECT_RATIO)
                    // A touch of depth against the surface, so the foil sits
                    // *on* the card rather than being printed flat into it.
                    .graphicsLayer {
                        translationX = tiltX.value * 4f
                        translationY = tiltY.value * 4f
                    },
                tiltShift = tiltY.value * 0.45f + tiltX.value * 0.20f
            )

            maskedCode(accessCode)?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = it,
                    color = Color.White.copy(alpha = 0.62f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp
                )
            }

            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                CardField("CARDHOLDER", holderDisplay(holderName), placeholderWidth = 132.dp)
                CardField("VALID THRU", state.validThru, TextAlign.End)
            }

            Spacer(Modifier.height(8.dp))

            state.fractionRemaining?.let { fraction ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(max(0.012f, fraction))
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(state.tint.copy(alpha = 0.7f), state.tint)
                                )
                            )
                    )
                }
                Spacer(Modifier.height(6.dp))
            }

            Text(
                text = state.remainingText,
                color = if (state.needsRenewal) state.tint else Color.White.copy(alpha = 0.55f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun StatusPill(state: AccessState) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.07f))
            .border(0.5.dp, Color.White.copy(alpha = 0.10f), CircleShape)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(state.tint)
        )
        Text(
            text = state.label,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.4.sp
        )
    }
}

@Composable
private fun CardField(
    title: String,
    value: String?,
    align: TextAlign = TextAlign.Start,
    placeholderWidth: Dp = 96.dp
) {
    Column(horizontalAlignment = if (align == TextAlign.End) Alignment.End else Alignment.Start) {
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.40f),
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.6.sp
        )
        Spacer(Modifier.height(3.dp))
        // Set like embossed type on a real card — uppercase, tracked, in a
        // monospaced face so a long name and a short date sit on the same
        // baseline rhythm.
        // Null while the member's record is still loading: an embossed name
        // that cuts from "MEMBER" to the real one is the single most obvious
        // tell that the card was drawn before the data arrived.
        RedactedText(
            text = value,
            placeholderWidth = placeholderWidth,
            style = LocalTextStyle.current.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.2.sp
            ),
            color = Color.White,
            maxLines = 1
        )
    }
}

/**
 * Null only while the member record is still in flight, so the field shimmers
 * rather than showing a stand-in that will be replaced a moment later.
 *
 * A record that has loaded and simply carries no name is a different thing and
 * still reads "MEMBER" — otherwise that member's card would shimmer forever.
 */
private fun holderDisplay(name: String?): String? =
    name?.trim()?.ifEmpty { "MEMBER" }?.uppercase()

/** `TWT4H2K9XQP` → `TWT  ••••  9XQP`. */
private fun maskedCode(code: String?): String? {
    if (code == null || code.length < 7) return null
    return "TWT  ••••  ${code.takeLast(4)}"
}

/**
 * Everything the card says about time, worked out once from three dates.
 */
private class AccessState(
    private val expiresAt: Date?,
    private val grantedAt: Date?,
    private val now: Date
) {
    private val secondsLeft: Double? =
        expiresAt?.let { (it.time - now.time) / 1000.0 }

    private val isExpired: Boolean get() = (secondsLeft ?: 1.0) <= 0

    /**
     * A fortnight. Renewal here is a message and a transfer checked by hand,
     * and neither happens at 2am on the last night.
     */
    val needsRenewal: Boolean
        get() = secondsLeft?.let { it <= 14 * 86_400 } ?: false

    /**
     * Never "LIFETIME" here, even for a lifetime code: that word already sits
     * under Valid Thru, and a card that says it twice reads as filled in by a
     * form rather than designed.
     */
    val label: String
        get() = when {
            isExpired -> "EXPIRED"
            needsRenewal -> "RENEW SOON"
            else -> "ACTIVE"
        }

    /** The one semantic colour on the card, and only as a dot and a thin bar. */
    val tint: Color
        get() = when {
            isExpired -> IosColors.SellRed
            needsRenewal -> IosColors.Warning
            else -> IosColors.BuyGreen
        }

    val validThru: String
        get() {
            val end = expiresAt ?: return "LIFETIME"
            val calendar = Calendar.getInstance().apply { time = end }
            return "%02d/%02d".format(
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.YEAR) % 100
            )
        }

    /**
     * Share of the term still to run. Null for a lifetime code, or for an
     * account granted before `accessGrantedAt` was recorded — a bar with a
     * made-up starting point would be a guess drawn to look like a fact.
     */
    val fractionRemaining: Float?
        get() {
            val end = expiresAt ?: return null
            val start = grantedAt ?: return null
            if (end.time <= start.time) return null
            val total = (end.time - start.time).toDouble()
            return min(1.0, max(0.0, (end.time - now.time) / total)).toFloat()
        }

    val remainingText: String
        get() {
            val seconds = secondsLeft ?: return "Never expires"
            if (seconds <= 0) return "Access ended — tap to renew"

            val span = if (seconds < 86_400) {
                val hours = (seconds / 3_600).toInt()
                when {
                    hours < 1 -> "Less than an hour"
                    hours == 1 -> "1 hour"
                    else -> "$hours hours"
                }
            } else {
                // Rounded *up*. A code expiring 64 days from now is 63.99 days
                // away a second after it is issued, and flooring that says
                // "63 days left" to somebody who was just told they had 64.
                // People count days the way a calendar does.
                val days = kotlin.math.ceil(seconds / 86_400).toInt()
                if (days == 1) "1 day" else "$days days"
            }
            return if (needsRenewal) "$span left — tap to renew" else "$span left"
        }
}
