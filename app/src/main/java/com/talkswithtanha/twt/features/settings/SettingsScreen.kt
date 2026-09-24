package com.talkswithtanha.twt.features.settings

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.talkswithtanha.twt.BuildConfig
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.designsystem.components.MemberAvatar
import com.talkswithtanha.twt.core.notifications.NotificationPermission
import com.talkswithtanha.twt.core.notifications.rememberNotificationPermissionRequest
import com.talkswithtanha.twt.features.profile.ProfileViewModel

/**
 * Settings, as a full-height sheet — the screen behind the avatar on home.
 *
 * A direct port of `ProfileView.swift`, down to the order of the groups and the
 * colour of each glyph tile.
 */
@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    onViewProfile: () -> Unit,
    onEditProfile: () -> Unit,
    onMySignals: () -> Unit,
    onMyDeals: () -> Unit,
    onAppearance: () -> Unit,
    onContactSupport: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val support by viewModel.support.collectAsStateWithLifecycle()
    val openFollows by viewModel.openFollowCount.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmSignOut by remember { mutableStateOf(false) }
    var notificationsOn by remember { mutableStateOf(NotificationPermission.isGranted(context)) }
    val askForNotifications = rememberNotificationPermissionRequest { granted ->
        notificationsOn = granted
    }

    fun open(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
    }

    fun openSystemSettings() {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            )
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(IosColors.Background)
            .statusBarsPadding()
    ) {
        SheetHeader("Settings", onClose)

        LazyColumn(
            contentPadding = PaddingValues(
                start = SettingsMetrics.pageInset,
                end = SettingsMetrics.pageInset,
                top = 8.dp,
                bottom = 48.dp
            )
        ) {
            item {
                SettingsCard {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onViewProfile)
                            .padding(
                                horizontal = SettingsMetrics.rowPadding,
                                vertical = 8.dp
                            )
                            .height(68.dp),
                        horizontalArrangement = Arrangement.spacedBy(SettingsMetrics.iconGap),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MemberAvatar(
                            photoUrl = user?.profilePhoto,
                            name = user?.fullName,
                            size = 44.dp
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = user?.fullName ?: "Trader",
                                color = IosColors.TextPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Text(
                                text = "View Profile",
                                color = IosColors.TextSecondary,
                                fontSize = 14.sp
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            tint = IosColors.TextSecondary.copy(alpha = 0.65f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    SettingsDivider(SettingsMetrics.rowPadding)
                    SettingsRow(title = "Edit Profile", onClick = onEditProfile)
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
                SettingsCard {
                    SettingsRow(
                        title = "Account Settings",
                        icon = Icons.Filled.Group,
                        tint = IosColors.SettingsIcon.Account,
                        onClick = onViewProfile
                    )
                    SettingsDivider()
                    SettingsRow(
                        title = "My Signals",
                        icon = Icons.Filled.ShowChart,
                        tint = IosColors.SettingsIcon.Signals,
                        value = if (openFollows > 0) "$openFollows open" else null,
                        onClick = onMySignals
                    )
                    SettingsDivider()
                    SettingsRow(
                        title = "My Deals",
                        icon = Icons.Filled.Wallet,
                        tint = IosColors.SettingsIcon.Deals,
                        onClick = onMyDeals
                    )
                }
            }

            item {
                SettingsSectionHeader("Preferences")
                SettingsCard {
                    SettingsRow(
                        title = "Notifications",
                        icon = Icons.Filled.Notifications,
                        tint = IosColors.SettingsIcon.Notifications,
                        value = if (notificationsOn) "On" else "Off",
                        // Asking is what a tap means here; once the system will
                        // not show the dialog again, the request falls through
                        // to the app's notification settings on its own.
                        accessory = if (notificationsOn) SettingsAccessory.EXTERNAL
                        else SettingsAccessory.CHEVRON,
                        onClick = {
                            if (notificationsOn) openSystemSettings() else askForNotifications()
                        }
                    )
                    SettingsDivider()
                    SettingsRow(
                        title = "Permissions",
                        icon = Icons.Filled.Security,
                        tint = IosColors.SettingsIcon.Permissions,
                        accessory = SettingsAccessory.EXTERNAL,
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                        .setData("package:${context.packageName}".toUri())
                                )
                            }
                        }
                    )
                    SettingsDivider()
                    SettingsRow(
                        title = "Appearance",
                        icon = Icons.Filled.Palette,
                        tint = IosColors.SettingsIcon.Appearance,
                        value = "Dark",
                        onClick = onAppearance
                    )
                }
            }

            item {
                SettingsSectionHeader("Resources")
                SettingsCard {
                    SettingsRow(
                        title = "Contact Support",
                        icon = Icons.Filled.Email,
                        tint = IosColors.SettingsIcon.Support,
                        onClick = onContactSupport
                    )
                    SettingsDivider()
                    SettingsRow(
                        title = "Rate on Google Play",
                        icon = Icons.Filled.Star,
                        tint = IosColors.SettingsIcon.Rate,
                        accessory = SettingsAccessory.EXTERNAL,
                        onClick = {
                            // The Play listing, not the App Store. Same row,
                            // same place in the list, right shop.
                            open("https://play.google.com/store/apps/details?id=${context.packageName}")
                        }
                    )
                    SettingsDivider()
                    SettingsRow(
                        title = "TWT on YouTube",
                        icon = Icons.Filled.PlayArrow,
                        tint = IosColors.SettingsIcon.YouTube,
                        accessory = SettingsAccessory.EXTERNAL,
                        onClick = { open("https://www.youtube.com/@talkswithtanha") }
                    )
                    SettingsDivider()
                    SettingsRow(
                        title = "TWT on Instagram",
                        icon = Icons.Filled.PhotoCamera,
                        tint = IosColors.SettingsIcon.Instagram,
                        accessory = SettingsAccessory.EXTERNAL,
                        onClick = { open("https://www.instagram.com/talkswithtanha") }
                    )
                    SettingsDivider()
                    SettingsRow(
                        title = "Chat on WhatsApp",
                        icon = Icons.Filled.Campaign,
                        tint = IosColors.SettingsIcon.WhatsApp,
                        accessory = SettingsAccessory.EXTERNAL,
                        onClick = {
                            val name = user?.fullName ?: "there"
                            open(
                                support.whatsAppUrl(
                                    "Hi, this is $name. I need help with the TWT app."
                                )
                            )
                        }
                    )
                }
            }

            item {
                SettingsSectionHeader("Account")
                SettingsCard {
                    SettingsRow(
                        title = "Sign Out",
                        icon = Icons.AutoMirrored.Filled.Logout,
                        tint = IosColors.SettingsIcon.SignOut,
                        accessory = SettingsAccessory.NONE,
                        isDestructive = true,
                        onClick = { confirmSignOut = true }
                    )
                }
            }

            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Trade with Tanha",
                        color = IosColors.TextSecondary.copy(alpha = 0.7f),
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        color = IosColors.TextSecondary.copy(alpha = 0.45f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            containerColor = IosColors.SecondaryBackground,
            title = { Text("Sign out of TWT?", color = IosColors.TextPrimary) },
            text = {
                Text(
                    "Your access code stays linked to this account. Signing back in keeps it.",
                    color = IosColors.TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    viewModel.signOut()
                }) { Text("Sign out", color = IosColors.SellRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) {
                    Text("Stay signed in", color = IosColors.TextSecondary)
                }
            }
        )
    }
}
