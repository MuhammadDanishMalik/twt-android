package com.talkswithtanha.twt.features.setup

import com.talkswithtanha.twt.core.designsystem.readableWidth
import androidx.compose.ui.graphics.lerp
import androidx.compose.runtime.mutableIntStateOf
import com.talkswithtanha.twt.core.designsystem.ConfettiBurst
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.talkswithtanha.twt.core.designsystem.pressFlood
import com.talkswithtanha.twt.core.designsystem.WebSticker
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.talkswithtanha.twt.core.designsystem.Haptics
import com.talkswithtanha.twt.core.designsystem.WebBackdrop
import com.talkswithtanha.twt.core.designsystem.WebButton
import com.talkswithtanha.twt.core.designsystem.WebColors
import com.talkswithtanha.twt.core.designsystem.WebFonts
import com.talkswithtanha.twt.core.designsystem.WebHeadline
import com.talkswithtanha.twt.core.designsystem.RiseIn
import com.talkswithtanha.twt.core.designsystem.WebType
import com.talkswithtanha.twt.core.designsystem.pressScale
import com.talkswithtanha.twt.core.model.BrokerStatus
import com.talkswithtanha.twt.core.model.Brokers
import com.talkswithtanha.twt.core.model.Countries
import com.talkswithtanha.twt.core.model.Experience
import com.talkswithtanha.twt.core.model.IbStatus
import com.talkswithtanha.twt.core.model.ProfileOption
import com.talkswithtanha.twt.core.model.TradingDuration
import com.talkswithtanha.twt.features.profile.AvatarEditor
import com.talkswithtanha.twt.core.designsystem.components.TwtTextField
import java.util.Locale

/**
 * A few questions between confirming the email and entering the access code:
 * who you are, how long you have traded, and where.
 *
 * One question per screen, with a progress bar, because five short screens
 * feel quicker than one long form. There is no navigation out of here —
 * finishing sets `profileSetupAt` and the gate moves the stack on.
 */
@Composable
fun ProfileSetupScreen(viewModel: ProfileSetupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    BackHandler(enabled = state.stepIndex > 0) { viewModel.back() }

    // Finishing is worth a moment: confetti while the profile saves.
    var burst by remember { mutableIntStateOf(0) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(state.isSaving) {
        if (state.isSaving) {
            burst++
            Haptics.success(haptics)
        }
    }
    // A detent each time a new question slides in, forwards or back.
    var firstStep by remember { mutableStateOf(true) }
    LaunchedEffect(state.step) {
        if (firstStep) firstStep = false else Haptics.tick(haptics)
    }
    LaunchedEffect(state.error) { if (state.error != null) Haptics.failure(haptics) }

    WebBackdrop {
        Column(
            Modifier
                .fillMaxSize()
                .readableWidth()
                .safeDrawingPadding()
                .imePadding()
        ) {
            SetupTopBar(
                stepIndex = state.stepIndex,
                stepCount = state.steps.size,
                onBack = { viewModel.back() },
                onSignOut = viewModel::signOut
            )

            AnimatedContent(
                targetState = state.step,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    val dir = if (forward) 1 else -1
                    (slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 260f)) { it * dir / 3 } +
                        fadeIn())
                        .togetherWith(slideOutHorizontally { -it * dir / 3 } + fadeOut())
                        .using(SizeTransform(clip = false))
                },
                label = "step",
                modifier = Modifier.weight(1f)
            ) { step ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                ) {
                    Spacer(Modifier.height(12.dp))
                    when (step) {
                        SetupStep.ABOUT -> AboutStep(state, viewModel)
                        SetupStep.EXPERIENCE -> ChoiceStep(
                            lead = "How would you",
                            accent = "describe yourself?",
                            body = "So Tanha knows where to start with you.",
                            options = Experience.entries,
                            selected = state.experience,
                            onPick = viewModel::onExperience
                        )
                        SetupStep.DURATION -> ChoiceStep(
                            lead = "How long have",
                            accent = "you been trading?",
                            body = "Forex, gold, anything counts.",
                            options = TradingDuration.entries,
                            selected = state.duration,
                            onPick = viewModel::onDuration
                        )
                        SetupStep.BROKER -> BrokerStep(state, viewModel)
                        SetupStep.IB -> IbStep(state, viewModel)
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }

            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                AnimatedVisibility(
                    visible = state.error != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Row(
                        Modifier.padding(bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = WebColors.Red,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(state.error.orEmpty(), style = WebType.small.copy(color = WebColors.Red))
                    }
                }
                WebButton(
                    text = if (state.isLastStep) "Finish setup" else "Continue",
                    onClick = viewModel::next,
                    enabled = state.canContinue,
                    loading = state.isSaving,
                    icon = Icons.AutoMirrored.Filled.ArrowForward
                )
            }
        }
        ConfettiBurst(burst)
    }
}

