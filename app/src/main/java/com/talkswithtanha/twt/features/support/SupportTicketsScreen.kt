package com.talkswithtanha.twt.features.support

import com.talkswithtanha.twt.core.designsystem.WebFonts
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.designsystem.Spacing
import com.talkswithtanha.twt.core.designsystem.components.EmptyState
import com.talkswithtanha.twt.core.designsystem.components.SegmentedControl
import com.talkswithtanha.twt.core.designsystem.staggeredAppear
import com.talkswithtanha.twt.core.model.SupportTicket
import com.talkswithtanha.twt.core.model.TicketStatus
import com.talkswithtanha.twt.features.settings.SettingsMetrics
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * The member's support tickets: what they have raised, where each one stands,
 * and the way to raise another.
 *
 * A list rather than a thread, because a ticket is not a conversation — it is a
 * problem with a state. The state is the first thing on each row for that
 * reason: "Your turn" is an instruction, and burying it under a preview of the
 * last message is how support systems end up waiting on people who never knew
 * they were being waited on.
 *
 * Staff holding the `tickets` area get a second tab: the whole queue, which is
 * what lets Tanha answer a member from a bus. It only appears for accounts the
 * security rules would actually serve — showing a tab that errors is worse than
 * not showing it.
 */
@Composable
fun SupportTicketsScreen(
    onBack: () -> Unit,
    onOpenTicket: (String) -> Unit,
    onCompose: () -> Unit,
    viewModel: SupportTicketsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var scope by remember { mutableIntStateOf(0) }

    val tickets = if (scope == 1 && state.canWorkQueue) state.queue else state.mine

    Column(
        Modifier
            .fillMaxSize()
            .background(IosColors.Background)
            .statusBarsPadding()
    ) {
        TicketsTopBar(onBack = onBack, onCompose = onCompose)

        LazyColumn(
            contentPadding = PaddingValues(
                start = SettingsMetrics.pageInset,
                end = SettingsMetrics.pageInset,
                bottom = 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column(Modifier.padding(bottom = Spacing.sm)) {
                    Text(
                        text = if (scope == 1) "Member tickets" else "Your tickets",
                        color = IosColors.TextPrimary,
                        fontSize = 28.sp,
                        fontFamily = WebFonts.Display,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (scope == 1) {
                            "Everything members have raised. Replying here is the same as replying in the panel."
                        } else {
                            "Raise a problem and it stays here until it is sorted. We email you whenever it moves."
                        },
                        color = IosColors.TextSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            if (state.canWorkQueue) {
                item {
                    SegmentedControl(
                        options = listOf("My tickets", "Queue"),
                        selectedIndex = scope,
                        onSelect = { scope = it },
                        modifier = Modifier.padding(bottom = Spacing.sm)
                    )
                }
            }

            state.error?.let { message ->
                item {
                    Text(
                        text = message,
                        color = IosColors.SellRed,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = Spacing.sm)
                    )
                }
            }

            if (tickets.isEmpty() && !state.isLoading) {
                item {
                    EmptyState(
                        icon = Icons.Filled.SupportAgent,
                        title = if (scope == 1) "Nothing in the queue" else "No tickets",
                        message = if (scope == 1) {
                            "Tickets members raise in the app arrive here."
                        } else {
                            "Something not working? Raise a ticket and it will not get lost."
                        }
                    )
                }
            }

            items(tickets, key = { it.id }) { ticket ->
                TicketCard(
                    ticket = ticket,
                    showsMember = scope == 1 && state.canWorkQueue,
                    onClick = { onOpenTicket(ticket.id) },
                    modifier = Modifier.staggeredAppear(tickets.indexOf(ticket).coerceAtMost(6))
                )
            }
        }
    }
}

@Composable
private fun TicketsTopBar(onBack: () -> Unit, onCompose: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = SettingsMetrics.pageInset, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircleIconButton(Icons.Filled.ArrowBack, "Back", onBack)
        Spacer(Modifier.weight(1f))
        Text("Support", color = IosColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        CircleIconButton(Icons.Filled.Add, "New ticket", onCompose)
    }
}

@Composable
internal fun CircleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(IosColors.SecondaryBackground)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, tint = IosColors.TextPrimary, modifier = Modifier.size(18.dp))
    }
}

@Composable
internal fun TicketCard(
    ticket: SupportTicket,
    showsMember: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF141414))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = ticket.subject,
                color = IosColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            // One dot, and which one depends on who is looking: the member sees
            // "they replied", staff see "this one is waiting on us".
            val dot = when {
                showsMember && ticket.needsReply -> IosColors.SellRed
                !showsMember && ticket.unreadForMember -> IosColors.BuyGreen
                else -> null
            }
            dot?.let {
                Box(
                    Modifier
                        .padding(start = 8.dp, top = 5.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(it)
                )
            }
        }

        Text(
            text = ticket.lastMessage.ifEmpty { "No messages yet" },
            color = IosColors.TextSecondary,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusChip(ticket.status)
            Text(ticket.category, color = IosColors.TextSecondary, fontSize = 11.sp)
            if (showsMember) {
                Text(
                    "· ${ticket.memberName}",
                    color = IosColors.TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.weight(1f))
            Text(shortAgo(ticket.lastMessageAt ?: ticket.updatedAt), color = IosColors.TextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
internal fun StatusChip(status: TicketStatus) {
    val tint = when (status) {
        TicketStatus.RESOLVED, TicketStatus.CLOSED -> IosColors.BuyGreen
        TicketStatus.WAITING_ON_MEMBER -> IosColors.Warning
        TicketStatus.IN_PROGRESS -> IosColors.Accent
        TicketStatus.OPEN -> IosColors.TextPrimary
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.15f))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text = status.memberLabel.uppercase(),
            color = tint,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black
        )
    }
}

/** "12m ago". Short enough to sit on a row that already carries three things. */
internal fun shortAgo(date: Date): String {
    val seconds = ((Date().time - date.time) / 1000).coerceAtLeast(0)
    return when {
        seconds < 60 -> "just now"
        seconds < 3_600 -> "${TimeUnit.SECONDS.toMinutes(seconds)}m ago"
        seconds < 86_400 -> "${TimeUnit.SECONDS.toHours(seconds)}h ago"
        else -> "${TimeUnit.SECONDS.toDays(seconds)}d ago"
    }
}
