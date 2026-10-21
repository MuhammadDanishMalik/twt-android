package com.talkswithtanha.twt.features.chat

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talkswithtanha.twt.core.designsystem.Haptics
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.model.ChatMessage
import kotlinx.coroutines.delay

// ── Rich text ────────────────────────────────────────────────────────────────
//
// Signals Tanha shares from the admin come as **bold** headings with links;
// shown raw they were a wall of asterisks and dead URLs.

private val LINK = Regex("""https?://[^\s)]+""")
private val BOLD = Regex("""\*\*(.+?)\*\*""")
const val FORWARDED_PREFIX = "↪️ Forwarded\n"

/** `**bold**` becomes bold and every link can be tapped. */
fun richText(text: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val bold = BOLD.find(text, i)
        val link = LINK.find(text, i)
        val next = listOfNotNull(bold, link).minByOrNull { it.range.first }
        if (next == null) {
            append(text.substring(i))
            break
        }
        append(text.substring(i, next.range.first))
        if (next === bold) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(next.groupValues[1]) }
        } else {
            withLink(
                LinkAnnotation.Url(
                    next.value,
                    TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
                )
            ) { append(next.value) }
        }
        i = next.range.last + 1
    }
}

/** "Forwarded" in small italics, as WhatsApp marks a passed-on message. */
@Composable
fun ForwardedLabel() {
    Text("↪ Forwarded", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, fontStyle = FontStyle.Italic)
}

// ── Voice notes ──────────────────────────────────────────────────────────────

private val WAVE = listOf(30, 55, 85, 60, 35, 95, 75, 45, 65, 90, 35, 55, 75, 100, 45, 30, 65, 85, 55, 35, 70, 90, 45, 25)

/**
 * Plays a voice note in place: play/pause, a waveform that fills as it
 * plays, the time left, and 1x / 1.5x / 2x. Streams from the link; nothing is
 * downloaded until the member taps play.
 */
@Composable
fun VoiceNotePlayer(url: String, durationSecs: Int, isMine: Boolean) {
    val haptics = LocalHapticFeedback.current
    var player by remember(url) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(url) { mutableStateOf(false) }
    var preparing by remember(url) { mutableStateOf(false) }
    var progress by remember(url) { mutableFloatStateOf(0f) }
    var speed by remember(url) { mutableFloatStateOf(1f) }
    var failed by remember(url) { mutableStateOf(false) }

    DisposableEffect(url) {
        onDispose {
            player?.release()
            player = null
        }
    }

    LaunchedEffect(playing) {
        while (playing) {
            player?.let { p ->
                if (p.duration > 0) progress = p.currentPosition.toFloat() / p.duration
            }
            delay(100)
        }
    }

    fun applySpeed(p: MediaPlayer) {
        runCatching { p.playbackParams = p.playbackParams.setSpeed(speed) }
    }

    fun toggle() {
        Haptics.tap(haptics)
        val existing = player
        when {
            existing == null -> {
                preparing = true
                failed = false
                val p = MediaPlayer()
                p.setAudioAttributes(
                    AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
                )
                runCatching {
                    p.setDataSource(url)
                    p.setOnPreparedListener {
                        preparing = false
                        applySpeed(it)
                        it.start()
                        playing = true
                    }
                    p.setOnCompletionListener {
                        playing = false
                        progress = 0f
                    }
                    p.setOnErrorListener { _, _, _ ->
                        preparing = false
                        playing = false
                        failed = true
                        true
                    }
                    p.prepareAsync()
                }.onFailure {
                    preparing = false
                    failed = true
                }
                player = p
            }
            playing -> {
                existing.pause()
                playing = false
            }
            else -> {
                applySpeed(existing)
                existing.start()
                playing = true
            }
        }
    }

    val accent = if (isMine) Color.White else IosColors.Accent
    val total = if (durationSecs > 0) durationSecs else ((player?.duration ?: 0) / 1000)
    val remaining = if (playing || progress > 0f) (total * (1 - progress)).toInt() else total

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.widthIn(min = 220.dp)
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isMine) Color.White.copy(alpha = 0.22f) else IosColors.Accent)
                .clickable { toggle() },
            contentAlignment = Alignment.Center
        ) {
            if (preparing) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            } else {
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "Pause voice note" else "Play voice note",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Column(Modifier.weight(1f)) {
            Row(
                Modifier.height(26.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WAVE.forEachIndexed { i, h ->
                    val played = i.toFloat() / WAVE.size < progress
                    Box(
                        Modifier
                            .width(3.dp)
                            .height((h / 100f * 24).dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (played) accent else Color.White.copy(alpha = 0.35f))
                    )
                }
            }
            Text(
                if (failed) "Could not play — tap to retry" else "%d:%02d".format(remaining / 60, remaining % 60),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp
            )
        }

        Text(
            "${if (speed % 1f == 0f) speed.toInt() else speed}x",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.15f))
                .clickable {
                    speed = when (speed) { 1f -> 1.5f; 1.5f -> 2f; else -> 1f }
                    player?.let { if (playing) applySpeed(it) }
                }
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

// ── Announcement & appreciation ──────────────────────────────────────────────

@Composable
fun AnnouncementTag() {
    Text(
        "📢  ANNOUNCEMENT",
        color = Color(0xFFFBBF24),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp
    )
}

@Composable
fun AppreciationCard(message: ChatMessage) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFFBBF24).copy(alpha = 0.14f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("🌟", fontSize = 26.sp)
        Column {
            Text(
                message.targetName?.let { "Shout-out to $it" } ?: "Shout-out",
                color = Color(0xFFFBBF24),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(message.text, color = Color.White, fontSize = 14.sp)
        }
    }
}