// ── Chrome ─────────────────────────────────────────────────────────────────

@Composable
private fun SetupTopBar(stepIndex: Int, stepCount: Int, onBack: () -> Unit, onSignOut: () -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                androidx.compose.animation.AnimatedVisibility(visible = stepIndex > 0, enter = fadeIn(), exit = fadeOut()) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .border(1.dp, WebColors.Line2, CircleShape)
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = WebColors.Text,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                "STEP ${stepIndex + 1} OF $stepCount",
                style = WebType.small.copy(fontFamily = WebFonts.Mono, fontSize = 11.sp)
            )
            Spacer(Modifier.weight(1f))
            Text(
                "Sign out",
                style = WebType.small.copy(fontWeight = FontWeight.Medium),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onSignOut)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(stepCount) { i ->
                val fill by animateFloatAsState(
                    if (i <= stepIndex) 1f else 0f,
                    spring(dampingRatio = 0.8f, stiffness = 200f),
                    label = "progress$i"
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fill)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(WebColors.Brand)
                    )
                }
            }
        }
    }
}

// ── Steps ──────────────────────────────────────────────────────────────────

@Composable
private fun AboutStep(state: ProfileSetupUiState, viewModel: ProfileSetupViewModel) {
    var pickingCountry by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    WebHeadline(lead = "Set up your", accent = "profile.", size = 36.sp)
    Spacer(Modifier.height(8.dp))
    Text("A photo and your name are how the community sees you.", style = WebType.lead)

    Spacer(Modifier.height(24.dp))

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // Without a photo the circle gives a little shake every few seconds —
        // a nudge that it can be tapped, not a demand.
        val hasPhoto = !state.photoUrl.isNullOrBlank()
        val wiggle = rememberInfiniteTransition(label = "wiggle")
        val tilt by wiggle.animateFloat(
            initialValue = 0f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                keyframes {
                    durationMillis = 3200
                    0f at 0
                    0f at 2200
                    -8f at 2320
                    7f at 2440
                    -4f at 2560
                    2f at 2680
                    0f at 2800
                }
            ),
            label = "tilt"
        )
        AvatarEditor(
            photoUrl = state.photoUrl?.takeIf { it.isNotBlank() },
            initial = state.initial,
            isUploading = state.isUploadingPhoto,
            onCropped = viewModel::onPhotoCropped,
            onRemove = viewModel::onPhotoRemoved,
            size = 96.dp,
            accent = WebColors.Brand,
            onAccent = WebColors.OnBrand,
            placeholderGradient = listOf(WebColors.Ink3, WebColors.Ink2),
            initialColor = WebColors.Brand,
            modifier = Modifier.graphicsLayer { rotationZ = if (hasPhoto) 0f else tilt }
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (state.photoUrl.isNullOrBlank()) "Tap to add a photo (optional)" else "Tap to change",
            style = WebType.small
        )
    }

    Spacer(Modifier.height(24.dp))

    TwtTextField(
        value = state.fullName,
        onValueChange = viewModel::onNameChange,
        label = "Full name",
        capitalization = KeyboardCapitalization.Words,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(Modifier.height(12.dp))

    // A read-only Material field that opens the picker — the exposed-dropdown
    // look, with a searchable sheet behind it because the list is long.
    Box {
        TwtTextField(
            value = if (state.country.length == 2) Countries.name(state.country) else "",
            onValueChange = {},
            label = "Country",
            placeholder = "Choose your country",
            readOnly = true,
            leading = if (state.country.length == 2) {
                { Text(flagOf(state.country), fontSize = 20.sp) }
            } else null,
            trailing = { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth()
        )
        Box(
            Modifier
                .matchParentSize()
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(3.dp))
                .clickable {
                Haptics.sheetOpen(haptics)
                pickingCountry = true
            }
        )
    }

    if (pickingCountry) {
        CountryPickerSheet(
            selected = state.country,
            onPick = {
                Haptics.toggleOn(haptics)
                viewModel.onCountryChange(it)
                pickingCountry = false
            },
            onDismiss = {
                Haptics.sheetClose(haptics)
                pickingCountry = false
            }
        )
    }
}

