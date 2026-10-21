package com.talkswithtanha.twt.features.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.outlined.Flag
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talkswithtanha.twt.core.designsystem.Haptics
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.model.ChatMessage
import com.talkswithtanha.twt.core.moderation.ReportReason

/**
 * What a member can do about someone else's message: report it to the team,
 * or block its author. Google Play requires both of any app with user
 * conversations; they sit behind a long-press, where people look for them.
 */
private val QUICK_REACTIONS = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageActionsSheet(
    message: ChatMessage,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onReact: (String) -> Unit,
    onReport: (ReportReason) -> Unit,
    onBlock: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = LocalHapticFeedback.current
    var choosingReason by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = Color(0xFF1C1C1E),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            // The message being acted on, so there is no doubt which one.
            Column(
                Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .background(Color(0xFF2C2C2E), RoundedCornerShape(14.dp))
                    .padding(12.dp)
            ) {
                Text(message.senderName, color = IosColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(
                    message.text.ifEmpty { message.noteAttachment?.noteText.orEmpty() },
                    color = Color.White,
                    fontSize = 15.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(14.dp))

            // WhatsApp's six. One per member: the ringed one is theirs, and
            // tapping it again takes it back.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                QUICK_REACTIONS.forEach { emoji ->
                    val mine = message.myReaction == emoji
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(if (mine) IosColors.Accent.copy(alpha = 0.28f) else Color(0xFF2C2C2E))
                            .clickable {
                                Haptics.success(haptics)
                                onReact(emoji)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(emoji, fontSize = 24.sp)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            AnimatedContent(targetState = choosingReason, label = "moderation") { reasons ->
                Column {
                    if (!reasons) {
                        ActionRow(Icons.AutoMirrored.Filled.Reply, "Reply", null) {
                            Haptics.tap(haptics)
                            onReply()
                        }
                        if (message.text.isNotBlank()) {
                            ActionRow(Icons.Outlined.ContentCopy, "Copy", null) {
                                Haptics.tap(haptics)
                                onCopy()
                            }
                        }
                        onEdit?.let { edit ->
                            ActionRow(Icons.Outlined.Edit, "Edit", "Within 5 minutes of sending") {
                                Haptics.tap(haptics)
                                edit()
                            }
                        }
                        onDelete?.let { del ->
                            ActionRow(Icons.Outlined.Delete, "Delete", "Removed for everyone", destructive = true) {
                                Haptics.toggleOn(haptics)
                                del()
                            }
                        }
                    }
                    if (!reasons && !message.isCurrentUser) {
                        ActionRow(Icons.Outlined.Flag, "Report message", "The team reviews every report") {
                            Haptics.tap(haptics)
                            choosingReason = true
                        }
                        ActionRow(
                            Icons.Outlined.Block,
                            "Block ${message.senderName}",
                            "Hide their messages. They are not told.",
                            destructive = true
                        ) {
                            Haptics.toggleOn(haptics)
                            onBlock()
                        }
                    } else if (reasons) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { choosingReason = false }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = IosColors.TextSecondary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Why are you reporting this?", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                        ReportReason.entries.forEach { reason ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        Haptics.success(haptics)
                                        onReport(reason)
                                    }
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(reason.title, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    null,
                                    tint = IosColors.TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    val tint = if (destructive) IosColors.SellRed else Color.White
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, color = tint, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            subtitle?.let { Text(it, color = IosColors.TextSecondary, fontSize = 13.sp) }
        }
    }
}
