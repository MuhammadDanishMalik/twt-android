package com.talkswithtanha.twt.features.auth

import android.graphics.BlurMaskFilter
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talkswithtanha.twt.core.designsystem.WebColors
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// ─── GlowOtpField ──────────────────────────────────────────────────────────
//
// The verification code, as a sequence rather than a form: boxes that pop as
// digits land, a glowing line that traces every border while the server
// checks, and — when it says yes — the boxes fill with light, slide together
// into one, and a tick draws itself inside. A wrong code shakes, turns red and
// clears itself from the right.
//
// Everything that moves every frame is drawn in one Canvas and read from
// Animatables inside the draw lambda, so the sequence redraws without
// recomposing. Composition only changes when a digit lands or the state does.

/** What the server has said about the code. The view model owns it. */
@Immutable
sealed interface OtpUiState {
    data object Idle : OtpUiState
    data object Verifying : OtpUiState
    data object Success : OtpUiState
    /** [attempt] makes a second identical message a new event, so it shakes again. */
    data class Error(val message: String, val attempt: Int = 0) : OtpUiState
}

private val SheetColor = Color(0xFF1A1A1A)
private val BoxFill = Color(0xFF222222)
private val BoxBorder = Color(0xFF333333)
private val Grey = Color(0xFF8A8A8A)
private val ErrorRed = Color(0xFFFF3B30)

/**
 * The dark sheet the field sits in: rounded top, a grab handle, and a spring
 * up from the bottom when it first appears.
 */
@Composable
fun OtpSheet(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val rise = remember { Animatable(1f) }
    val inspection = LocalInspectionMode.current
    LaunchedEffect(Unit) {
        if (inspection) rise.snapTo(0f) else rise.animateTo(0f, spring(dampingRatio = 0.82f, stiffness = 260f))
    }
    Column(
        modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = rise.value * size.height }
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(SheetColor)
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 10.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.18f)))
        Spacer(Modifier.height(22.dp))
        content()
    }
}

/**
 * The code entry and its whole animated life.
 *
 * @param code the digits so far, held by the view model so they survive
 *   rotation and process death.
 * @param resendSeconds seconds until Resend is allowed; the view model counts
 *   it down, 0 means it is live now.
 * @param onSuccessFinished called once, 700ms after the success sequence ends.
 */
