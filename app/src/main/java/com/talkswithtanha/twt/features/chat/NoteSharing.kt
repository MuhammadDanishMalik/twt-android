package com.talkswithtanha.twt.features.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talkswithtanha.twt.core.designsystem.Haptics
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.designsystem.RiseIn
import com.talkswithtanha.twt.core.model.ChatMessage
import com.talkswithtanha.twt.core.notes.VideoNote

/**
 * "Share a Note": every saved lesson note, grouped by lesson — the Android
 * half of iOS's `NoteAttachmentPickerSheet`. Tapping one attaches it to the
 * message being written.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotePickerSheet(
    notes: List<VideoNote>,
    onPick: (VideoNote) -> Unit,
    onDismiss: () -> Unit
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val haptics = LocalHapticFeedback.current
    // Lessons in the order they were first noted on; notes in video order.
    val groups = notes
        .sortedBy { it.createdAt }
        .groupBy { it.videoId }
        .map { (_, list) -> list.first().videoTitle.ifBlank { "Lesson" } to list.sortedBy { it.timestamp } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = Color(0xFF1C1C1E),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Share a Note", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${notes.size} saved note${if (notes.size == 1) "" else "s"}",
                        color = IosColors.TextSecondary,
                        fontSize = 13.sp
                    )
                }
                Icon(
                    Icons.Filled.Cancel,
                    contentDescription = "Close",
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .clickable {
                            Haptics.sheetClose(haptics)
                            onDismiss()
                        }
                )
            }
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.1f)))

            if (notes.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Outlined.EditNote,
                        contentDescription = null,
                        tint = IosColors.Accent.copy(alpha = 0.45f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("No Notes Saved", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Open a lesson in the Academy and add notes while\nyou watch — they'll appear here.",
                        color = IosColors.TextSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                var flatIndex = 0
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                    groups.forEach { (title, list) ->
                        item(key = "header-${list.first().videoId}") {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF232325))
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.PlayCircle,
                                    contentDescription = null,
                                    tint = IosColors.Accent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    title,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "${list.size}",
                                    color = IosColors.Accent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(IosColors.Accent.copy(alpha = 0.12f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        items(list, key = { it.id }) { note ->
                            val delay = 80 + (flatIndex++).coerceAtMost(12) * 40
                            RiseIn(delayMillis = delay, distance = 12.dp) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            Haptics.toggleOn(haptics)
                                            onPick(note)
                                        }
                                        .padding(horizontal = 20.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    TimestampPill(note.formattedTimestamp, onAccent = false)
                                    Text(
                                        note.text,
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        lineHeight = 20.sp,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f).padding(top = 2.dp)
                                    )
                                    Icon(
                                        Icons.Filled.ArrowCircleUp,
                                        contentDescription = "Share",
                                        tint = IosColors.Accent.copy(alpha = 0.75f),
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** The note waiting above the input box, with a way to take it back off. */
@Composable
fun PendingNoteBanner(note: ChatMessage.NoteAttachment, onCancel: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF1C1C1E))
            .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.StickyNote2,
            contentDescription = null,
            tint = IosColors.Accent,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TimestampPill(note.formattedTimestamp, onAccent = false, small = true)
                Spacer(Modifier.width(6.dp))
                Text(
                    note.videoTitle,
                    color = IosColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                note.noteText,
                color = IosColors.TextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            Icons.Filled.Cancel,
            contentDescription = "Remove note",
            tint = Color.White.copy(alpha = 0.3f),
            modifier = Modifier
                .padding(start = 8.dp)
                .size(26.dp)
                .clip(CircleShape)
                .clickable {
                    Haptics.toggleOff(haptics)
                    onCancel()
                }
        )
    }
}

/**
 * A shared note inside a chat bubble: which lesson, when in it, and what was
 * written. Tapping it opens the lesson at that moment.
 */
@Composable
fun NoteAttachmentCard(
    note: ChatMessage.NoteAttachment,
    isMine: Boolean,
    onOpen: () -> Unit
) {
    val tint = if (isMine) Color.White.copy(alpha = 0.85f) else IosColors.Accent
    Column(
        Modifier
            .clip(RoundedCornerShape(13.dp))
            .background(if (isMine) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.06f))
            .clickable(onClick = onOpen)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.AutoMirrored.Outlined.StickyNote2,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                note.videoTitle.ifBlank { "Lesson note" },
                color = tint,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(8.dp))
            TimestampPill(note.formattedTimestamp, onAccent = isMine, small = true)
        }
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(2.5.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isMine) Color.White.copy(alpha = 0.6f) else IosColors.Accent)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                note.noteText,
                color = if (isMine) Color.White.copy(alpha = 0.92f) else Color.White,
                fontSize = 14.sp,
                lineHeight = 19.sp
            )
        }
    }
}

@Composable
private fun TimestampPill(text: String, onAccent: Boolean, small: Boolean = false) {
    Text(
        text,
        color = if (onAccent) Color.White else IosColors.Accent,
        fontSize = if (small) 11.sp else 13.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (onAccent) Color.White.copy(alpha = 0.22f) else IosColors.Accent.copy(alpha = 0.13f))
            .padding(horizontal = if (small) 7.dp else 10.dp, vertical = if (small) 2.dp else 5.dp)
    )
}
