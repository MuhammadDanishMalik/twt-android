package com.talkswithtanha.twt.features.chat

import androidx.compose.foundation.lazy.itemsIndexed
import com.talkswithtanha.twt.core.designsystem.bouncyClick
import com.talkswithtanha.twt.core.designsystem.appear
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.talkswithtanha.twt.core.data.Snapshot
import com.talkswithtanha.twt.core.data.SupportThread
import com.talkswithtanha.twt.core.designsystem.IosColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * The mark beside a staff member's name in any chat, as on the admin panel:
 * a blue tick for Tanha and admins, a green shield for team members.
 */
@Composable
fun RoleMark(role: String?, modifier: Modifier = Modifier) {
    when (role) {
        "owner", "admin" -> Icon(
            Icons.Filled.Verified,
            contentDescription = if (role == "owner") "Tanha" else "Admin",
            tint = Color(0xFF3390EC),
            modifier = modifier.size(15.dp)
        )
        "team" -> Icon(
            Icons.Filled.VerifiedUser,
            contentDescription = "Team member",
            tint = Color(0xFF22C55E),
            modifier = modifier.size(15.dp)
        )
        else -> Unit
    }
}

/**
 * Every member's private thread, for staff allowed the Chats area — the same
 * queue the admin panel's Messages page works from. Threads waiting on a reply
 * come first; answering one takes it out of the queue for everyone.
 */
@Composable
fun StaffInboxScreen(
    onBack: () -> Unit,
    onOpenThread: (String) -> Unit,
    viewModel: StaffInboxViewModel = hiltViewModel()
) {
    val snapshot by viewModel.threads.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .background(IosColors.Background)
            .statusBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Member chats", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                val waiting = (snapshot as? Snapshot.Data)?.value?.count { it.needsReply } ?: 0
                Text(
                    if (waiting == 0) "Nobody is waiting" else "$waiting waiting for a reply",
                    color = if (waiting == 0) IosColors.TextSecondary else Color(0xFF22C55E),
                    fontSize = 13.sp
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Search, null, tint = IosColors.TextSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("Search name, email or message", color = IosColors.TextSecondary, fontSize = 15.sp)
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        when (val s = snapshot) {
            null -> Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                com.talkswithtanha.twt.core.designsystem.components.LoadingLabel("Loading member chats", Modifier.padding(start = 4.dp))
                com.talkswithtanha.twt.core.designsystem.components.SkeletonCard {
                    com.talkswithtanha.twt.core.designsystem.components.SkeletonRows(7, trailing = false)
                }
            }
            is Snapshot.Failed -> Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
                Text(
                    "The chats could not be loaded. Your account may not have the Chats area.",
                    color = IosColors.TextSecondary,
                    fontSize = 15.sp
                )
            }
            is Snapshot.Data -> {
                val q = query.trim().lowercase()
                val shown = s.value.filter {
                    q.isEmpty() || it.memberName.lowercase().contains(q) ||
                        it.memberEmail.lowercase().contains(q) || it.lastMessage.lowercase().contains(q)
                }
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(shown, key = { _, thread -> thread.roomId }) { index, thread ->
                        Box(Modifier.appear(index % 8)) { ThreadRow(thread) { onOpenThread(thread.roomId) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreadRow(thread: SupportThread, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .bouncyClick(pressedScale = 0.98f, haptic = false, onClick = onClick)
            .clip(RoundedCornerShape(16.dp))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val initials = thread.memberName.split(" ").filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Text(initials, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    thread.memberName,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = if (thread.needsReply) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.weight(1f))
                Text(
                    thread.lastMessageAt?.let { whenLabel(it) }.orEmpty(),
                    color = if (thread.needsReply) Color(0xFF22C55E) else IosColors.TextSecondary,
                    fontSize = 12.sp
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    thread.lastMessage.ifBlank { thread.memberEmail },
                    color = if (thread.needsReply) Color.White.copy(alpha = 0.85f) else IosColors.TextSecondary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (thread.needsReply) {
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF22C55E)))
                }
            }
        }
    }
}

private fun whenLabel(date: Date): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { time = date }
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    return SimpleDateFormat(if (sameDay) "HH:mm" else "d MMM", Locale.getDefault()).format(date)
}
