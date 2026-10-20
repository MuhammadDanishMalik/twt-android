package com.talkswithtanha.twt.features.feed

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.talkswithtanha.twt.core.designsystem.Haptics
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.designsystem.Motion
import com.talkswithtanha.twt.core.voice.VoicePlayer
import com.talkswithtanha.twt.core.voice.formatVoiceDuration
import kotlinx.coroutines.delay
import kotlin.math.absoluteValue
import kotlin.math.sin

/**
 * A voice note, in the feed and in the composer's preview.
 *
 * ### Why the title is the biggest thing on it
 *
 * A column of players is unscannable. WhatsApp gets away with bare notes
 * because they arrive one at a time from someone you know; a feed does not —
 * nobody taps a grey bar labelled "1:42" to find out whether it matters. The
 * title carries the card and the duration is a detail underneath it.
 *
 * ### Why the waveform is drawn rather than measured
 *
 * The real amplitudes would mean decoding the file before anything could be
 * shown, on a phone, for every note in the feed. The bars here are a stable
 * function of the URL, so a given note always looks the same and no two notes
 * look alike — which is all the picture is doing. It says "this is audio and
 * here is how far through you are", and a true waveform says exactly that too.
 */
@Composable
fun VoiceNotePlayer(
    url: String,
    title: String,
    seconds: Int,
    modifier: Modifier = Modifier,
    /** Dims the title when the composer has not been given one yet. */
    isMuted: Boolean = false
) {
    val state by VoicePlayer.state.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current

    val isThisNote = state.url == url
    val isPlaying = isThisNote && state.isPlaying
    val progress = state.progressFor(url)

    // The player has no tick, so the position is pulled while this one is on
    // screen and playing — and only then, so a feed of notes is not twenty
    // timers deep.
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            VoicePlayer.refreshPosition()
            delay(120)
        }
    }

    // Whatever the recording says, once it is playing the file itself is the
    // authority — a note trimmed by Cloudinary would otherwise count past its
    // own end.
    val total = if (isThisNote && state.durationMs > 0) state.durationMs / 1000 else seconds
    val position = if (isThisNote) state.positionMs / 1000 else 0

    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF121212))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlayButton(
            isPlaying = isPlaying,
            isPreparing = isThisNote && state.isPreparing
        ) {
            Haptics.tap(haptics)
            VoicePlayer.toggle(url)
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (isMuted) IosColors.TextSecondary else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Waveform(
                seed = url,
                progress = progress,
                isActive = isThisNote,
                onScrub = { VoicePlayer.seek(it) }
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = if (isThisNote) formatVoiceDuration(position) else formatVoiceDuration(total),
                    color = IosColors.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                if (isThisNote) {
                    Text(
                        text = "/ ${formatVoiceDuration(total)}",
                        color = IosColors.TextSecondary.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayButton(isPlaying: Boolean, isPreparing: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(50))
            .background(if (isPlaying) Color.White else IosColors.Accent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val tint = if (isPlaying) Color.Black else Color.White
        when {
            isPreparing -> Box(
                Modifier
                    .size(13.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(tint.copy(alpha = 0.5f))
            )
            isPlaying -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(2) {
                    Box(
                        Modifier
                            .width(4.dp)
                            .height(15.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(tint)
                    )
                }
            }
            else -> Canvas(Modifier.size(16.dp)) {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width * 0.18f, 0f)
                    lineTo(size.width, size.height / 2f)
                    lineTo(size.width * 0.18f, size.height)
                    close()
                }
                drawPath(path, tint)
            }
        }
    }
}

/**
 * The bars, and the scrubber.
 *
 * Dragging seeks. It is the one gesture people try on a voice note they are
 * halfway through and it works everywhere else, so leaving it out reads as the
 * player being broken rather than simple.
 */
@Composable
private fun Waveform(
    seed: String,
    progress: Float,
    isActive: Boolean,
    onScrub: (Float) -> Unit
) {
    val bars = remember(seed) { barsFor(seed) }
    val animated by animateFloatAsState(progress, Motion.quick(), label = "wave")

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(26.dp)
            .then(
                if (!isActive) Modifier else Modifier.pointerInput(seed) {
                    detectHorizontalDragGestures { change, _ ->
                        onScrub(change.position.x / size.width)
                    }
                }
            )
    ) {
        val gap = 3.dp.toPx()
        val barWidth = 2.6.dp.toPx()
        val count = ((size.width + gap) / (barWidth + gap)).toInt().coerceAtLeast(1)
        val played = count * animated

        repeat(count) { index ->
            val height = size.height * bars[index % bars.size]
            val x = index * (barWidth + gap)
            val top = (size.height - height) / 2f
            drawRoundRect(
                color = if (index < played) Color.White else Color.White.copy(alpha = 0.22f),
                topLeft = Offset(x, top),
                size = androidx.compose.ui.geometry.Size(barWidth, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f)
            )
        }
    }
}

/**
 * Bar heights for a note, from its URL.
 *
 * Two sines of unrelated periods, which never line up into a pattern the eye
 * can read as repetition, floored at 0.22 so a quiet stretch still looks like
 * audio rather than a gap.
 */
private fun barsFor(seed: String): List<Float> {
    val base = seed.hashCode().absoluteValue % 997
    return List(48) { index ->
        val a = sin((index + base) * 0.7f)
        val b = sin((index + base) * 0.23f)
        (0.22f + ((a + b + 2f) / 4f) * 0.78f).coerceIn(0.22f, 1f)
    }
}
