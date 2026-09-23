package com.talkswithtanha.twt.features.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.talkswithtanha.twt.core.designsystem.AnimatedText
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.images.Cloudinary
import com.talkswithtanha.twt.core.designsystem.brand.CrownGlyph
import com.talkswithtanha.twt.core.designsystem.components.RedactedText
import com.talkswithtanha.twt.core.designsystem.staggeredAppear
import com.talkswithtanha.twt.core.model.AccessCode
import com.talkswithtanha.twt.core.model.Countries
import com.talkswithtanha.twt.core.model.User
import com.talkswithtanha.twt.features.settings.SettingsCard
import com.talkswithtanha.twt.features.settings.SettingsDivider
import com.talkswithtanha.twt.features.settings.SettingsMetrics
import com.talkswithtanha.twt.features.settings.SettingsSectionHeader
import com.talkswithtanha.twt.features.settings.SheetHeader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "View Profile" — everything the app knows about this account, read-only.
 *
 * A port of `AccountDetailsSheet.swift`, and read-only for the same reason:
 * editing lives one row down under Edit Profile, and a screen that both shows
 * and edits ends up full of fields that look tappable and are not. What is here
 * is the set of facts a member gets asked for when they message support —
 * their email, their access code, and when their access runs out.
 *
 * Values shimmer rather than showing dashes until the member record arrives.
 * Every row here is a fact about the reader, and a screen that briefly tells
 * somebody their own email is "—" reads as the app having lost them.
 */
@Composable
fun AccountDetailsScreen(
    onClose: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val user by viewModel.user.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(IosColors.Background)
            .statusBarsPadding()
    ) {
        SheetHeader("Profile", onClose)

        LazyColumn(
            contentPadding = PaddingValues(
                start = SettingsMetrics.pageInset,
                end = SettingsMetrics.pageInset,
                bottom = 48.dp
            )
        ) {
            item { Identity(user, Modifier.staggeredAppear(0)) }

            item {
                SettingsCard(Modifier.staggeredAppear(1)) {
                    DetailRow("Email", user?.email, 168.dp)
                    SettingsDivider(SettingsMetrics.rowPadding)
                    DetailRow("Phone", user?.let { it.phone ?: NOT_SET }, 96.dp)
                    SettingsDivider(SettingsMetrics.rowPadding)
                    DetailRow("Bio", user?.let { it.bio?.ifBlank { null } ?: NOT_SET }, 120.dp)
                    SettingsDivider(SettingsMetrics.rowPadding)
                    DetailRow("Country", user?.let { Countries.name(it.country) }, 88.dp)
                    SettingsDivider(SettingsMetrics.rowPadding)
                    DetailRow("Signed in with", user?.loginProvider?.stored, 64.dp)
                }
            }

            item {
                SettingsSectionHeader("Access")
                SettingsCard(Modifier.staggeredAppear(2)) {
                    DetailRow("Status", user?.let { if (it.hasAppAccess) "Unlocked" else "Locked" }, 88.dp)
                    SettingsDivider(SettingsMetrics.rowPadding)
                    DetailRow("Valid until", user?.let(::expiryText), 140.dp)
                    SettingsDivider(SettingsMetrics.rowPadding)
                    // Shown so somebody can read it back over WhatsApp without
                    // digging out the message it arrived in.
                    DetailRow(
                        label = "Access code",
                        value = user?.let { member ->
                            member.accessCode?.let(AccessCode::display) ?: EM_DASH
                        },
                        placeholderWidth = 150.dp,
                        monospaced = true
                    )
                }
            }

            item {
                SettingsSectionHeader("Member since")
                SettingsCard(Modifier.staggeredAppear(3)) {
                    DetailRow("Joined", user?.let { longDate(it.createdAt) }, 140.dp)
                }
            }
        }
    }
}

/** The avatar, the name and — if their access is live — the gold capsule. */
@Composable
private fun Identity(user: User?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(IosColors.AvatarGradient)),
            contentAlignment = Alignment.Center
        ) {
            val photo = Cloudinary.avatar(
                user?.profilePhoto?.takeIf { it.isNotBlank() },
                size = 76
            )
            if (photo != null) {
                AsyncImage(
                    model = photo,
                    contentDescription = "Profile photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            } else {
                // The initial stands in until a member sets a photo. Accounts
                // created through Google arrive with one already.
                Text(
                    text = user?.fullName?.take(1)?.uppercase() ?: "T",
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        AnimatedText(
            text = user?.fullName ?: "Trader",
            style = LocalTextStyle.current.copy(
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            ),
            color = IosColors.TextPrimary,
            maxLines = 1
        )

        if (user?.hasAppAccess == true) {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(IosColors.Gold)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CrownGlyph(Modifier.size(11.dp), tint = Color.Black)
                Text(
                    text = "VIP ACCESS",
                    color = Color.Black,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }
        }
    }
}

/**
 * Label on the left, fact on the right.
 *
 * A null [value] means the record has not arrived yet, which is not the same as
 * a field the member never filled in — that one has already resolved to
 * "Not set" by the time it gets here.
 */
@Composable
private fun DetailRow(
    label: String,
    value: String?,
    placeholderWidth: androidx.compose.ui.unit.Dp,
    monospaced: Boolean = false
) {
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 50.dp)
            .padding(horizontal = SettingsMetrics.rowPadding, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = IosColors.TextSecondary,
            fontSize = 16.sp
        )
        Spacer(Modifier.weight(1f))
        RedactedText(
            text = value,
            placeholderWidth = placeholderWidth,
            style = LocalTextStyle.current.copy(
                fontSize = if (monospaced) 15.sp else 16.sp,
                fontWeight = if (monospaced) FontWeight.Medium else FontWeight.Normal,
                fontFamily = if (monospaced) FontFamily.Monospace else FontFamily.Default,
                textAlign = TextAlign.End
            ),
            color = IosColors.TextPrimary,
            maxLines = 2
        )
    }
}

private const val NOT_SET = "Not set"
private const val EM_DASH = "—"

/** Locked accounts get a dash; unlocked ones with no expiry never run out. */
private fun expiryText(user: User): String = when {
    !user.hasAppAccess -> EM_DASH
    user.membershipExpiresAt == null -> "Never expires"
    else -> longDate(user.membershipExpiresAt)
}

/** Built per call — see the note in `ChatScreens.formatTime`. */
private fun longDate(date: Date): String =
    SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(date)