@Composable
private fun <T : ProfileOption> ChoiceStep(
    lead: String,
    accent: String,
    body: String,
    options: List<T>,
    selected: T?,
    onPick: (T) -> Unit
) {
    WebHeadline(lead = lead, accent = accent, size = 36.sp)
    Spacer(Modifier.height(8.dp))
    Text(body, style = WebType.lead)
    Spacer(Modifier.height(24.dp))
    OptionList(options, selected, onPick)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BrokerStep(state: ProfileSetupUiState, viewModel: ProfileSetupViewModel) {
    WebHeadline(lead = "Do you trade with", accent = "a broker yet?", size = 36.sp)
    Spacer(Modifier.height(8.dp))
    Text("It helps the team help you with the right account.", style = WebType.lead)
    Spacer(Modifier.height(24.dp))

    OptionList(BrokerStatus.entries, state.brokerStatus, viewModel::onBrokerStatus)

    AnimatedVisibility(
        visible = state.brokerStatus?.hasAccount == true,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Column {
            Spacer(Modifier.height(24.dp))
            FieldLabel("Which broker?")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                (Brokers.known + Brokers.OTHER).forEachIndexed { index, name ->
                    RiseIn(delayMillis = 60 + index * 40, distance = 10.dp) {
                        BrokerChip(name, state.broker == name) { viewModel.onBroker(name) }
                    }
                }
            }
            AnimatedVisibility(visible = state.broker == Brokers.OTHER) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    TwtTextField(
                        value = state.brokerOther,
                        onValueChange = viewModel::onBrokerOther,
                        label = "Broker name",
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun IbStep(state: ProfileSetupUiState, viewModel: ProfileSetupViewModel) {
    WebHeadline(lead = "Is your account", accent = "under an IB?", size = 36.sp)
    Spacer(Modifier.height(8.dp))
    Text(
        "Your ${state.broker?.takeIf { it != Brokers.OTHER } ?: "broker"} account, that is.",
        style = WebType.lead
    )
    Spacer(Modifier.height(16.dp))

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(WebColors.BrandSoft)
            .padding(14.dp)
    ) {
        Icon(Icons.Outlined.Info, null, tint = WebColors.Brand, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            "An IB (introducing broker) is a partner your account is linked to when " +
                "you open it. It doesn't change your spreads or your trading.",
            style = WebType.small.copy(color = WebColors.Text2, fontSize = 13.sp, lineHeight = 19.sp)
        )
    }

    Spacer(Modifier.height(20.dp))

    OptionList(IbStatus.entries, state.ibStatus, viewModel::onIb)
}

// ── Pieces ─────────────────────────────────────────────────────────────────

/**
 * The answers to one question, as rows in a single panel rather than a stack
 * of look-alike boxes. Each row has a letter key, like a paper form; the chosen
 * one floods green from the finger. Scale questions draw their position as
 * rising bars. A sticker with a short reply stamps onto the panel's corner.
 */
@Composable
private fun <T : ProfileOption> OptionList(options: List<T>, selected: T?, onPick: (T) -> Unit) {
    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(WebColors.Ink2.copy(alpha = 0.92f))
                .border(1.dp, WebColors.Line2, RoundedCornerShape(18.dp))
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            options.forEachIndexed { index, option ->
                RiseIn(delayMillis = 60 + index * 55, distance = 14.dp) {
                    OptionRow(
                        key = 'A' + index,
                        option = option,
                        selected = option == selected,
                        dimmed = selected != null && option != selected,
                        appearDelay = 120 + index * 55,
                        onClick = { onPick(option) }
                    )
                }
            }
        }
        // Re-keyed on the answer, so every new pick stamps a fresh sticker.
        selected?.reaction?.let { reaction ->
            key(selected) {
                WebSticker(
                    reaction,
                    rotation = if (options.indexOf(selected) % 2 == 0) 4f else -4f,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-10).dp, y = (-14).dp)
                )
            }
        }
    }
}

