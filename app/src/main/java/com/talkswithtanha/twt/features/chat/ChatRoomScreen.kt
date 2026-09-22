package com.talkswithtanha.twt.features.chat

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.talkswithtanha.twt.core.designsystem.Haptics
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.model.ChatMessage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * One room, ported from `TalksWithTanhaView` and `SupportChatView`.
 *
 * Messages are grouped under date dividers, other people's carry an avatar and
 * their name, and the member's own are accent-filled and right-aligned. The
 * support thread gets a header and, while it is empty, a set of prompts — the
 * questions people actually open that thread to ask.
 */
@Composable
fun ChatRoomScreen(
    onBack: () -> Unit,
    viewModel: ChatRoomViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Stick to the newest message. Keyed on the count rather than the list, so
    // an edit or a reaction on an old message does not yank the view to the
    // bottom while somebody is reading history.
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(IosColors.Background)
            .statusBarsPadding()
            .imePadding()
    ) {
        when {
            viewModel.isSellerChat -> SellerHeader(onBack)
            viewModel.isSupportRoom -> SupportHeader(onBack)
            else -> RoomHeader(title = viewModel.roomTitle, onBack = onBack)
        }

        when {
            state.error != null -> Box(Modifier.weight(1f), Alignment.Center) {
                Text(
                    text = state.error!!,
                    color = IosColors.TextSecondary,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp)
                )
            }

            state.messages.isEmpty() && !state.isLoading -> Box(Modifier.weight(1f)) {
                when {
                    viewModel.isSellerChat -> SellerOpening()
                    viewModel.isSupportRoom -> SupportEmptyState(onPrompt = viewModel::onDraftChange)
                    else -> EmptyRoom()
                }
            }

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsWithDateDividers(state.messages)
            }
        }

        // Only while the thread is still opening. Once a conversation is under
        // way the canned lines are noise between the member and the keyboard.
        if (viewModel.isSellerChat && state.messages.isEmpty()) {
            QuickReplyBar(
                rate = state.exchangeRate,
                onSelect = viewModel::useQuickReply
            )
        }

        ChatInputBar(
            draft = state.draft,
            enabled = state.canPost,
            onDraftChange = viewModel::onDraftChange,
            onSend = viewModel::send
        )
    }
}

/**
 * Inserts a divider whenever the day changes.
 *
 * Done at the list level rather than inside the bubble because a divider is not
 * part of a message — it belongs between two of them, and a bubble that draws
 * its own header cannot be reordered or removed without leaving one behind.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.itemsWithDateDividers(
    messages: List<ChatMessage>
) {
    messages.forEachIndexed { index, message ->
        val previous = messages.getOrNull(index - 1)
        if (previous == null || !isSameDay(previous.timestamp, message.timestamp)) {
            item(key = "divider-${message.id}") {
                ChatDateDivider(chatDateLabel(message.timestamp))
            }
        }
        item(key = message.id) { ChatBubble(message) }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    var appeared by remember(message.id) { mutableStateOf(false) }
    LaunchedEffect(message.id) { appeared = true }
    val scale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.6f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "bubble"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = scale
            },
        horizontalArrangement = if (message.isCurrentUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!message.isCurrentUser) {
            Avatar(message.senderName)
            Spacer(Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (message.isCurrentUser) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            if (!message.isCurrentUser) {
                Text(
                    text = message.senderName,
                    color = IosColors.TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)
                )
            }

            Column(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (message.isCurrentUser) IosColors.Accent else Color(0xFF2C2C2E)
                    )
                    .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                message.replyTo?.let { reply ->
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.10f))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            Modifier
                                .width(3.dp)
                                .height(32.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(IosColors.Accent)
                        )
                        Column {
                            Text(
                                text = reply.senderName,
                                color = IosColors.Accent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = reply.text,
                                color = IosColors.TextSecondary,
                                fontSize = 13.sp,
                                maxLines = 2
                            )
                        }
                    }
                }

                if (message.text.isNotEmpty()) {
                    Text(text = message.text, color = Color.White, fontSize = 16.sp)
                }
            }

            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = timeFormat().format(message.timestamp),
                    color = IosColors.TextSecondary,
                    fontSize = 13.sp
                )
                if (message.isEdited) {
                    Text("• edited", color = IosColors.TextSecondary, fontSize = 13.sp)
                }
                if (message.isPending) {
                    // Queued offline. Shown rather than hidden, so a message on
                    // a bad connection does not look like it silently failed.
                    Text("• sending", color = IosColors.TextSecondary, fontSize = 13.sp)
                }
            }
        }
    }
}

/**
 * The sender's initial on a colour picked from their name.
 *
 * Deterministic, so one person keeps one colour down the whole room — the
 * cheapest way to make a busy thread scannable without reading any names.
 */
@Composable
private fun Avatar(name: String) {
    val palette = listOf(
        Color(0xFF0A84FF), Color(0xFF7D4CE0), Color(0xFFFF9F0A),
        Color(0xFFFF2D78), Color(0xFF30B0C7), Color(0xFF5E5CE6)
    )
    val colour = palette[abs(name.hashCode()) % palette.size]

    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(colour),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.take(1).uppercase(),
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ChatDateDivider(label: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(0.5.dp)
                .background(IosColors.TextSecondary.copy(alpha = 0.5f))
        )
        Text(
            text = label,
            color = IosColors.TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        Box(
            Modifier
                .weight(1f)
                .height(0.5.dp)
                .background(IosColors.TextSecondary.copy(alpha = 0.5f))
        )
    }
}