// ── Pinned bar ───────────────────────────────────────────────────────────────

/**
 * The newest pin under the room header; with several, "Pinned message 2 of 3"
 * and a tap moves to the next older one and jumps the chat to it.
 */
@Composable
fun PinnedBar(pins: List<ChatMessage>, shownIndex: Int, onTap: (ChatMessage) -> Unit, onClose: () -> Unit) {
    if (pins.isEmpty()) return
    val shown = pins[pins.lastIndex - (shownIndex % pins.size)]
    val number = pins.indexOf(shown) + 1
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF1C1C1E))
            .clickable { onTap(shown) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.width(3.dp).height(32.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            pins.takeLast(4).forEach {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (it.id == shown.id) IosColors.Accent else IosColors.Accent.copy(alpha = 0.3f))
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (pins.size > 1) "Pinned message $number of ${pins.size}" else "Pinned message",
                color = IosColors.Accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                shown.text.removePrefix(FORWARDED_PREFIX).ifBlank { "Voice note" },
                color = Color.White,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(Icons.Outlined.PushPin, null, tint = IosColors.TextSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Icon(
            Icons.Filled.Close,
            contentDescription = "Hide pinned messages",
            tint = IosColors.TextSecondary,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable { onClose() }
                .padding(5.dp)
        )
    }
}

// ── Reply / edit banner above the input ──────────────────────────────────────

@Composable
fun ComposeBanner(editing: Boolean, message: ChatMessage, onCancel: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF1C1C1E))
            .padding(start = 16.dp, end = 8.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (editing) Icons.Filled.Edit else Icons.AutoMirrored.Filled.Reply,
            null,
            tint = IosColors.Accent,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Box(Modifier.width(3.dp).height(34.dp).clip(RoundedCornerShape(2.dp)).background(IosColors.Accent))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (editing) "Edit message" else "Replying to ${message.senderName}",
                color = IosColors.Accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                message.text.removePrefix(FORWARDED_PREFIX).ifBlank { "Voice note" },
                color = IosColors.TextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            Icons.Filled.Close,
            contentDescription = if (editing) "Cancel edit" else "Cancel reply",
            tint = IosColors.TextSecondary,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable { onCancel() }
                .padding(8.dp)
        )
    }
}
