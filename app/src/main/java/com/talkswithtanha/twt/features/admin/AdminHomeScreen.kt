package com.talkswithtanha.twt.features.admin

import com.talkswithtanha.twt.core.designsystem.WebFonts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.designsystem.staggeredAppear
import com.talkswithtanha.twt.core.model.StaffArea
import com.talkswithtanha.twt.features.settings.SettingsMetrics

/**
 * The admin area's front door, showing only the areas this account holds.
 *
 * Somebody running the exchange sees deals and nothing about posting trades;
 * somebody calling trades sees the opposite. An account with one area lands on
 * a screen with one thing on it, which is the point.
 */
@Composable
fun AdminHomeScreen(
    onBack: () -> Unit,
    onPostSignal: () -> Unit,
    onTickets: () -> Unit,
    onChats: () -> Unit,
    onSignals: () -> Unit,
    onDeals: () -> Unit,
    onBanners: () -> Unit,
    viewModel: AdminHomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(IosColors.Background)
            .statusBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = SettingsMetrics.pageInset, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(IosColors.SecondaryBackground)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = IosColors.TextPrimary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.weight(1f))
            Text("Admin", color = IosColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(40.dp))
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SettingsMetrics.pageInset)
        ) {
            Text("Admin", color = IosColors.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = WebFonts.Display)
            Text(
                text = "Signed in as ${state.user?.fullName ?: "staff"} · ${state.user?.role?.stored?.replaceFirstChar { it.uppercase() } ?: ""}",
                color = IosColors.TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
            )

            // Posting a signal is the one thing worth a full-width button: it is
            // the reason to open this screen at all, and everything else here is
            // a reaction to something that already happened. It is also the
            // first thing to disappear for a team member who does not call
            // trades.
            if (state.can(StaffArea.SIGNALS)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White)
                        .clickable(onClick = onPostSignal),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.AddCircle, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(9.dp))
                    Text("Post a signal", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(16.dp))
            }

            var index = 0
            if (state.can(StaffArea.TICKETS)) {
                AdminTile(
                    icon = Icons.Filled.SupportAgent,
                    title = "Support tickets",
                    subtitle = if (state.waitingTickets == 0) {
                        "Nobody is waiting on a reply"
                    } else {
                        "${state.waitingTickets} member${if (state.waitingTickets == 1) "" else "s"} waiting on a reply"
                    },
                    badge = state.waitingTickets,
                    modifier = Modifier.staggeredAppear(index++),
                    onClick = onTickets
                )
            }
            if (state.can(StaffArea.CHATS)) {
                AdminTile(
                    icon = Icons.AutoMirrored.Filled.Chat,
                    title = "Member chats",
                    subtitle = "Every member's private thread — reply as the team",
                    modifier = Modifier.staggeredAppear(index++),
                    onClick = onChats
                )
            }
            if (state.can(StaffArea.SIGNALS)) {
                AdminTile(
                    icon = Icons.Filled.ShowChart,
                    title = "Live signals",
                    subtitle = if (state.liveSignals == 0) {
                        "Nothing running"
                    } else {
                        "${state.liveSignals} running · tick targets, close trades"
                    },
                    modifier = Modifier.staggeredAppear(index++),
                    onClick = onSignals
                )
            }
            // Also behind MARKETPLACE_ENABLED: the exchange is left out of the
            // Play build entirely, and staff must not be shown a counter for a
            // shop that is not in this build.
            if (state.can(StaffArea.MARKETPLACE) && com.talkswithtanha.twt.BuildConfig.MARKETPLACE_ENABLED) {
                AdminTile(
                    icon = Icons.Filled.SwapHoriz,
                    title = "Marketplace",
                    subtitle = "Confirm payments and release funds on deals",
                    modifier = Modifier.staggeredAppear(index++),
                    onClick = onDeals
                )
            }
            if (state.can(StaffArea.APP_CONTENT)) {
                AdminTile(
                    icon = Icons.Filled.Image,
                    title = "Home banners",
                    subtitle = "What members see at the top of the app",
                    modifier = Modifier.staggeredAppear(index++),
                    onClick = onBanners
                )
            }

            Text(
                text = state.footnote,
                color = IosColors.TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 16.dp, bottom = 48.dp)
            )
        }
    }
}

@Composable
private fun AdminTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    badge: Int = 0,
    onClick: () -> Unit
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF141414))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF242424)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = IosColors.TextPrimary, modifier = Modifier.size(18.dp))
        }

        Column(Modifier.weight(1f).padding(start = 13.dp)) {
            Text(title, color = IosColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = IosColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        }

        if (badge > 0) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(IosColors.SellRed)
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text("$badge", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.size(8.dp))
        }

        Icon(Icons.Filled.ChevronRight, null, tint = IosColors.TextSecondary, modifier = Modifier.size(18.dp))
    }
}
