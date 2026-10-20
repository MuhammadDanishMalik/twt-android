package com.talkswithtanha.twt.features.auth

import com.talkswithtanha.twt.core.designsystem.pressFlood
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.talkswithtanha.twt.core.designsystem.components.TwtTextField
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import com.talkswithtanha.twt.core.designsystem.WebFonts
import com.talkswithtanha.twt.core.designsystem.WebColors
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalHapticFeedback
import com.talkswithtanha.twt.core.designsystem.Haptics
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.designsystem.Motion
import com.talkswithtanha.twt.core.designsystem.pressScale

/**
 * The pieces the three auth screens share.
 *
 * One file because sign in, verify and reset are one journey. A member who
 * mistypes a password, asks for a code and then sets a new one should not be
 * able to tell where one screen ends and the next begins.
 */

/** The field and card fill: the website's `--ink-3`. */
internal val AuthSurface = WebColors.Ink3

/**
 * The primary action: the website's flat green (`.lp-btn-primary`). Flat on
 * purpose — a lit gradient turns muddy when the button dims to disabled. Text
 * on it is dark, see [WebColors.OnBrand].
 */
internal val AuthGradient = listOf(WebColors.Brand, WebColors.Brand)

/** The full-width violet button every auth screen finishes on. */
@Composable
internal fun AuthPrimaryButton(
    text: String,
    enabled: Boolean,
    loading: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    // Dimmed rather than greyed: the button keeps its colour so it still reads
    // as the way forward, and says "not yet" instead of "not for you".
    val alpha by animateFloatAsState(
        targetValue = if (enabled || loading) 1f else 0.38f,
        animationSpec = Motion.quick(),
        label = "enabled"
    )

    Box(
        modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .background(Brush.horizontalGradient(AuthGradient))
            .pressFlood(interaction, Color.White.copy(alpha = 0.38f))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled && !loading,
                onClick = {
                    Haptics.tap(haptics)
                    onClick()
                }
            )
            .padding(vertical = 17.dp),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = WebColors.OnBrand,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Text(
                text = text,
                color = WebColors.OnBrand,
                fontSize = 16.sp,
                fontFamily = WebFonts.Body,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * An error, when there is one.
 *
 * Expands rather than appearing, so the fields below do not jump by the height
 * of a line the instant somebody mistypes a password.
 */
@Composable
internal fun AuthError(message: String?, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(Motion.quick()) + expandVertically(Motion.gentle()),
        exit = fadeOut(Motion.quick()) + shrinkVertically(Motion.gentle()),
        modifier = modifier
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(IosColors.SellRed.copy(alpha = 0.10f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = IosColors.SellRed,
                modifier = Modifier.size(15.dp)
            )
            // Held so the text does not vanish before the box finishes closing.
            Text(
                text = message ?: "",
                color = IosColors.SellRed,
                fontSize = 13.sp
            )
        }
    }
}

/**
 * Six boxes that hold one code.
 *
 * A single text field would work and would look like a form. This looks like a
 * code, which tells somebody what to do with it before they read the label.
 *
 * There is one real input underneath, transparent and full-bleed: six separate
 * fields mean six focus targets, backspace that jumps the wrong way, and a
 * paste that only fills the first box.
 */
@Composable
internal fun CodeInput(
    code: String,
    length: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onCodeChange: (String) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val haptics = LocalHapticFeedback.current

    // The keyboard comes up on arrival. This screen has exactly one job and
    // making somebody tap to start it is a step for nobody's benefit.
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Box(modifier) {
        BasicTextField(
            value = code,
            onValueChange = { next ->
                // Only on a digit landing, not on a delete: backspace already
                // has the keyboard's own feedback and doubling it feels broken.
                if (next.length > code.length) Haptics.key(haptics)
                onCodeChange(next)
            },
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            // Transparent, not hidden: a field with no size cannot hold focus
            // and the keyboard closes itself.
            cursorBrush = SolidColor(Color.Transparent),
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.Transparent),
            modifier = Modifier
                .matchParentSize()
                .focusRequester(focusRequester)
                .focusable()
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(length) { index ->
                val character = code.getOrNull(index)
                // The next empty box is marked, so it is obvious where the
                // typing is going without a blinking caret to chase.
                val isNext = index == code.length
                CodeCell(character, isNext && enabled, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CodeCell(character: Char?, isNext: Boolean, modifier: Modifier = Modifier) {
    val borderAlpha by animateFloatAsState(
        targetValue = if (isNext) 0.55f else 0.10f,
        animationSpec = Motion.quick(),
        label = "border"
    )

    Box(
        modifier
            .height(58.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(AuthSurface)
            .border(
                width = if (isNext) 1.5.dp else 1.dp,
                color = if (isNext) WebColors.Brand.copy(alpha = borderAlpha)
                else Color.White.copy(alpha = borderAlpha),
                shape = RoundedCornerShape(13.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (character != null) {
            Text(
                text = character.toString(),
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold
            )
        } else if (isNext) {
            Box(
                Modifier
                    .size(width = 2.dp, height = 22.dp)
                    .clip(CircleShape)
                    .background(WebColors.Brand.copy(alpha = 0.7f))
            )
        }
    }
}

/** "Resend in 42s", or a live link once the wait is over. */
@Composable
internal fun ResendRow(
    secondsRemaining: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onResend: () -> Unit
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Didn't get it?",
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 14.sp
        )
        Spacer(Modifier.width(6.dp))
        if (secondsRemaining > 0) {
            Text(
                text = "Resend in ${secondsRemaining}s",
                color = Color.White.copy(alpha = 0.3f),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        } else {
            Text(
                text = "Send again",
                color = WebColors.Brand,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = enabled, onClick = onResend)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

/** The heading block every auth screen opens with. */
@Composable
internal fun AuthHeading(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 32.sp,
            lineHeight = 34.sp,
            fontFamily = WebFonts.Display,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 15.sp,
            lineHeight = 21.sp
        )
    }
}

/** Centred confirmation, for the end of a flow. */
@Composable
internal fun AuthSuccess(title: String, message: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(IosColors.BuyGreen.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = IosColors.BuyGreen,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(text = title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            lineHeight = 21.sp
        )
    }
}

/**
 * A form field on the auth screens: the app's Material 3 [TwtTextField].
 *
 * Passwords carry their own show/hide toggle, so screens no longer track it.
 */
@Composable
internal fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    imeAction: ImeAction = ImeAction.Next
) {
    TwtTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier.fillMaxWidth(),
        keyboardType = keyboardType,
        isPassword = isPassword,
        enabled = enabled,
        isError = isError,
        supportingText = supportingText,
        capitalization = capitalization,
        autoCorrect = keyboardType != KeyboardType.Email,
        imeAction = imeAction
    )
}

/** `────── or ──────`, the seam between the primary path and the alternatives. */
@Composable
internal fun OrDivider(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(Color.White.copy(alpha = 0.10f)))
        Text(
            text = "or",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 14.dp)
        )
        Box(Modifier.weight(1f).height(1.dp).background(Color.White.copy(alpha = 0.10f)))
    }
}

/** An outlined full-width alternative, below the divider. */
@Composable
internal fun AuthOutlinedButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.03f))
            .pressFlood(interaction, WebColors.Brand.copy(alpha = 0.16f))
            .border(1.dp, WebColors.Line2, CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null
            ) {
                Haptics.tap(haptics)
                onClick()
            }
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = WebColors.Text,
            fontSize = 15.sp,
            fontFamily = WebFonts.Body,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * A live strength meter for a new password: four bars that fill and change
 * colour, the rating in words, and the checks that make it up. Shown on sign-up
 * only — on sign-in the password already exists and rating it helps nobody.
 */
@Composable
internal fun PasswordStrengthMeter(password: String, modifier: Modifier = Modifier) {
    val strength = ratePassword(password)
    val checks = passwordChecks(password)
    // A tick each time the rating moves, so it can be felt while typing.
    val haptics = LocalHapticFeedback.current
    var lastStrength by remember { mutableStateOf<PasswordStrength?>(null) }
    LaunchedEffect(strength, password.isEmpty()) {
        if (password.isNotEmpty() && lastStrength != null && lastStrength != strength) {
            if (strength == PasswordStrength.Strong) Haptics.success(haptics) else Haptics.tick(haptics)
        }
        lastStrength = if (password.isEmpty()) null else strength
    }
    val color by animateColorAsState(
        when (strength) {
            PasswordStrength.TooShort, PasswordStrength.Weak -> WebColors.Red
            PasswordStrength.Fair -> WebColors.Amber
            PasswordStrength.Good -> WebColors.Mint
            PasswordStrength.Strong -> WebColors.Brand
        },
        animationSpec = Motion.quick(),
        label = "strength"
    )

    AnimatedVisibility(
        visible = password.isNotEmpty(),
        enter = fadeIn(Motion.quick()) + expandVertically(Motion.gentle()),
        exit = fadeOut(Motion.quick()) + shrinkVertically(Motion.gentle()),
        modifier = modifier
    ) {
        Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    repeat(4) { i ->
                        val fill by animateFloatAsState(
                            targetValue = if (i < strength.filledBars) 1f else 0f,
                            animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
                            label = "bar$i"
                        )
                        Box(
                            Modifier
                                .weight(1f)
                                .height(5.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(fill)
                                    .height(5.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = strength.label,
                    color = color,
                    fontSize = 13.sp,
                    fontFamily = WebFonts.Body,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PasswordCheck("$MIN_NEW_PASSWORD_LENGTH+ chars", checks.longEnough)
                PasswordCheck("Aa", checks.mixedCase)
                PasswordCheck("123", checks.hasNumber)
                PasswordCheck("#!?", checks.hasSymbol)
            }
        }
    }
}

@Composable
private fun PasswordCheck(label: String, met: Boolean) {
    val tint by animateColorAsState(
        if (met) WebColors.Brand else Color.White.copy(alpha = 0.35f),
        animationSpec = Motion.quick(),
        label = "check"
    )
    // The label reads white once met; green is kept for the tick itself, so
    // the row does not turn into a wall of green text.
    val labelColor by animateColorAsState(
        if (met) WebColors.Text else Color.White.copy(alpha = 0.4f),
        animationSpec = Motion.quick(),
        label = "checkLabel"
    )
    val pop by animateFloatAsState(
        targetValue = if (met) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 500f),
        label = "pop"
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(16.dp)
                .graphicsLayer {
                    val s = 0.85f + 0.15f * pop
                    scaleX = s
                    scaleY = s
                }
                .clip(CircleShape)
                .background(if (met) WebColors.BrandSoft else Color.Transparent)
                .border(1.dp, tint, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (met) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(11.dp)
                )
            }
        }
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            color = labelColor,
            fontSize = 12.sp,
            fontFamily = WebFonts.Body,
            fontWeight = FontWeight.Medium
        )
    }
}
