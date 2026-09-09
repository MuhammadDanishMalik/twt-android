package com.talkswithtanha.twt.features.profile

import android.content.Intent
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.talkswithtanha.twt.core.designsystem.Radius
import com.talkswithtanha.twt.core.designsystem.Spacing
import com.talkswithtanha.twt.core.designsystem.TwtColors
import com.talkswithtanha.twt.core.designsystem.components.PremiumBadge
import com.talkswithtanha.twt.core.designsystem.components.TwtCard
import com.talkswithtanha.twt.core.designsystem.components.TwtScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onEditProfile: () -> Unit,
    onOpenMySignals: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val support by viewModel.support.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }

    fun open(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
    }

    TwtScreen {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            contentPadding = PaddingValues(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TwtColors.TextPrimary
                        )
                    }
                    Text(
                        text = "Profile",
                        style = MaterialTheme.typography.headlineLarge,
                        color = TwtColors.TextPrimary
                    )
                }
            }

            item {
                TwtCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(TwtColors.SurfaceElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            val photo = user?.profilePhoto
                            if (photo != null) {
                                AsyncImage(
                                    model = photo,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    Icons.Outlined.Person,
                                    contentDescription = null,
                                    tint = TwtColors.TextTertiary
                                )
                            }
                        }
                        Spacer(Modifier.padding(horizontal = Spacing.sm))
                        Column {
                            Text(
                                text = user?.fullName.orEmpty(),
                                style = MaterialTheme.typography.titleLarge,
                                color = TwtColors.TextPrimary
                            )
                            Text(
                                text = user?.email.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TwtColors.TextTertiary
                            )
                        }
                    }

                    user?.bio?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(Spacing.md))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TwtColors.TextSecondary
                        )
                    }

                    if (user?.hasAppAccess == true) {
                        Spacer(Modifier.height(Spacing.md))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PremiumBadge()
                            Text(
                                text = user?.membershipExpiresAt?.let {
                                    "Access until ${formatDate(it)}"
                                } ?: "Access does not expire",
                                style = MaterialTheme.typography.labelMedium,
                                color = TwtColors.TextTertiary
                            )
                        }
                    }
                }
            }

            item {
                SettingsGroup {
                    SettingsRow(Icons.Outlined.Edit, "Edit profile", onEditProfile)
                    SettingsRow(Icons.Outlined.Timeline, "My signals", onOpenMySignals)
                }
            }

            item {
                SettingsGroup {
                    SettingsRow(Icons.Outlined.Chat, "Message support") {
                        open(
                            support.whatsAppUrl(
                                "Hi, I need help with the TWT app. " +
                                    "My account is ${user?.email.orEmpty()}."
                            )
                        )
                    }
                    SettingsRow(Icons.Outlined.MailOutline, "Email support") {
                        open(support.mailtoUrl("TWT app support"))
                    }
                }
            }

            item {
                SettingsGroup {
                    SettingsRow(Icons.Outlined.Description, "Terms of use") {
                        open(support.termsUrl)
                    }
                    SettingsRow(Icons.Outlined.PrivacyTip, "Privacy policy") {
                        open(support.privacyUrl)
                    }
                }
            }

            item {
                SettingsGroup {
                    SettingsRow(
                        icon = Icons.AutoMirrored.Outlined.Logout,
                        title = "Sign out",
                        onClick = viewModel::signOut
                    )
                }
            }

            item {
                TextButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Delete my account",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TwtColors.Sell
                    )
                }
            }

            item { Spacer(Modifier.height(Spacing.huge)) }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = TwtColors.BackgroundElevated,
            title = { Text("Delete your account?", color = TwtColors.TextPrimary) },
            text = {
                Text(
                    // Said plainly, because it is true and because somebody who
                    // deletes an account expecting their code back will be in
                    // touch otherwise. Releasing a code is Tanha's decision from
                    // the admin panel, not something deleting an account does.
                    text = "This signs you out permanently and removes your login. " +
                        "Your access code stays used — contact Tanha if you need it released.",
                    color = TwtColors.TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteAccount { }
                }) {
                    Text("Delete", color = TwtColors.Sell)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Cancel", color = TwtColors.TextSecondary)
                }
            }
        )
    }
}

/** Grouped settings cards use the tighter 18dp radius, not the 20dp content one. */
@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    TwtCard(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(Radius.grouped),
        contentPadding = Spacing.xs
    ) {
        content()
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(Radius.chip))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = TwtColors.TextSecondary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = TwtColors.TextPrimary
        )
    }
}

/** Built per call — see the note in `ChatScreens.formatTime`. */
private fun formatDate(date: Date): String =
    SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(date)
