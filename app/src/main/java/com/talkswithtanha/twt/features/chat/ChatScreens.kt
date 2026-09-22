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
    onOpenSellerChat: (String) -> Unit,
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
                    val supportRoom = rooms.lastOrNull()?.roomId
                    ChatRoomRow(
                        title = "Trade with Tanha",
                        subtitle = "Buy and sell dollars",
                        initials = "TW",
                        icon = null,
                        locked = supportRoom == null,
                        crowned = false,
                        onClick = { supportRoom?.let(onOpenSellerChat) }
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