@Composable
private fun RoomHeader(title: String, onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        BackButton(onBack)
        Text(
            text = title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
private fun SupportHeader(onBack: () -> Unit) {
    Column {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            BackButton(onBack)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(IosColors.Gold.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text("T", color = IosColors.Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Column {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Trade with Tanha",
                        color = IosColors.TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        Icons.Filled.Verified,
                        contentDescription = null,
                        tint = IosColors.Accent,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    "Private chat · he answers these himself",
                    color = IosColors.TextSecondary,
                    fontSize = 11.sp
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(Color.White.copy(alpha = 0.08f))
        )
    }
}

/**
 * The marketplace header: Tanha as the verified seller, with the online dot.
 *
 * "Online" is not a presence system — it is what the desk says while it is
 * taking deals, matching `SellerChatHeader` on iOS. If a real presence signal
 * ever exists, this is the one place that should read it.
 */
@Composable
private fun SellerHeader(onBack: () -> Unit) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BackButton(onBack)

            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            androidx.compose.ui.graphics.Brush.linearGradient(
                                listOf(
                                    IosColors.Accent.copy(alpha = 0.35f),
                                    Color(0xFF8C73BF).copy(alpha = 0.25f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("T", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(IosColors.BuyGreen)
                        .border(1.5.dp, IosColors.Background, CircleShape)
                )
            }

            Column(Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Trade with Tanha",
                        color = IosColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        Icons.Filled.Verified,
                        contentDescription = null,
                        tint = IosColors.Accent,
                        modifier = Modifier.size(12.dp)
                    )
                }
                Text("Online", color = IosColors.BuyGreen, fontSize = 11.sp)
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(Color.White.copy(alpha = 0.08f))
        )
    }
}

/** The line the desk opens with, ported from `SellerChatView.openingMessages`. */
@Composable
private fun SellerOpening() {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            text = "Trade with Tanha",
            color = IosColors.TextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)
        )
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF2C2C2E))
                .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = "Assalam o Alaikum! Rates are live in the app — whatever you see is what you get. Let me know the amount.",
                color = Color.White,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun QuickReplyBar(rate: String?, onSelect: (String) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val replies = listOf(
        "What's your best rate right now?",
        rate?.let { "I want to buy $100 at $it" },
        "Is JazzCash available?",
        "How long does release take?"
    ).filterNotNull()

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(replies) { reply ->
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(IosColors.Accent.copy(alpha = 0.10f))
                    .border(0.5.dp, IosColors.Accent.copy(alpha = 0.2f), CircleShape)
                    .clickable {
                        Haptics.tap(haptics)
                        onSelect(reply)
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(reply, color = IosColors.Accent, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(IosColors.SecondaryBackground)
            .clickable {
                Haptics.tap(haptics)
                onBack()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** The questions people actually open this thread to ask. */
private val supportPrompts = listOf(
    "My access code is not working",
    "When does my access expire?",
    "I need to move my access to a new account",
    "How do I renew?"
)

@Composable
private fun SupportEmptyState(onPrompt: (String) -> Unit) {
    val haptics = LocalHapticFeedback.current

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(48.dp))
        Icon(
            Icons.Outlined.Forum,
            contentDescription = null,
            tint = IosColors.Gold.copy(alpha = 0.8f),
            modifier = Modifier.size(56.dp)
        )
        Text(
            "Ask him anything",
            color = IosColors.TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            "This goes straight to Tanha and nobody else can see it. Your access code, your account, or a signal you did not follow.",
            color = IosColors.TextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        supportPrompts.forEach { prompt ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF141414))
                    .clickable {
                        Haptics.tap(haptics)
                        // Fills the box rather than sending. The member almost
                        // always wants to add a sentence of their own.
                        onPrompt(prompt)
                    }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = prompt,
                    color = IosColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Filled.ArrowUpward,
                    contentDescription = null,
                    tint = IosColors.TextSecondary,
                    modifier = Modifier
                        .size(14.dp)
                        .graphicsLayer { rotationZ = -45f }
                )
            }
        }
    }
}

@Composable
private fun EmptyRoom() {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(80.dp))
        Icon(
            Icons.Outlined.Forum,
            contentDescription = null,
            tint = IosColors.TextSecondary.copy(alpha = 0.5f),
            modifier = Modifier.size(44.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text("Nothing here yet", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text("Be the first to post.", color = IosColors.TextSecondary, fontSize = 14.sp)
    }
}

@Composable
private fun ChatInputBar(
    draft: String,
    enabled: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val canSend = enabled && draft.isNotBlank()

    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF1C1C1E))
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0xFF2C2C2E)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.AttachFile,
                contentDescription = "Attach",
                tint = IosColors.TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }

        Box(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF2C2C2E))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            BasicTextField(
                value = draft,
                onValueChange = onDraftChange,
                enabled = enabled,
                maxLines = 5,
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                cursorBrush = SolidColor(IosColors.Accent),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (draft.isEmpty()) {
                        Text(
                            text = if (enabled) "Message..." else "You cannot post in this room.",
                            color = IosColors.TextSecondary,
                            fontSize = 16.sp
                        )
                    }
                    inner()
                }
            )
        }

        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (canSend) IosColors.Accent else Color(0xFF2C2C2E))
                .clickable(enabled = canSend) {
                    Haptics.tap(haptics)
                    onSend()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.ArrowUpward,
                contentDescription = "Send",
                tint = if (canSend) Color.White else IosColors.TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun timeFormat() = SimpleDateFormat("h:mm a", Locale.getDefault())

private fun isSameDay(a: Date, b: Date): Boolean {
    val first = Calendar.getInstance().apply { time = a }
    val second = Calendar.getInstance().apply { time = b }
    return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
        first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
}

private fun chatDateLabel(date: Date): String {
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    return when {
        isSameDay(date, today.time) -> "Today"
        isSameDay(date, yesterday.time) -> "Yesterday"
        else -> SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(date)
    }
}
