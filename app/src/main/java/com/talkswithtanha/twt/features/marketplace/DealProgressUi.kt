package com.talkswithtanha.twt.features.marketplace

import android.media.AudioManager
import android.media.ToneGenerator
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.designsystem.WebFonts
import com.talkswithtanha.twt.core.model.DealProgress
import com.talkswithtanha.twt.core.model.PaymentAccount
import com.talkswithtanha.twt.core.model.Turn
import com.talkswithtanha.twt.core.model.clockText
import kotlinx.coroutines.delay
import java.util.Date

private val Card = Color(0xFF1C1C1E)
private val Field = Color(0xFF141416)

/** The current second, ticking while the screen shows it. */
@Composable
fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000 - System.currentTimeMillis() % 1_000)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/** Deal opened · Payment · Release · Completed, with a ✓ on each one done. */
@Composable
fun DealStepper(progress: DealProgress, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card).padding(vertical = 14.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        DealProgress.STEPS.forEachIndexed { i, label ->
            val done = i < progress.step || progress.step == 4
            val current = !done && i == progress.step && !progress.appeal
            StepCell(label, i + 1, done, current, appeal = false, Modifier.weight(1f))
        }
        if (progress.appeal) StepCell("Under appeal", 0, done = false, current = true, appeal = true, Modifier.weight(1f))
    }
}