@Composable
fun GlowOtpField(
    state: OtpUiState,
    code: String,
    onCodeComplete: (String) -> Unit,
    onResend: () -> Unit,
    modifier: Modifier = Modifier,
    length: Int = 4,
    accent: Color = WebColors.Brand,
    onCodeChange: (String) -> Unit = {},
    resendSeconds: Int = 30,
    title: String = "Let's verify your email",
    subtitle: String = "We've sent a $length digit code to your email. It'll auto verify once entered.",
    successTitle: String = "Verified successfully",
    onSuccessFinished: () -> Unit = {},
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val keyboard = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboardManager.current
    val windowInfo = LocalWindowInfo.current
    val inspection = LocalInspectionMode.current
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val measurer = rememberTextMeasurer()

    // System animations off means motion off here too: no trace, no merge,
    // no sparks, just a crossfade to the tick.
    val reducedMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }

    val latestCode by rememberUpdatedState(code)
    val latestState by rememberUpdatedState(state)
    val latestOnChange by rememberUpdatedState(onCodeChange)
    val latestOnFinished by rememberUpdatedState(onSuccessFinished)

    val inputEnabled = state is OtpUiState.Idle || (state is OtpUiState.Error && code.isEmpty())

    // ── Per-box digit pops ──────────────────────────────────────────────
    val shown = remember(length) { mutableStateListOf<Char?>().apply { repeat(length) { add(null) } } }
    val popScale = remember(length) { List(length) { Animatable(1f) } }
    val popAlpha = remember(length) { List(length) { Animatable(0f) } }
    var previous by remember { mutableStateOf("") }

    LaunchedEffect(code) {
        val before = previous
        previous = code
        val added = code.length - before.length
        // A typed digit ticks; a pasted code pops in a wave, 40ms per box.
        if (added == 1) haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
        for (i in 0 until length) {
            val now = code.getOrNull(i)
            val was = before.getOrNull(i)
            // On the composable's own scope, not this effect's: the code can
            // change again before an animation ends (clearing runs 40ms
            // apart), and a cancelled fade would leave a half-gone digit.
            if (now != null && now != was) {
                val wait = if (added > 1) (i - before.length).coerceAtLeast(0) * 40L else 0L
                scope.launch {
                    delay(wait)
                    shown[i] = now
                    popScale[i].snapTo(0.6f)
                    popAlpha[i].snapTo(0f)
                    launch { popAlpha[i].animateTo(1f, tween(140)) }
                    popScale[i].animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 600f))
                }
            } else if (now == null && was != null) {
                scope.launch {
                    launch { popScale[i].animateTo(0.8f, tween(120)) }
                    popAlpha[i].animateTo(0f, tween(120))
                    if (latestCode.getOrNull(i) == null) shown[i] = null
                }
            }
        }
        if (code.length == length && before.length < length && latestState is OtpUiState.Idle) {
            keyboard?.hide()
            onCodeComplete(code)
        }
    }

    // Focus on arrival, and back on the first box after a wrong code.
    LaunchedEffect(inputEnabled) {
        if (inputEnabled && !inspection) {
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
        }
    }

    // A code copied in the mail app fills itself in when the member comes
    // back. Each copied code is tried once.
    var lastPasted by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        snapshotFlow { windowInfo.isWindowFocused }.collect { focused ->
            if (!focused || latestCode.isNotEmpty() || latestState !is OtpUiState.Idle) return@collect
            val copied = runCatching { clipboard.getText()?.text }.getOrNull() ?: return@collect
            val found = Regex("(?<!\\d)\\d{$length}(?!\\d)").find(copied)?.value ?: return@collect
            if (found == lastPasted) return@collect
            lastPasted = found
            latestOnChange(found)
        }
    }

    // ── The sequence's clocks ───────────────────────────────────────────
    val trace = remember { Animatable(0f) }        // first full trace, 0 → 1
    val comet = remember { Animatable(0f) }        // looping comet position
    var cometOn by remember { mutableStateOf(false) }
    val breathe = remember { Animatable(1f) }      // glow alpha while waiting
    val full = remember { Animatable(0f) }         // complete glowing border
    val fill = remember { Animatable(0f) }         // box fill → accent
    val bgGlow = remember { Animatable(0f) }       // radial light from below
    val headerOut = remember { Animatable(0f) }    // title, subtitle, resend leaving
    val digitsOut = remember { Animatable(0f) }
    val merge = remember { Animatable(0f) }
    val check = remember { Animatable(0f) }
    val boxScale = remember { Animatable(1f) }
    val sparks = remember { Animatable(0f) }
    val successIn = remember { Animatable(0f) }
    val errorMix = remember { Animatable(0f) }
    val shake = remember { Animatable(0f) }
    var finishedSuccess by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        when (state) {
            OtpUiState.Idle -> {
                cometOn = false
                breathe.snapTo(1f)
                if (errorMix.value > 0f) errorMix.animateTo(0f, tween(200))
                trace.snapTo(0f)
            }
            OtpUiState.Verifying -> {
                delay(80)
                if (reducedMotion) {
                    trace.snapTo(1f)
                    return@LaunchedEffect
                }
                trace.snapTo(0f)
                trace.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
                // Still waiting: a short bright comet keeps circling, and the
                // glow breathes, until the server answers.
                cometOn = true
                launch {
                    breathe.animateTo(0.6f, infiniteRepeatable(tween(700), RepeatMode.Reverse))
                }
                while (true) {
                    comet.snapTo(0f)
                    comet.animateTo(1f, tween(900, easing = LinearEasing))
                }
            }
            OtpUiState.Success -> {
                if (finishedSuccess) return@LaunchedEffect
                cometOn = false
                breathe.snapTo(1f)
                if (inspection) {
                    // Previews show the end frame.
                    listOf(full, fill, headerOut, digitsOut, merge, check, successIn).forEach { it.snapTo(1f) }
                    bgGlow.snapTo(0.18f)
                    return@LaunchedEffect
                }
                if (reducedMotion) {
                    coroutineScope {
                        launch { headerOut.animateTo(1f, tween(200)) }
                        launch { digitsOut.animateTo(1f, tween(200)) }
                        merge.snapTo(1f)
                        full.snapTo(1f)
                        fill.animateTo(1f, tween(250))
                        check.snapTo(1f)
                        successIn.animateTo(1f, tween(250))
                    }
                    bgGlow.snapTo(0.18f)
                } else {
                    coroutineScope {
                        launch { full.animateTo(1f, tween(200)) }
                        launch { fill.animateTo(1f, tween(200)) }
                        launch { bgGlow.animateTo(0.3f, tween(200)) }
                        launch { delay(100); headerOut.animateTo(1f, tween(200)) }
                        launch { delay(200); digitsOut.animateTo(1f, tween(120)) }
                        launch { delay(200); merge.animateTo(1f, tween(250, easing = FastOutSlowInEasing)) }
                        launch { delay(450); check.animateTo(1f, tween(200, easing = FastOutSlowInEasing)) }
                        launch {
                            delay(450)
                            boxScale.animateTo(1.18f, tween(200, easing = FastOutSlowInEasing))
                            boxScale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 380f))
                        }
                        launch { delay(650); sparks.animateTo(1f, tween(300)) }
                        launch { delay(450); successIn.animateTo(1f, tween(300)) }
                    }
                    bgGlow.animateTo(0.18f, tween(300))
                }
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                finishedSuccess = true
                delay(700)
                latestOnFinished()
            }
            is OtpUiState.Error -> {
                cometOn = false
                breathe.snapTo(1f)
                trace.snapTo(0f)
                haptics.performHapticFeedback(HapticFeedbackType.Reject)
                coroutineScope {
                    launch { errorMix.animateTo(1f, tween(200)) }
                    launch {
                        for (x in listOf(-10f, 10f, -6f, 6f, -2f, 0f)) shake.animateTo(x, tween(400 / 6))
                    }
                }
                delay(600)
                // Cleared from the right, a beat apart; each removal scales
                // its digit out through the same path as a backspace.
                var left = latestCode
                while (left.isNotEmpty()) {
                    left = left.dropLast(1)
                    latestOnChange(left)
                    delay(40)
                }
                errorMix.animateTo(0f, tween(200))
            }
        }
    }

    // ── Layout ──────────────────────────────────────────────────────────
    val announcement = when (state) {
        OtpUiState.Verifying -> "Verifying"
        OtpUiState.Success -> successTitle
        is OtpUiState.Error -> state.message
        OtpUiState.Idle -> ""
    }

    Box(
        modifier
            .fillMaxWidth()
            .drawBehind {
                // Light rising from the bottom of the sheet, behind the boxes.
                val a = bgGlow.value
                if (a > 0f) {
                    drawRect(
                        Brush.radialGradient(
                            listOf(accent.copy(alpha = a), Color.Transparent),
                            center = Offset(size.width / 2f, size.height * 1.05f),
                            radius = size.width * 0.75f
                        )
                    )
                }
            }
            .semantics { liveRegion = LiveRegionMode.Polite; contentDescription = announcement }
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Column(
                    Modifier.fadeBlurOut(headerOut, density.run { 8.dp.toPx() }),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    HeaderIn {
                        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.height(8.dp))
                    HeaderIn(delayMillis = 60) {
                        Text(
                            subtitle,
                            color = Grey,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }
                // The new title, in the old one's place.
                Text(
                    successTitle,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .graphicsLayer {
                            alpha = successIn.value
                            val r = (1f - successIn.value) * 8.dp.toPx()
                            if (Build.VERSION.SDK_INT >= 31 && r > 0.5f) renderEffect = BlurEffect(r, r, TileMode.Decal)
                        }
                )
            }

            Spacer(Modifier.height(28.dp))

            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                // 56dp boxes and 12dp gaps where they fit; six boxes on a
                // narrow phone shrink to fit rather than run off the edge.
                val gap = if (length > 4) 8.dp else 12.dp
                val box = min(56f, (maxWidth.value - gap.value * (length - 1)) / length).dp
                val rowWidth = box * length + gap * (length - 1)

                // The real input, invisible, under the drawing. One field for
                // the whole row, so paste, backspace and autofill just work.
                BasicTextField(
                    value = code,
                    onValueChange = { next -> onCodeChange(next.filter(Char::isDigit).take(length)) },
                    enabled = inputEnabled,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (code.length == length) keyboard?.hide() }),
                    cursorBrush = SolidColor(Color.Transparent),
                    textStyle = TextStyle(color = Color.Transparent),
                    modifier = Modifier
                        .width(rowWidth)
                        .height(box)
                        .focusRequester(focusRequester)
                        .semantics {
                            contentDescription = "Verification code, $length digits"
                            // Keyboards offer a code they saw in a notification.
                            contentType = ContentType.SmsOtpCode
                        }
                )

                val focusIndex = if (inputEnabled) code.length.coerceAtMost(length - 1) else -1
                val focusMix = List(length) { i ->
                    animateFloatAsState(if (i == focusIndex) 1f else 0f, tween(150), label = "focus$i")
                }
                val digitStyle = TextStyle(color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                val glyphs = remember(measurer) { ('0'..'9').associateWith { measurer.measure(it.toString(), digitStyle) } }

                Canvas(
                    Modifier
                        .width(rowWidth + 48.dp)
                        .height(box + 48.dp)
                        .graphicsLayer { translationX = shake.value.dp.toPx() }
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = inputEnabled) {
                            runCatching { focusRequester.requestFocus() }
                            keyboard?.show()
                        }
                ) {
                    val boxPx = box.toPx()
                    val gapPx = gap.toPx()
                    val radius = 14.dp.toPx()
                    val startX = (size.width - (boxPx * length + gapPx * (length - 1))) / 2f
                    val top = (size.height - boxPx) / 2f
                    val center = size.width / 2f
                    val traceColor = lerp(accent, ErrorRed, errorMix.value)
                    val m = merge.value
                    val outline = roundedOutline(boxPx, radius)
                    val measure = PathMeasure().apply { setPath(outline, false) }

                    fun boxLeft(i: Int, mergeAt: Float): Float {
                        val x = startX + i * (boxPx + gapPx)
                        val cx = x + boxPx / 2f
                        return x + (center - cx) * mergeAt
                    }

                    fun tilt(i: Int, mergeAt: Float): Float {
                        if (length < 2) return 0f
                        val mid = (length - 1) / 2f
                        return ((i - mid) / mid) * 6f * sin(mergeAt * PI.toFloat())
                    }

                    // Faint ghosts trailing the boxes as they slide together.
                    if (m > 0f && m < 1f) {
                        for ((lag, a) in listOf(0.08f to 0.30f, 0.16f to 0.15f)) {
                            val at = (m - lag).coerceAtLeast(0f)
                            for (i in 0 until length) {
                                translate(boxLeft(i, at), top) {
                                    rotate(tilt(i, at), Offset(boxPx / 2f, boxPx / 2f)) {
                                        drawRoundRect(accent.copy(alpha = a * 0.6f), size = Size(boxPx, boxPx), cornerRadius = CornerRadius(radius), style = Stroke(1.5.dp.toPx()))
                                    }
                                }
                            }
                        }
                    }

                    // Outer boxes first, the centre one last, so the stack
                    // reads as one box once they overlap.
                    val order = (0 until length).sortedByDescending { kotlin.math.abs(it - (length - 1) / 2f) }
                    for (i in order) {
                        translate(boxLeft(i, m), top) {
                            rotate(tilt(i, m), Offset(boxPx / 2f, boxPx / 2f)) {
                                scale(boxScale.value, Offset(boxPx / 2f, boxPx / 2f)) {
                                    drawBox(
                                        boxPx = boxPx,
                                        radius = radius,
                                        fillMix = fill.value,
                                        accent = accent,
                                        focus = focusMix[i].value,
                                        errorMix = errorMix.value,
                                        outline = outline,
                                        measure = measure,
                                        trace = trace.value,
                                        comet = if (cometOn) comet.value else -1f,
                                        full = full.value,
                                        glowAlpha = breathe.value * (1f + 0.4f * check.value),
                                        traceColor = traceColor,
                                    )
                                    // The digit, popping in or out.
                                    val ch = shown[i]
                                    val layout = ch?.let { glyphs[it] }
                                    if (layout != null) {
                                        val s = popScale[i].value
                                        val a = popAlpha[i].value * (1f - digitsOut.value)
                                        if (a > 0f) {
                                            scale(s, Offset(boxPx / 2f, boxPx / 2f)) {
                                                drawText(
                                                    layout,
                                                    color = Color.White.copy(alpha = a),
                                                    topLeft = Offset((boxPx - layout.size.width) / 2f, (boxPx - layout.size.height) / 2f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // The tick, drawing itself in the one box that is left.
                    if (check.value > 0f) {
                        translate(center - boxPx / 2f, top) {
                            scale(boxScale.value, Offset(boxPx / 2f, boxPx / 2f)) {
                                val tick = Path().apply {
                                    moveTo(boxPx * 0.30f, boxPx * 0.52f)
                                    lineTo(boxPx * 0.44f, boxPx * 0.66f)
                                    lineTo(boxPx * 0.72f, boxPx * 0.36f)
                                }
                                val tm = PathMeasure().apply { setPath(tick, false) }
                                val part = Path()
                                tm.getSegment(0f, tm.length * check.value, part, true)
                                drawPath(part, Color.White, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                            }
                        }
                    }

                    // A few sparks drifting off the box as it settles.
                    val sp = sparks.value
                    if (sp > 0f && sp < 1f) {
                        val c = Offset(center, top + boxPx / 2f)
                        for (k in 0 until 8) {
                            val angle = (k * 45f + 20f) * (PI.toFloat() / 180f)
                            val dist = boxPx * 0.55f + sp * 26.dp.toPx() * (0.7f + (k % 3) * 0.15f)
                            drawCircle(
                                accent.copy(alpha = (1f - sp) * 0.9f),
                                radius = 1.dp.toPx(),
                                center = c + Offset(cos(angle) * dist, sin(angle) * dist)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            AnimatedVisibility(visible = state is OtpUiState.Error, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    (state as? OtpUiState.Error)?.message ?: "",
                    color = ErrorRed,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(Modifier.height(16.dp))

            // Resend, or how long until it is allowed.
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val press by animateFloatAsState(if (pressed) 0.95f else 1f, label = "press")
            val canResend = resendSeconds <= 0 && state !is OtpUiState.Verifying && state !is OtpUiState.Success
            Row(
                Modifier.fadeBlurOut(headerOut, density.run { 8.dp.toPx() }),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Didn't receive the code? ", color = Grey, fontSize = 13.sp)
                Text(
                    if (resendSeconds > 0) "Resend in ${resendSeconds / 60}:${(resendSeconds % 60).toString().padStart(2, '0')}" else "Resend",
                    color = if (canResend) Color.White else Grey,
                    fontSize = 13.sp,
                    fontWeight = if (canResend) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier
                        .graphicsLayer { scaleX = press; scaleY = press }
                        .clickable(interactionSource = interaction, indication = null, enabled = canResend) {
                            onCodeChange("")
                            onResend()
                            Toast.makeText(context, "New code sent", Toast.LENGTH_SHORT).show()
                        }
                )
            }
        }
    }
}

/** Title and subtitle arriving: a fade from a 6dp blur (a plain fade below API 31). */
@Composable
private fun HeaderIn(delayMillis: Int = 0, content: @Composable () -> Unit) {
    val inspection = LocalInspectionMode.current
    val t = remember { Animatable(if (inspection) 1f else 0f) }
    LaunchedEffect(Unit) {
        delay(delayMillis.toLong() + 120)
        t.animateTo(1f, tween(380))
    }
    Box(
        Modifier.graphicsLayer {
            alpha = t.value
            val r = (1f - t.value) * 6.dp.toPx()
            if (Build.VERSION.SDK_INT >= 31 && r > 0.5f) renderEffect = BlurEffect(r, r, TileMode.Decal)
        }
    ) { content() }
}

/** Leaving: fade, blur up to [maxBlur] px, and lift 8dp, read in the layer so it never recomposes. */
private fun Modifier.fadeBlurOut(t: Animatable<Float, *>, maxBlur: Float): Modifier = graphicsLayer {
    val v = t.value
    alpha = 1f - v
    translationY = -8.dp.toPx() * v
    val r = v * maxBlur
    renderEffect = if (Build.VERSION.SDK_INT >= 31 && r > 0.5f) BlurEffect(r, r, TileMode.Decal) else null
}

/**
 * A rounded square's border as a path that starts at the top centre and runs
 * clockwise, so a trace drawn along it begins where the reference does.
 */
private fun roundedOutline(side: Float, r: Float): Path = Path().apply {
    val d = r * 2
    moveTo(side / 2f, 0f)
    lineTo(side - r, 0f)
    arcTo(Rect(side - d, 0f, side, d), -90f, 90f, false)
    lineTo(side, side - r)
    arcTo(Rect(side - d, side - d, side, side), 0f, 90f, false)
    lineTo(r, side)
    arcTo(Rect(0f, side - d, d, side), 90f, 90f, false)
    lineTo(0f, r)
    arcTo(Rect(0f, 0f, d, d), 180f, 90f, false)
    close()
}

/** One box: fill, resting border, focus ring, and whichever glowing line is running. */
private fun DrawScope.drawBox(
    boxPx: Float,
    radius: Float,
    fillMix: Float,
    accent: Color,
    focus: Float,
    errorMix: Float,
    outline: Path,
    measure: PathMeasure,
    trace: Float,
    comet: Float,
    full: Float,
    glowAlpha: Float,
    traceColor: Color,
) {
    val corner = CornerRadius(radius)
    val boxSize = Size(boxPx, boxPx)
    val fillColor = lerp(lerp(BoxFill, accent.copy(alpha = 0.25f).compositeOver(BoxFill), fillMix), ErrorRed.copy(alpha = 0.12f).compositeOver(BoxFill), errorMix * 0.6f)
    drawRoundRect(fillColor, size = boxSize, cornerRadius = corner)

    // Inner glow once the box has filled with light.
    if (fillMix > 0f) {
        drawRoundRect(
            Brush.radialGradient(listOf(accent.copy(alpha = 0.35f * fillMix), Color.Transparent), center = Offset(boxPx / 2f, boxPx / 2f), radius = boxPx * 0.7f),
            size = boxSize,
            cornerRadius = corner
        )
    }

    // Resting border, warming to the accent on the box that is next.
    val border = lerp(lerp(BoxBorder, accent, focus), ErrorRed, errorMix)
    drawRoundRect(border, size = boxSize, cornerRadius = corner, style = Stroke((1f + 0.5f * focus).dp.toPx()))

    val length = measure.length
    val segment = Path()
    when {
        full > 0f -> {
            // Success: the whole border glows.
            glowStroke(outline, traceColor, glowAlpha * full)
        }
        comet >= 0f -> {
            // Waiting: a 25% comet looping round, wrapping past the start.
            val from = comet * length
            val to = from + length * 0.25f
            measure.getSegment(from, min(to, length), segment, true)
            if (to > length) measure.getSegment(0f, to - length, segment, true)
            glowStroke(segment, traceColor, glowAlpha)
        }
        trace > 0f -> {
            // The first pass: a line growing from the top centre to all the way round.
            measure.getSegment(0f, length * trace, segment, true)
            glowStroke(segment, traceColor, glowAlpha)
        }
        errorMix > 0f -> glowStroke(outline, ErrorRed, errorMix * 0.7f)
    }
}

/**
 * A 2dp line with a soft glow behind it. Blurred with a mask filter where the
 * hardware draws one (API 28+); below that, two wider faint strokes stand in.
 */
private fun DrawScope.glowStroke(path: Path, color: Color, alpha: Float) {
    if (alpha <= 0f) return
    if (Build.VERSION.SDK_INT >= 28) {
        drawIntoCanvas { canvas ->
            val paint = Paint().asFrameworkPaint().apply {
                isAntiAlias = true
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 8.dp.toPx()
                strokeCap = android.graphics.Paint.Cap.ROUND
                this.color = color.copy(alpha = 0.45f * alpha).toArgb()
                maskFilter = BlurMaskFilter(6.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
            }
            canvas.nativeCanvas.drawPath(path.asAndroidPath(), paint)
        }
    } else {
        drawPath(path, color.copy(alpha = 0.16f * alpha), style = Stroke(9.dp.toPx(), cap = StrokeCap.Round))
        drawPath(path, color.copy(alpha = 0.28f * alpha), style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
    }
    drawPath(path, color.copy(alpha = min(1f, 0.6f + 0.4f * alpha)), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
}

private fun Color.compositeOver(background: Color): Color {
    val a = alpha + background.alpha * (1f - alpha)
    if (a == 0f) return Color.Transparent
    return Color(
        red = (red * alpha + background.red * background.alpha * (1f - alpha)) / a,
        green = (green * alpha + background.green * background.alpha * (1f - alpha)) / a,
        blue = (blue * alpha + background.blue * background.alpha * (1f - alpha)) / a,
        alpha = a
    )
}

// ─── Previews ──────────────────────────────────────────────────────────────

@Composable
private fun PreviewFrame(state: OtpUiState, code: String, length: Int = 4) {
    Box(Modifier.background(Color.Black).padding(top = 40.dp)) {
        OtpSheet {
            GlowOtpField(state = state, code = code, length = length, onCodeComplete = {}, onResend = {}, resendSeconds = 24)
        }
    }
}

@Preview(name = "Idle", widthDp = 380)
@Composable
private fun PreviewIdle() = PreviewFrame(OtpUiState.Idle, "")

@Preview(name = "Typing, 2 digits", widthDp = 380)
@Composable
private fun PreviewTyping() = PreviewFrame(OtpUiState.Idle, "48")

@Preview(name = "Verifying", widthDp = 380)
@Composable
private fun PreviewVerifying() = PreviewFrame(OtpUiState.Verifying, "4821")

@Preview(name = "Success, end frame", widthDp = 380)
@Composable
private fun PreviewSuccess() = PreviewFrame(OtpUiState.Success, "4821")

@Preview(name = "Error", widthDp = 380)
@Composable
private fun PreviewError() = PreviewFrame(OtpUiState.Error("That code didn't match. Try again."), "4829")

@Preview(name = "Six digits", widthDp = 360)
@Composable
private fun PreviewSix() = PreviewFrame(OtpUiState.Idle, "4821", length = 6)
