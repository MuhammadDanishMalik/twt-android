package com.talkswithtanha.twt.features.profile

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talkswithtanha.twt.core.designsystem.Haptics
import com.talkswithtanha.twt.core.designsystem.IosColors
import kotlin.math.max
import kotlin.math.min

/** How far past its covering size a member may zoom in. */
private const val MAX_ZOOM = 5f

/** The crop window, as a fraction of the stage's shorter edge. */
private const val WINDOW_FRACTION = 0.82f

/**
 * Frame the square.
 *
 * Pan and pinch rather than drag-handles on a rectangle: the output is always
 * square — every avatar in this app renders in a circle — so the only real
 * choices are which part of the photo and how close. Moving the photo under a
 * fixed window expresses exactly those two and nothing else.
 *
 * The image is laid out to *cover the crop window*, not the stage, and that is
 * load-bearing rather than incidental: [com.talkswithtanha.twt.core.images.cropSquare]
 * runs this same arithmetic backwards to find the source pixels, so if the two
 * ever disagree the uploaded crop is not the one the member framed.
 *
 * The window never shows emptiness. Offsets are clamped against the new zoom on
 * every gesture, so there is no way to frame a crescent of background.
 */
@Composable
fun AvatarCropScreen(
    source: Bitmap,
    isBusy: Boolean = false,
    onCancel: () -> Unit,
    onConfirm: (zoom: Float, offsetX: Float, offsetY: Float, windowPx: Float) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    var zoom by remember(source) { mutableFloatStateOf(1f) }
    var offsetX by remember(source) { mutableFloatStateOf(0f) }
    var offsetY by remember(source) { mutableFloatStateOf(0f) }
    var windowPx by remember { mutableFloatStateOf(0f) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Text(
                text = "Cancel",
                color = IosColors.TextPrimary,
                fontSize = 17.sp,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .clip(CircleShape)
                    .clickable(enabled = !isBusy) {
                        Haptics.tap(haptics)
                        onCancel()
                    }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
            Text(
                text = "Move and Scale",
                color = IosColors.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Center)
            )
            Text(
                text = if (isBusy) "Saving…" else "Choose",
                color = if (isBusy) IosColors.TextSecondary else IosColors.Accent,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clip(CircleShape)
                    .clickable(enabled = !isBusy && windowPx > 0f) {
                        Haptics.success(haptics)
                        onConfirm(zoom, offsetX, offsetY, windowPx)
                    }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }

        Box(
            modifier = Modifier
                // Weighted, not fillMaxSize: the stage takes what is left after
                // the header and the hint, rather than all of it and pushing
                // the hint off the bottom of the screen.
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { windowPx = min(it.width, it.height) * WINDOW_FRACTION }
                .pointerInput(source, windowPx) {
                    if (windowPx <= 0f) return@pointerInput
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        val next = (zoom * gestureZoom).coerceIn(1f, MAX_ZOOM)

                        // Clamped against the *new* zoom, so pinching back out
                        // never strands the image short of the window it covers.
                        val scale = coverScale(source, windowPx) * next
                        val slackX = max(0f, (source.width * scale - windowPx) / 2f)
                        val slackY = max(0f, (source.height * scale - windowPx) / 2f)

                        zoom = next
                        offsetX = (offsetX + pan.x).coerceIn(-slackX, slackX)
                        offsetY = (offsetY + pan.y).coerceIn(-slackY, slackY)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (windowPx > 0f) {
                val windowDp = with(density) { windowPx.toDp() }

                // Sized to the window and deliberately not clipped: the photo
                // spills past it and the dim below is what marks the edge.
                Box(Modifier.size(windowDp)) {
                    Image(
                        bitmap = source.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = zoom
                                scaleY = zoom
                                translationX = offsetX
                                translationY = offsetY
                            }
                    )
                }
            }

            // The surround, dimmed, with the circle punched out of it.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithCache {
                        val radius = windowPx / 2f
                        val centre = Offset(size.width / 2f, size.height / 2f)
                        onDrawWithContent {
                            drawRect(Color.Black.copy(alpha = 0.62f))
                            drawCircle(
                                color = Color.Black,
                                radius = radius,
                                center = centre,
                                blendMode = BlendMode.Clear
                            )
                            drawCircle(
                                color = Color.White.copy(alpha = 0.85f),
                                radius = radius,
                                center = centre,
                                style = Stroke(width = 2f)
                            )
                        }
                    }
            )
        }

        Text(
            text = "Drag to reposition · pinch to zoom",
            color = IosColors.TextSecondary,
            fontSize = 13.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

/**
 * The scale at which [source] exactly covers a square of [windowPx].
 *
 * The same expression `ContentScale.Crop` applies to a square box, written out
 * so the gesture clamp and the crop can both reason about it.
 */
private fun coverScale(source: Bitmap, windowPx: Float): Float =
    max(windowPx / source.width, windowPx / source.height)
