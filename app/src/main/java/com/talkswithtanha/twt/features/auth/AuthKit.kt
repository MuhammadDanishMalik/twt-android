package com.talkswithtanha.twt.features.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.remember
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

/** The card fill, matching the Exchange screen. */
internal val AuthSurface = Color(0xFF1A1A1C)

/** The primary action's violet, lit in the middle. Same as the Exchange CTA. */
internal val AuthGradient = listOf(
    Color(0xFF6C2BE1),
    Color(0xFF9151FF),
    Color(0xFF7E3FEB)
)

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
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(AuthGradient.map { it.copy(alpha = alpha) })
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled && !loading,
                onClick = onClick
            )
            .padding(vertical = 17.dp),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Text(
                text = text,
                color = Color.White.copy(alpha = if (enabled) 1f else 0.7f),
                fontSize = 16.sp,
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

    // The keyboard comes up on arrival. This screen has exactly one job and
    // making somebody tap to start it is a step for nobody's benefit.
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Box(modifier) {
        BasicTextField(
            value = code,
            onValueChange = onCodeChange,
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
                color = if (isNext) IosColors.Accent.copy(alpha = borderAlpha)
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
                    .background(IosColors.Accent.copy(alpha = 0.7f))
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
                color = IosColors.Accent,
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
            fontSize = 30.sp,
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
 * A labelled field in the auth family's style.
 *
 * The label lives above the box rather than floating into it. A floating label
 * animates over the value on a screen where the value is often a password the
 * member is squinting at, and the movement is the last thing they need.
 */
@Composable
internal fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    enabled: Boolean = true,
    onTogglePasswordVisibility: (() -> Unit)? = null
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(7.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(AuthSurface)
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = Color.White,
                    fontSize = 16.sp
                ),
                cursorBrush = SolidColor(IosColors.Accent),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                visualTransformation = if (isPassword && !passwordVisible) {
                    androidx.compose.ui.text.input.PasswordVisualTransformation()
                } else {
                    androidx.compose.ui.text.input.VisualTransformation.None
                },
                modifier = Modifier.weight(1f)
            )
            if (isPassword && onTogglePasswordVisibility != null) {
                Icon(
                    imageVector = if (passwordVisible) Icons.Filled.VisibilityOff
                    else Icons.Filled.Visibility,
                    contentDescription = if (passwordVisible) "Hide password" else "Show password",
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(onClick = onTogglePasswordVisibility)
                )
            }
        }
    }
}
