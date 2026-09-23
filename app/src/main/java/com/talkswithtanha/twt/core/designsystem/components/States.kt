package com.talkswithtanha.twt.core.designsystem.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import com.talkswithtanha.twt.core.designsystem.rememberShimmerProgress
import com.talkswithtanha.twt.core.designsystem.Radius
import com.talkswithtanha.twt.core.designsystem.Spacing
import com.talkswithtanha.twt.core.designsystem.TwtColors

/**
 * A shimmering block, for the shape of content that has not arrived.
 *
 * Skeletons rather than a spinner because the layout is known before the data
 * is: showing the geometry the real content will occupy means nothing jumps when
 * it lands.
 */
/**
 * A loading placeholder with a highlight sweeping across it.
 *
 * A sweep rather than a pulse. A block that fades in and out reads as something
 * blinking at you; a highlight travelling left to right reads as the direction
 * text is about to arrive from, which is why every platform settled on it.
 */
@Composable
fun Shimmer(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    cornerRadius: Dp = 8.dp
) {
    val progress = rememberShimmerProgress()

    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .drawWithCache {
                val base = TwtColors.SurfaceElevated
                val highlight = Color.White.copy(alpha = 0.06f)
                // Travels a full width beyond each edge, so the highlight
                // enters and leaves rather than appearing in the middle.
                val sweep = size.width * (progress * 2f - 0.5f)
                val brush = Brush.linearGradient(
                    colors = listOf(base, highlight, base),
                    start = Offset(sweep - size.width * 0.4f, 0f),
                    end = Offset(sweep + size.width * 0.4f, 0f)
                )
                onDrawBehind {
                    drawRect(base)
                    drawRect(brush)
                }
            }
    )
}

/** The skeleton for one signal card, sized so nothing reflows when data lands. */
@Composable
fun SignalCardSkeleton(modifier: Modifier = Modifier) {
    TwtCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Shimmer(Modifier.width(110.dp), height = 20.dp)
            Shimmer(Modifier.width(64.dp), height = 20.dp)
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(Spacing.lg))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
            repeat(3) { Shimmer(Modifier.width(64.dp), height = 32.dp) }
        }
    }
}

/**
 * Nothing here, and that is fine.
 *
 * Distinct from [ErrorState] on purpose: "no signals yet" and "we could not
 * reach the server" call for different words and different buttons, and
 * collapsing them makes a working app look broken.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(Radius.chip))
                .background(TwtColors.SurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = TwtColors.TextTertiary)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = TwtColors.TextPrimary,
            textAlign = TextAlign.Center
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = TwtColors.TextSecondary,
            textAlign = TextAlign.Center
        )
        action?.invoke()
    }
}

/** Something failed, and there is a way to try again. */
@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Icon(
            Icons.Outlined.CloudOff,
            contentDescription = null,
            tint = TwtColors.TextTertiary,
            modifier = Modifier.size(40.dp)
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = TwtColors.TextSecondary,
            textAlign = TextAlign.Center
        )
        onRetry?.let {
            TwtSecondaryButton(text = "Try again", onClick = it)
        }
    }
}
