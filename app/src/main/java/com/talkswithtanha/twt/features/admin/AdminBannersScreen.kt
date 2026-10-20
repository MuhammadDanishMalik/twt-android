package com.talkswithtanha.twt.features.admin

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.talkswithtanha.twt.core.data.AdminBanner
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.designsystem.components.TwtButton
import com.talkswithtanha.twt.core.designsystem.components.TwtSecondaryButton
import com.talkswithtanha.twt.features.settings.SettingsMetrics
import com.talkswithtanha.twt.features.settings.SheetHeader

/**
 * The app's own furniture: the banners at the top of the home screen.
 *
 * This is the "app content" half of the admin area — nothing here touches money
 * or a member's account, which is exactly why it is safe from a phone. The
 * common job is not writing a new banner, it is turning yesterday's off, so the
 * switch is the first thing on every row and editing is a tap away rather than
 * the default.
 *
 * Images are a pasted URL rather than an upload, for the same reason the signal
 * screenshots are: Firebase Storage is not enabled on this project yet.
 */
@Composable
fun AdminBannersScreen(
    onBack: () -> Unit,
    viewModel: AdminBannersViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<AdminBanner?>(null) }

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
            Text("Home banners", color = IosColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(IosColors.SecondaryBackground)
                    .clickable {
                        editing = AdminBanner(order = (state.banners.maxOfOrNull { it.order } ?: 0) + 1)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Add, "New banner", tint = IosColors.TextPrimary, modifier = Modifier.size(18.dp))
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(
                start = SettingsMetrics.pageInset,
                end = SettingsMetrics.pageInset,
                bottom = 48.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            state.error?.let { item { Text(it, color = IosColors.SellRed, fontSize = 13.sp) } }

            if (state.banners.isEmpty()) {
                item {
                    Text(
                        text = "No banners yet. The home screen simply shows nothing at the top until there is one.",
                        color = IosColors.TextSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }

            items(state.banners, key = { it.id }) { banner ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF141414))
                        .padding(13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(
                        checked = banner.isActive,
                        onCheckedChange = { viewModel.setActive(banner, it) }
                    )
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(
                            text = banner.title.ifEmpty { "(no title)" },
                            color = if (banner.isActive) IosColors.TextPrimary else IosColors.TextSecondary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = banner.subtitle.ifEmpty {
                                AdminBanner.actions.firstOrNull { it.first == banner.action }?.second ?: banner.action
                            },
                            color = IosColors.TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF222222))
                            .clickable { editing = banner }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text("Edit", color = IosColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    editing?.let { banner ->
        BannerEditor(
            banner = banner,
            onDismiss = { editing = null },
            onSave = {
                viewModel.save(it)
                editing = null
            },
            onDelete = {
                viewModel.delete(it)
                editing = null
            }
        )
    }
}

@Composable
private fun BannerEditor(
    banner: AdminBanner,
    onDismiss: () -> Unit,
    onSave: (AdminBanner) -> Unit,
    onDelete: (AdminBanner) -> Unit
) {
    var draft by remember(banner.id) { mutableStateOf(banner) }

    Column(
        Modifier
            .fillMaxSize()
            .background(IosColors.Background)
            .statusBarsPadding()
            .imePadding()
    ) {
        SheetHeader(if (banner.id.isEmpty()) "New banner" else "Edit banner", onDismiss)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SettingsMetrics.pageInset)
        ) {
            Field("Title", draft.title) { draft = draft.copy(title = it) }
            Field("Subtitle", draft.subtitle) { draft = draft.copy(subtitle = it) }
            Field("Image URL", draft.imageUrl) { draft = draft.copy(imageUrl = it) }

            Text(
                text = "WHEN TAPPED",
                color = IosColors.TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
            )
            AdminBanner.actions.forEach { (value, label) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF171717))
                        .clickable { draft = draft.copy(action = value) }
                        .padding(horizontal = 13.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, color = IosColors.TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    if (draft.action == value) {
                        Icon(Icons.Filled.Check, null, tint = IosColors.BuyGreen, modifier = Modifier.size(16.dp))
                    }
                }
            }

            if (draft.action == "url") {
                Field("Link", draft.url) { draft = draft.copy(url = it) }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Show it on the home screen",
                    color = IosColors.TextPrimary,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = draft.isActive, onCheckedChange = { draft = draft.copy(isActive = it) })
            }

            TwtButton(text = "Save", onClick = { onSave(draft) })

            if (banner.id.isNotEmpty()) {
                Spacer(Modifier.size(10.dp))
                TwtSecondaryButton(text = "Delete banner", onClick = { onDelete(banner) })
            }

            Spacer(Modifier.size(48.dp))
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    Text(
        text = label.uppercase(),
        color = IosColors.TextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
    )
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF171717))
            .padding(13.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = LocalTextStyle.current.copy(color = IosColors.TextPrimary, fontSize = 15.sp),
            cursorBrush = SolidColor(IosColors.TextPrimary),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