@Composable
private fun StepCell(label: String, number: Int, done: Boolean, current: Boolean, appeal: Boolean, modifier: Modifier) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val glow by pulse.animateFloat(1f, 1.18f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "glow")
    val tick by animateFloatAsState(if (done) 1f else 0f, tween(350), label = "tick")
    val tint = when {
        appeal -> IosColors.Warning
        done -> IosColors.BuyGreen
        current -> Color.White
        else -> Color.White.copy(alpha = 0.14f)
    }
    Column(
        modifier.semantics(mergeDescendants = true) {
            contentDescription = "$label, " + when { done -> "done"; current -> "now"; else -> "to come" }
        },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(22.dp).scale(if (current) glow else 1f).clip(CircleShape).background(tint),
            contentAlignment = Alignment.Center
        ) {
            when {
                done -> Icon(Icons.Filled.Check, null, tint = Color.Black, modifier = Modifier.size(13.dp).scale(0.6f + 0.4f * tick))
                appeal -> Icon(Icons.Outlined.PriorityHigh, null, tint = Color.Black, modifier = Modifier.size(13.dp))
                else -> Text("$number", color = if (current) Color.Black else Color.White.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            label,
            color = when { appeal -> IosColors.Warning; current -> Color.White; done -> Color.White.copy(alpha = 0.65f); else -> Color.White.copy(alpha = 0.3f) },
            fontSize = 10.sp,
            fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

/**
 * YOUR TURN / THEIR TURN / UNDER REVIEW, what to do, what happens next, and
 * the clock when the turn has one. Announced to TalkBack when it changes.
 */
@Composable
fun TurnCard(progress: DealProgress, sound: Boolean, modifier: Modifier = Modifier) {
    val accent = when (progress.turn) {
        Turn.YOU -> IosColors.BuyGreen
        Turn.REVIEW -> IosColors.Warning
        else -> Color.White.copy(alpha = 0.55f)
    }
    val badge = when (progress.turn) {
        Turn.YOU -> "YOUR TURN"
        Turn.DESK -> "THEIR TURN"
        Turn.REVIEW -> "UNDER REVIEW"
        Turn.DONE -> "COMPLETED"
        Turn.CLOSED -> "CLOSED"
    }
    val icon = when (progress.turn) {
        Turn.YOU -> Icons.Outlined.PriorityHigh
        Turn.DESK -> Icons.Outlined.Schedule
        Turn.REVIEW -> Icons.Outlined.Shield
        Turn.DONE -> Icons.Filled.Check
        Turn.CLOSED -> Icons.Outlined.Close
    }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (progress.turn == Turn.YOU || progress.turn == Turn.REVIEW) accent.copy(alpha = 0.09f) else Card)
            .border(1.dp, if (progress.turn == Turn.DESK || progress.turn == Turn.CLOSED) Color.White.copy(alpha = 0.06f) else accent.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
            .padding(16.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(if (progress.turn == Turn.DESK || progress.turn == Turn.CLOSED) Color.White.copy(alpha = 0.1f) else accent).padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            val ink = if (progress.turn == Turn.DESK || progress.turn == Turn.CLOSED) Color.White.copy(alpha = 0.8f) else Color.Black
            Icon(icon, null, tint = ink, modifier = Modifier.size(12.dp))
            Text(badge, color = ink, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp)
        }
        Text(progress.headline, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, lineHeight = 24.sp)
        Text(progress.next, color = Color.White.copy(alpha = 0.55f), fontSize = 13.5.sp, lineHeight = 19.sp)
        progress.deadline?.let { Countdown(it, progress.clockText, alarm = progress.turn == Turn.YOU, sound = sound) }
    }
}

/**
 * A ring that empties as time runs out: green, amber under five minutes, red
 * under two. On the member's own clock the phone buzzes once and a short low
 * tone plays on crossing two minutes, unless they turned sounds off.
 */
@Composable
private fun Countdown(deadline: Date, words: (String) -> String, alarm: Boolean, sound: Boolean) {
    val now = rememberNow()
    val view = LocalView.current
    val left = deadline.time - now
    val urgent = alarm && left in 1..120_000
    var warnedFor by rememberSaveable { mutableLongStateOf(0L) }

    LaunchedEffect(urgent, deadline.time) {
        if (!urgent || !sound || warnedFor == deadline.time) return@LaunchedEffect
        warnedFor = deadline.time
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 35)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 180)
            delay(400)
            tone.release()
        }
    }

    val span = if (alarm) 15 * 60_000f else 30 * 60_000f
    val share = (left / span).coerceIn(0f, 1f)
    val color = when {
        left <= 120_000 -> IosColors.SellRed
        left <= 300_000 -> IosColors.Warning
        else -> IosColors.BuyGreen
    }
    val text = when {
        left > 0 -> words(clockText(left))
        alarm -> "Time is up. The deal is closing."
        else -> "Taking longer than usual. Message the desk if you need to."
    }

    Row(
        Modifier.padding(top = 6.dp).semantics(mergeDescendants = true) { contentDescription = text },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Canvas(Modifier.size(52.dp)) {
            val stroke = 5.dp.toPx()
            val inset = stroke / 2
            val arc = Size(size.width - stroke, size.height - stroke)
            drawArc(Color.White.copy(alpha = 0.1f), 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            drawArc(color, -90f, 360f * share, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column {
            Text(clockText(left), color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = WebFonts.Mono)
            Text(text, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

/** Funds sent: short and calm, one way onward. */
@Composable
fun FundsSentCard(amount: String, to: String, onFeedback: () -> Unit, modifier: Modifier = Modifier) {
    val pop by animateFloatAsState(1f, tween(450), label = "pop")
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Card).padding(vertical = 30.dp, horizontal = 18.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(72.dp).scale(pop).clip(CircleShape).background(IosColors.BuyGreen), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, null, tint = Color.Black, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text("$amount sent", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("To your $to. This deal is complete.", color = Color.White.copy(alpha = 0.55f), fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "Tell the desk how it went",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(50))
                .clickable(onClick = onFeedback).padding(horizontal = 18.dp, vertical = 10.dp)
        )
    }
}

/** The line is down: say so, and the money buttons wait. */
@Composable
fun ReconnectingBar(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(IosColors.Warning.copy(alpha = 0.12f)).padding(12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CircularProgressIndicator(color = IosColors.Warning, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
        Text("Reconnecting… buttons wait until the connection is back.", color = IosColors.Warning, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

private val RECEIVE_METHODS = listOf("JazzCash", "Easypaisa", "Bank Transfer", "USDT (TRC20)")

/**
 * Where the desk should send the member's side. A full-screen sheet, like the
 * payment report: a wrong number here cannot be pulled back.
 */
@Composable
fun ReceiveAccountSheet(
    isSubmitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (PaymentAccount) -> Unit
) {
    var method by remember { mutableStateOf(RECEIVE_METHODS.first()) }
    var title by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    val usdt = method.startsWith("USDT")

    Dialog(onDismissRequest = { if (!isSubmitting) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(IosColors.Background).statusBarsPadding().imePadding().padding(horizontal = 20.dp)
        ) {
            Box(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
                Text("Cancel", color = IosColors.Accent, fontSize = 16.sp, modifier = Modifier.align(Alignment.CenterStart).clickable(enabled = !isSubmitting, onClick = onDismiss))
                Text("Where to send it", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Center))
            }
            Spacer(Modifier.height(16.dp))
            Label("METHOD")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                RECEIVE_METHODS.forEach { m ->
                    val on = m == method
                    Text(
                        m.substringBefore(" ("),
                        color = if (on) Color.Black else Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (on) IosColors.BuyGreen else Field)
                            .clickable { method = m }.padding(vertical = 11.dp).semantics { contentDescription = m },
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Label("ACCOUNT TITLE")
            Spacer(Modifier.height(8.dp))
            Input(title, "The name on the account") { title = it.take(60) }
            Spacer(Modifier.height(16.dp))
            Label(if (usdt) "WALLET ADDRESS" else "ACCOUNT NUMBER")
            Spacer(Modifier.height(8.dp))
            Input(number, if (usdt) "TRC20 address" else "03xx xxxxxxx / IBAN", mono = true) { number = it.trim().take(64) }
            Spacer(Modifier.height(8.dp))
            Text("Check this twice. Funds sent to a wrong number cannot be pulled back.", color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = IosColors.SellRed, fontSize = 13.sp)
            }
            Spacer(Modifier.weight(1f))
            ConfirmButton(
                label = "Send it here",
                enabled = title.isNotBlank() && number.isNotBlank() && !isSubmitting,
                loading = isSubmitting,
                onClick = { onSave(PaymentAccount(method, title.trim(), number.trim())) }
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.1.sp)
}

@Composable
private fun Input(value: String, placeholder: String, mono: Boolean = false, onChange: (String) -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Field).padding(horizontal = 14.dp, vertical = 16.dp)) {
        if (value.isEmpty()) Text(placeholder, color = Color.White.copy(alpha = 0.25f), fontSize = 16.sp, fontFamily = if (mono) WebFonts.Mono else null)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontFamily = if (mono) WebFonts.Mono else null),
            cursorBrush = SolidColor(IosColors.Accent),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
