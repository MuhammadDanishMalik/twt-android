package com.talkswithtanha.twt.features.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.features.settings.SettingsCard
import com.talkswithtanha.twt.features.settings.SettingsDivider
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.talkswithtanha.twt.core.designsystem.Radius
import com.talkswithtanha.twt.core.designsystem.Spacing
import com.talkswithtanha.twt.core.designsystem.TwtColors
import com.talkswithtanha.twt.core.designsystem.components.EmptyState
import com.talkswithtanha.twt.core.designsystem.components.TwtCard
import com.talkswithtanha.twt.core.designsystem.components.TwtScreen
import com.talkswithtanha.twt.core.model.ChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The Chats tab, grouped the way iOS groups it: the two community rooms and the
 * private thread with Tanha, then anything that came out of the marketplace.
 */
@Composable
fun ChatListScreen(
    onOpenRoom: (String) -> Unit,
    onOpenMarketplace: () -> Unit,
    viewModel: ChatListViewModel = hiltViewModel()
) {
    val rooms by viewModel.rooms.collectAsStateWithLifecycle()
    val user by viewModel.user.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(IosColors.Background)
            .statusBarsPadding()
    ) {
        Text(
            text = "Chats",
            color = Color.White,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 16.dp)
        )

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp),
            modifier = Modifier.padding(bottom = 100.dp)
        ) {
            item { ChatSectionHeader("Community") }

            item {
                SettingsCard {
                    rooms.forEachIndexed { index, room ->
                        val locked = room.requiresAccess && user?.hasAppAccess != true
                        ChatRoomRow(
                            title = room.title,
                            subtitle = room.subtitle,
                            initials = room.initials,
                            icon = room.icon,
                            locked = locked,
                            crowned = room.crowned
                        ) {
                            if (!locked) onOpenRoom(room.roomId)
                        }
                        if (index < rooms.lastIndex) {
                            SettingsDivider(76.dp)
                        }
                    }
                }
            }

            item { ChatSectionHeader("From the Marketplace") }

            item {
                SettingsCard {
                    ChatRoomRow(
                        title = "Trade with Tanha",
                        subtitle = "Buy and sell dollars",
                        initials = "TW",
                        icon = null,
                        locked = false,
                        crowned = false,
                        onClick = onOpenMarketplace
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatSectionHeader(title: String) {
    Text(
        text = title,
        color = IosColors.TextSecondary,
        fontSize = 15.sp,
        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
    )
}

/**
 * One row. An emblem on the left — a glyph for a room, initials for a person —
 * then the name, then a lock if the member's code does not reach it.
 */
@Composable
private fun ChatRoomRow(
    title: String,
    subtitle: String,
    initials: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    locked: Boolean,
    crowned: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0xFF2C2C2E)),
            contentAlignment = Alignment.Center
        ) {
            when {
                icon != null -> Icon(
                    icon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(22.dp)
                )
                else -> Text(
                    text = initials.orEmpty(),
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Column(Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = if (locked) IosColors.TextSecondary else Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (crowned) {
                    Icon(
                        Icons.Filled.WorkspacePremium,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Text(
                text = subtitle,
                color = IosColors.TextSecondary,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (locked) {
            Icon(
                Icons.Outlined.Lock,
                contentDescription = "Locked",
                tint = IosColors.TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun ChatRoomScreen(
    onBack: () -> Unit,
    viewModel: ChatRoomViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Stick to the newest message. `size` rather than the list itself, so an
    // edit or a reaction on an old message does not yank the view to the bottom
    // while somebody is reading history.
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    TwtScreen {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TwtColors.TextPrimary
                    )
                }
                Text(
                    text = if (viewModel.isSupportRoom) "Talks with Tanha" else "Community",
                    style = MaterialTheme.typography.titleLarge,
                    color = TwtColors.TextPrimary
                )
            }

            when {
                state.error != null -> Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.error!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TwtColors.TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(Spacing.xl)
                    )
                }

                state.messages.isEmpty() && !state.isLoading -> Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        icon = Icons.Outlined.Forum,
                        title = if (viewModel.isSupportRoom) "Say hello" else "Nothing here yet",
                        message = if (viewModel.isSupportRoom) {
                            "This thread is just you and Tanha."
                        } else {
                            "Be the first to post."
                        }
                    )
                }

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        MessageBubble(message)
                    }
                }
            }

            ChatInputBar(
                draft = state.draft,
                enabled = state.canPost,
                onDraftChange = viewModel::onDraftChange,
                onSend = viewModel::send
            )
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val mine = message.isCurrentUser
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start
    ) {
        if (!mine) {
            Text(
                text = message.senderName,
                style = MaterialTheme.typography.labelMedium,
                color = TwtColors.TextTertiary,
                modifier = Modifier.padding(start = Spacing.sm, bottom = Spacing.xxs)
            )
        }
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = Radius.chip,
                        topEnd = Radius.chip,
                        bottomStart = if (mine) Radius.chip else 4.dp,
                        bottomEnd = if (mine) 4.dp else Radius.chip
                    )
                )
                .background(if (mine) TwtColors.GoldWash else TwtColors.Surface)
                .border(
                    0.5.dp,
                    if (mine) TwtColors.Gold.copy(alpha = 0.25f) else TwtColors.Hairline,
                    RoundedCornerShape(Radius.chip)
                )
                .padding(horizontal = Spacing.md, vertical = Spacing.sm)
        ) {
            message.replyTo?.let { reply ->
                Text(
                    text = reply.senderName,
                    style = MaterialTheme.typography.labelSmall,
                    color = TwtColors.Gold
                )
                Text(
                    text = reply.text,
                    style = MaterialTheme.typography.labelMedium,
                    color = TwtColors.TextTertiary,
                    maxLines = 2
                )
                Spacer(Modifier.height(Spacing.xs))
            }
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyLarge,
                color = TwtColors.TextPrimary
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTime(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = TwtColors.TextTertiary
                )
                if (message.isEdited) {
                    Text(
                        text = "edited",
                        style = MaterialTheme.typography.labelSmall,
                        color = TwtColors.TextTertiary
                    )
                }
                if (message.isPending) {
                    // Sent, not yet acknowledged by the server. Shown rather
                    // than hidden so a message queued offline does not look
                    // like it silently failed.
                    Text(
                        text = "sending",
                        style = MaterialTheme.typography.labelSmall,
                        color = TwtColors.TextTertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    draft: String,
    enabled: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.md),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            enabled = enabled,
            placeholder = {
                Text(
                    text = if (enabled) "Message" else "You cannot post in this room.",
                    color = TwtColors.TextTertiary
                )
            },
            maxLines = 4,
            shape = RoundedCornerShape(Radius.card),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TwtColors.Gold,
                unfocusedBorderColor = TwtColors.HairlineStrong,
                disabledBorderColor = TwtColors.Hairline,
                focusedTextColor = TwtColors.TextPrimary,
                unfocusedTextColor = TwtColors.TextPrimary,
                cursorColor = TwtColors.Gold
            ),
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = onSend,
            enabled = enabled && draft.isNotBlank(),
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(Radius.card))
                .background(if (enabled && draft.isNotBlank()) TwtColors.Gold else TwtColors.Surface)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send",
                tint = if (enabled && draft.isNotBlank()) TwtColors.Background else TwtColors.TextTertiary
            )
        }
    }
}

/**
 * Built per call, not held in a `val`.
 *
 * A formatter created once at class initialisation captures whatever locale was
 * current then and keeps it for the life of the process — so a member who
 * changes their phone's language goes on seeing timestamps in the old one until
 * they force-quit. `SimpleDateFormat` is also not thread-safe, which a shared
 * instance quietly ignores.
 */
private fun formatTime(date: Date): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