@Composable
private fun OptionRow(
    key: Char,
    option: ProfileOption,
    selected: Boolean,
    dimmed: Boolean,
    appearDelay: Int,
    onClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }

    // Chosen: a soft green wash and outline rather than a solid flood, a bar
    // growing up the left edge, the key flipping over to a tick, and one ring
    // of glow pulsing out from the row.
    val chosen by animateFloatAsState(
        if (selected) 1f else 0f,
        spring(dampingRatio = 0.7f, stiffness = 320f),
        label = "chosen"
    )
    val flip by animateFloatAsState(
        if (selected) 180f else 0f,
        spring(dampingRatio = 0.55f, stiffness = 260f),
        label = "flip"
    )
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected) {
            pulse.snapTo(0f)
            pulse.animateTo(1f, tween(620, easing = FastOutSlowInEasing))
        }
    }
    val alpha by animateFloatAsState(if (dimmed) 0.55f else 1f, label = "dim")
    val shape = RoundedCornerShape(13.dp)

    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                // The glow ring, drawn outside the row before it is clipped.
                if (pulse.value < 1f) {
                    val grow = pulse.value * 6.dp.toPx()
                    drawRoundRect(
                        color = WebColors.Brand.copy(alpha = (1f - pulse.value) * 0.55f),
                        topLeft = Offset(-grow, -grow),
                        size = androidx.compose.ui.geometry.Size(size.width + grow * 2, size.height + grow * 2),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(13.dp.toPx() + grow),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                    )
                }
            }
            .pressScale(interaction, pressedScale = 0.97f)
            .graphicsLayer { this.alpha = alpha }
            .clip(shape)
            .background(WebColors.Brand.copy(alpha = 0.09f * chosen))
            .border(
                width = 1.dp + 0.5.dp * chosen,
                color = lerp(Color.Transparent, WebColors.Brand.copy(alpha = 0.7f), chosen),
                shape = shape
            )
            .drawBehind {
                // The accent bar, growing from the middle of the left edge.
                if (chosen > 0f) {
                    val h = size.height * 0.52f * chosen
                    drawRoundRect(
                        color = WebColors.Brand,
                        topLeft = Offset(0f, (size.height - h) / 2f),
                        size = androidx.compose.ui.geometry.Size(3.dp.toPx(), h),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                    )
                }
            }
            .pressFlood(interaction, WebColors.Brand.copy(alpha = 0.10f))
            .clickable(interactionSource = interaction, indication = null) {
                Haptics.toggleOn(haptics)
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The key card: the letter on the front, a tick on the back.
        Box(
            Modifier
                .size(28.dp)
                .graphicsLayer {
                    rotationY = flip
                    cameraDistance = 12f * density
                },
            contentAlignment = Alignment.Center
        ) {
            if (flip < 90f) {
                Box(
                    Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(7.dp))
                        .border(1.dp, WebColors.Line2, RoundedCornerShape(7.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        key.toString(),
                        style = TextStyle(
                            fontFamily = WebFonts.Mono,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = WebColors.Text2
                        )
                    )
                }
            } else {
                Box(
                    Modifier
                        .matchParentSize()
                        .graphicsLayer { rotationY = 180f }
                        .clip(RoundedCornerShape(7.dp))
                        .background(WebColors.Brand),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = WebColors.OnBrand, modifier = Modifier.size(17.dp))
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                option.title,
                style = TextStyle(
                    fontFamily = WebFonts.Body,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = WebColors.Text
                )
            )
            option.subtitle?.let {
                Spacer(Modifier.height(1.dp))
                Text(it, style = WebType.small.copy(color = lerp(WebColors.Text3, WebColors.Text2, chosen)))
            }
        }
        val level = option.level
        if (level != null && option.maxLevel > 0) {
            Spacer(Modifier.width(12.dp))
            LevelBars(
                level = level,
                max = option.maxLevel,
                selected = selected,
                appearDelay = appearDelay,
                on = WebColors.Brand,
                off = WebColors.Line2
            )
        }
    }
}

/**
 * Rising bars, [level] of [max] lit — a signal-strength meter for a scale
 * answer. They grow in one after another when the row appears, and ripple up
 * like an equaliser when the answer is picked.
 */
