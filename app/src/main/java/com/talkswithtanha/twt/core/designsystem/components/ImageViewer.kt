package com.talkswithtanha.twt.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.SubcomposeAsyncImage
import com.talkswithtanha.twt.core.designsystem.Haptics
import com.talkswithtanha.twt.core.images.Cloudinary
import kotlinx.coroutines.launch
import kotlin.math.max

private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f

/**
 * Full-screen viewer for a signal's charts and screenshots.
 *
 * Swipe between images; pinch or double-tap to zoom, drag to pan while zoomed.
 * The pager only takes the swipe while the image is at its normal size, so
 * panning a zoomed chart never flips to the next one by accident.
 */
@Composable
fun ImageViewer(
    images: List<String>,
    startIndex: Int,
    onDismiss: () -> Unit
) {
    if (images.isEmpty()) return
    val haptics = LocalHapticFeedback.current
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, images.lastIndex)) { images.size }
    var zoomed by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(
                state = pager,
                userScrollEnabled = !zoomed,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                ZoomableImage(
                    url = images[page],
                    onZoomChanged = { if (pager.currentPage == page) zoomed = it }
                )
            }

            Box(
                Modifier
                    .safeDrawingPadding()
                    .padding(12.dp)
                    .align(Alignment.TopEnd)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.14f))
                    .clickable {
                        Haptics.tap(haptics)
                        onDismiss()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(20.dp))
            }

            if (images.size > 1) {
                Text(
                    "${pager.currentPage + 1} / ${images.size}",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .safeDrawingPadding()
                        .padding(bottom = 20.dp)
                        .align(Alignment.BottomCenter)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.14f))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun ZoomableImage(url: String, onZoomChanged: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    fun clamp(o: Offset, s: Float): Offset {
        val maxX = max(0f, (size.width * s - size.width) / 2f)
        val maxY = max(0f, (size.height * s - size.height) / 2f)
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    fun reset() = scope.launch {
        offset = Offset.Zero
        scale.animateTo(1f, spring(dampingRatio = 0.8f))
        onZoomChanged(false)
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(url) {
                size = this.size
                detectTapGestures(
                    onDoubleTap = { tap ->
                        if (scale.value > 1.05f) {
                            reset()
                        } else scope.launch {
                            // Zoom in on the point that was tapped.
                            val target = DOUBLE_TAP_ZOOM
                            val center = Offset(size.width / 2f, size.height / 2f)
                            offset = clamp((center - tap) * (target - 1f), target)
                            onZoomChanged(true)
                            scale.animateTo(target, spring(dampingRatio = 0.8f))
                        }
                    }
                )
            }
            .pointerInput(url) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        val pinching = event.changes.size > 1
                        // Only take the gesture when pinching or already zoomed;
                        // a one-finger swipe at 1x belongs to the pager.
                        if (pinching || scale.value > 1.01f) {
                            val newScale = (scale.value * zoom).coerceIn(1f, MAX_ZOOM)
                            val centroid = event.calculateCentroid(useCurrent = false)
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val focal = if (centroid == Offset.Unspecified) center else centroid
                            val scaleChange = if (scale.value == 0f) 1f else newScale / scale.value
                            offset = clamp(
                                (offset + center - focal) * scaleChange - (center - focal) + pan,
                                newScale
                            )
                            scope.launch { scale.snapTo(newScale) }
                            onZoomChanged(newScale > 1.01f)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    if (scale.value < 1.01f) {
                        offset = Offset.Zero
                        onZoomChanged(false)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        SubcomposeAsyncImage(
            model = Cloudinary.wide(url, width = 2000),
            contentDescription = "Screenshot",
            contentScale = ContentScale.Fit,
            loading = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingLabel("Loading image")
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    translationX = offset.x
                    translationY = offset.y
                }
        )
    }
}
