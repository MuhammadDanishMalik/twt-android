package com.talkswithtanha.twt.core.designsystem.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit

/** The springs behind every number change — soft, slightly under-damped, like iOS. */
private val SlideSpring = spring<IntOffset>(dampingRatio = 0.82f, stiffness = 420f)
private val SizeSpring = spring<IntSize>(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)
private val FadeSpring = spring<Float>(dampingRatio = 1f, stiffness = 520f)

/**
 * A number that rolls rather than jumps, after SwiftUI's
 * `.contentTransition(.numericText())`: each character that changes slides
 * out and the new one slides in — upwards when the value went up, downwards
 * when it went down — on a spring, with a short fade and a touch of scale.
 * Characters that did not change stay perfectly still, so "4,159.50 →
 * 4,159.75" only moves the last two digits.
 *
 * Positions are keyed from the right, so a value that gains a digit
 * ("9,999 → 10,000") grows on the left the way a counter does. Figures are
 * tabular so the row never shimmies sideways mid-roll.
 *
 * [text] is the formatted value, signs and separators included; the
 * direction comes from the digits in it.
 */
@Composable
fun RollingNumber(
    text: String,
    color: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    style: TextStyle = TextStyle.Default
) {
    val tint by animateColorAsState(color, spring(stiffness = Spring.StiffnessMediumLow), label = "rollingTint")
    val textStyle = style.merge(TextStyle(fontFeatureSettings = "tnum"))

    // Which way the last change went. Read during composition and updated
    // after, so it is not state: changing it must not recompose anything.
    val last = remember { DoubleArray(1) { Double.NaN } }
    val value = numericValue(text)
    val goingUp = value == null || last[0].isNaN() || value >= last[0]
    if (value != null) last[0] = value

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        text.forEachIndexed { index, char ->
            key(text.length - index) {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        val rolls = initialState.isDigit() && targetState.isDigit()
                        if (rolls) {
                            (slideInVertically(SlideSpring) { h -> if (goingUp) h / 2 else -h / 2 } +
                                fadeIn(FadeSpring) + scaleIn(FadeSpring, initialScale = 0.85f))
                                .togetherWith(
                                    slideOutVertically(SlideSpring) { h -> if (goingUp) -h / 2 else h / 2 } +
                                        fadeOut(FadeSpring) + scaleOut(FadeSpring, targetScale = 0.85f)
                                )
                        } else {
                            fadeIn(FadeSpring).togetherWith(fadeOut(FadeSpring))
                        }.using(SizeTransform(clip = false) { _, _ -> SizeSpring })
                    },
                    contentAlignment = Alignment.Center,
                    label = "rollingChar"
                ) { shown ->
                    Text(shown.toString(), color = tint, fontSize = fontSize, fontWeight = fontWeight, style = textStyle)
                }
            }
        }
    }
}

/** The value in a formatted number, for the direction of a change: "−1,234.5K" → -1234.5. */
private fun numericValue(text: String): Double? {
    val negative = text.trimStart().let { it.startsWith("-") || it.startsWith("−") }
    val digits = text.filter { it.isDigit() || it == '.' }
    return digits.toDoubleOrNull()?.let { if (negative) -it else it }
}