@Composable
private fun LevelBars(level: Int, max: Int, selected: Boolean, appearDelay: Int, on: Color, off: Color) {
    val grow = remember { List(max) { Animatable(0f) } }
    LaunchedEffect(Unit) {
        grow.forEachIndexed { i, bar ->
            launch {
                delay((appearDelay + i * 70).toLong())
                bar.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 380f))
            }
        }
    }
    val wave = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected) {
            wave.snapTo(0f)
            wave.animateTo(1f, tween(650, easing = LinearEasing))
        }
    }

    Row(
        Modifier.height(24.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        repeat(max) { i ->
            // A bump that travels left to right across the bars.
            val phase = (wave.value * (max + 1) - i).coerceIn(0f, 1f)
            val bump = kotlin.math.sin(phase * Math.PI).toFloat() * 0.45f
            Box(
                Modifier
                    .width(4.dp)
                    .height(6.dp + (12.dp * (i + 1) / max))
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        scaleY = grow[i].value * (1f + bump)
                    }
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (i < level) on else off)
            )
        }
    }
}

@Composable
private fun BrokerChip(name: String, selected: Boolean, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val fill by animateColorAsState(if (selected) WebColors.BrandSoft else Color.White.copy(alpha = 0.03f), label = "chip")
    val pop = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected) {
            pop.snapTo(0.8f)
            pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 500f))
        }
    }
    Box(
        Modifier
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
            .pressScale(interaction, pressedScale = 0.92f)
            .clip(CircleShape)
            .background(fill)
            .pressFlood(interaction, WebColors.Brand.copy(alpha = 0.2f))
            .border(1.dp, if (selected) WebColors.Brand else WebColors.Line2, CircleShape)
            .clickable(interactionSource = interaction, indication = null) {
                Haptics.toggleOn(haptics)
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedVisibility(
                visible = selected,
                enter = fadeIn() + androidx.compose.animation.scaleIn(spring(dampingRatio = 0.4f)) +
                    androidx.compose.animation.expandHorizontally(),
                exit = fadeOut() + androidx.compose.animation.shrinkHorizontally()
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = WebColors.Brand,
                    modifier = Modifier.padding(end = 6.dp).size(16.dp)
                )
            }
            Text(
                name,
                style = TextStyle(
                    fontFamily = WebFonts.Body,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = WebColors.Text
                )
            )
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style = WebType.small.copy(color = WebColors.Text2, fontWeight = FontWeight.Medium),
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

/** A searchable list of every country, with the ones most members pick first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountryPickerSheet(selected: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val all = remember {
        Locale.getISOCountries()
            .map { it to Countries.name(it) }
            .filter { it.second != "Not set" }
            .sortedBy { it.second }
    }
    val popular = remember { listOf("PK", "AE", "SA", "GB", "US", "IN") }
    val shown = remember(query) {
        if (query.isBlank()) {
            all.filter { it.first in popular }.sortedBy { popular.indexOf(it.first) } +
                all.filter { it.first !in popular }
        } else {
            all.filter { it.second.contains(query.trim(), ignoreCase = true) || it.first.equals(query.trim(), true) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = WebColors.Ink2,
        dragHandle = null
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(top = 20.dp)) {
            Text("Your country", style = WebType.h3)
            Spacer(Modifier.height(12.dp))
            TwtTextField(
                value = query,
                onValueChange = { query = it },
                label = "Search countries",
                leading = { Icon(Icons.Outlined.Search, contentDescription = null) },
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Search,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.fillMaxWidth().height(460.dp)) {
                items(shown, key = { it.first }) { (code, name) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPick(code) }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(flagOf(code), fontSize = 22.sp)
                        Spacer(Modifier.width(14.dp))
                        Text(
                            name,
                            style = WebType.lead.copy(color = WebColors.Text),
                            modifier = Modifier.weight(1f)
                        )
                        if (code == selected) {
                            Icon(Icons.Filled.Check, null, tint = WebColors.Brand, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

/** "PK" → 🇵🇰, from the two regional-indicator letters. */
private fun flagOf(code: String): String =
    code.uppercase().filter { it in 'A'..'Z' }.take(2)
        .map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }
        .joinToString("")
