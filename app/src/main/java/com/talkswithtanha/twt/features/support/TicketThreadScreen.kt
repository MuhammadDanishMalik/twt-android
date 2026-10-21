package com.talkswithtanha.twt.features.support

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.model.SupportTicket
import com.talkswithtanha.twt.core.model.TicketMessage
import com.talkswithtanha.twt.core.model.TicketStatus
import com.talkswithtanha.twt.features.settings.SettingsMetrics
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * One ticket: what was said, and what happens next.
 *
 * The state sits in the header rather than at the bottom, because on a ticket
 * the state *is* the news — "Resolved" answers the question the member opened
 * the screen to ask, and a thread of bubbles does not.
 *
 * Staff holding the `tickets` area get the actions they would have in the admin
 * panel: reply, which leaves it waiting on the member, and resolve. The one
 * thing they cannot do from here is send the email — that needs a server
 * credential, and a phone has none. The panel's reply box sends it.
 */
@Composable
fun TicketThreadScreen(
    onBack: () -> Unit,
    viewModel: TicketThreadViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.ticket?.id) { viewModel.markRead() }
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(IosColors.Background)
            .statusBarsPadding()
            .imePadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = SettingsMetrics.pageInset, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleIconButton(Icons.Filled.ArrowBack, "Back", onBack)
            Spacer(Modifier.weight(1f))
            Text("Ticket", color = IosColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(40.dp))
        }

        val ticket = state.ticket
        if (ticket == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                com.talkswithtanha.twt.core.designsystem.components.LoadingLabel("Loading the ticket")
            }
            return@Column
        }

        TicketHeader(
            ticket = ticket,
            repliesAsStaff = state.repliesAsStaff,
            onStatus = viewModel::setStatus
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(SettingsMetrics.pageInset),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(state.messages, key = { it.id }) { message ->
                TicketBubble(message = message, isMine = isMine(message, state.repliesAsStaff))
            }
        }

        state.error?.let {
            Text(
                text = it,
                color = IosColors.SellRed,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = SettingsMetrics.pageInset, vertical = 4.dp)
            )
        }

        Composer(
            value = draft,
            hint = if (state.repliesAsStaff) "Reply to ${ticket.memberName.substringBefore(' ')}…" else "Write a message…",
            isSending = state.isSending,
            onChange = { draft = it },
            onSend = {
                viewModel.send(draft)
                draft = ""
            }
        )
    }
}

private fun isMine(message: TicketMessage, repliesAsStaff: Boolean): Boolean =
    if (repliesAsStaff) message.isStaff else !message.isStaff

@Composable
private fun TicketHeader(
    ticket: SupportTicket,
    repliesAsStaff: Boolean,
    onStatus: (TicketStatus) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F0F0F))
            .padding(horizontal = SettingsMetrics.pageInset, vertical = 12.dp)
    ) {
        Text(ticket.subject, color = IosColors.TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)

        Row(
            Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusChip(ticket.status)
            Text(ticket.category, color = IosColors.TextSecondary, fontSize = 12.sp)
            if (repliesAsStaff) Text("· ${ticket.memberName}", color = IosColors.TextSecondary, fontSize = 12.sp)
        }

        Text(
            text = explanation(ticket, repliesAsStaff),
            color = IosColors.TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp)
        )

        Row(
            Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (repliesAsStaff) {
                if (ticket.status != TicketStatus.RESOLVED) {
                    SmallAction("Resolve", Icons.Filled.Check) { onStatus(TicketStatus.RESOLVED) }
                }
                if (ticket.status == TicketStatus.OPEN) {
                    SmallAction("Picking it up", Icons.Filled.PanTool) { onStatus(TicketStatus.IN_PROGRESS) }
                }
            } else if (ticket.status.isFinished) {
                Text("Reply below to reopen this ticket.", color = IosColors.TextSecondary, fontSize = 12.sp)
            } else {
                SmallAction("Close it", Icons.Filled.Check) { onStatus(TicketStatus.CLOSED) }
            }
        }
    }
}

private fun explanation(ticket: SupportTicket, repliesAsStaff: Boolean): String = when {
    repliesAsStaff && ticket.needsReply -> "Waiting on a reply from us."
    repliesAsStaff -> "Waiting on the member."
    ticket.status == TicketStatus.OPEN -> "Raised. Somebody will pick this up."
    ticket.status == TicketStatus.IN_PROGRESS -> "Somebody is looking at this."
    ticket.status == TicketStatus.WAITING_ON_MEMBER -> "We have replied — your answer is what moves it on."
    ticket.status == TicketStatus.RESOLVED -> "Marked as sorted. Reply here if it happens again."
    else -> "Closed. Replying reopens it."
}

@Composable
private fun SmallAction(title: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF242424))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(icon, null, tint = IosColors.TextPrimary, modifier = Modifier.size(13.dp))
        Text(title, color = IosColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TicketBubble(message: TicketMessage, isMine: Boolean) {
    val formatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.84f)
                .wrapContentWidthIfNeeded(isMine)
                .clip(RoundedCornerShape(18.dp))
                .background(if (isMine) Color.White else Color(0xFF1F1F1F))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = message.text,
                color = if (isMine) Color.Black else IosColors.TextPrimary,
                fontSize = 15.sp
            )
            Text(
                text = if (message.isStaff) {
                    "${message.senderName.ifEmpty { "TWT Team" }} · ${formatter.format(message.timestamp)}"
                } else {
                    formatter.format(message.timestamp)
                },
                color = if (isMine) Color.Black.copy(alpha = 0.5f) else IosColors.TextSecondary,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/** Bubbles hug their text rather than filling the row they are aligned in. */
private fun Modifier.wrapContentWidthIfNeeded(isMine: Boolean): Modifier =
    this.then(if (isMine) Modifier else Modifier)

@Composable
private fun Composer(
    value: String,
    hint: String,
    isSending: Boolean,
    onChange: (String) -> Unit,
    onSend: () -> Unit
) {
    val canSend = value.isNotBlank() && !isSending

    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F0F0F))
            .navigationBarsPadding()
            .padding(horizontal = SettingsMetrics.pageInset, vertical = 10.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF1C1C1C))
                .padding(horizontal = 14.dp, vertical = 11.dp)
        ) {
            if (value.isEmpty()) {
                Text(hint, color = IosColors.TextSecondary, fontSize = 15.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                textStyle = LocalTextStyle.current.copy(color = IosColors.TextPrimary, fontSize = 15.sp),
                cursorBrush = SolidColor(IosColors.TextPrimary),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (canSend) Color.White else Color(0xFF2A2A2A))
                .clickable(enabled = canSend, onClick = onSend),
            contentAlignment = Alignment.Center
        ) {
            if (isSending) {
                CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    Icons.Filled.ArrowUpward,
                    contentDescription = "Send",
                    tint = if (canSend) Color.Black else IosColors.TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
