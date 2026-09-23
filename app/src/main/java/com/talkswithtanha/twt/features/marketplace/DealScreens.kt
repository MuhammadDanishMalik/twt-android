package com.talkswithtanha.twt.features.marketplace

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.SwapHoriz
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.talkswithtanha.twt.core.designsystem.Haptics
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.designsystem.staggeredAppear
import com.talkswithtanha.twt.core.model.Deal
import com.talkswithtanha.twt.core.model.DealSide
import com.talkswithtanha.twt.core.model.DealStatus
import com.talkswithtanha.twt.core.model.ExchangeRate
import com.talkswithtanha.twt.features.settings.SettingsCard
import com.talkswithtanha.twt.features.settings.SettingsDivider
import com.talkswithtanha.twt.features.settings.SettingsMetrics
import com.talkswithtanha.twt.features.settings.SettingsSectionHeader
import com.talkswithtanha.twt.features.settings.SheetHeader
import com.talkswithtanha.twt.features.settings.SheetPrimaryButton
import java.text.SimpleDateFormat
import java.util.Locale

/** Every deal this member has opened. */
@Composable
fun MyDealsScreen(
    onClose: () -> Unit,
    onOpenDeal: (String) -> Unit,
    onNewDeal: () -> Unit,
    viewModel: DealsViewModel = hiltViewModel()
) {
    val deals by viewModel.deals.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(IosColors.Background)
            .statusBarsPadding()
    ) {
        SheetHeader("My Deals", onClose)

        LazyColumn(
            contentPadding = PaddingValues(
                start = SettingsMetrics.pageInset,
                end = SettingsMetrics.pageInset,
                bottom = 48.dp
            )
        ) {
            item { SettingsSectionHeader("MY DEALS") }

            if (deals.isEmpty()) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(SettingsMetrics.cardRadius))
                            .background(IosColors.SecondaryBackground)
                            .padding(SettingsMetrics.rowPadding),
                        horizontalArrangement = Arrangement.spacedBy(SettingsMetrics.iconGap),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.SwapHoriz,
                            contentDescription = null,
                            tint = IosColors.TextSecondary,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            "No deals yet. Your exchange history will appear here.",
                            color = IosColors.TextSecondary,
                            fontSize = 15.sp
                        )
                    }
                }
            } else {
                item {
                    SettingsCard {
                        deals.forEachIndexed { index, deal ->
                            DealRow(deal, Modifier.staggeredAppear(index)) { onOpenDeal(deal.id) }
                            if (index < deals.lastIndex) {
                                SettingsDivider(SettingsMetrics.rowPadding)
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(24.dp))
                SheetPrimaryButton("Start a new deal", onClick = onNewDeal)
            }
        }
    }
}

@Composable
private fun DealRow(deal: Deal, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = SettingsMetrics.rowPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "${deal.side.title} · ${money(deal.amountUsdCents, "$")}",
                color = IosColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${deal.reference} · ${dayMonth.format(deal.createdAt)}",
                color = IosColors.TextSecondary,
                fontSize = 13.sp
            )
        }
        StatusPill(deal.status)
    }
}

@Composable
private fun StatusPill(status: DealStatus) {
    val tint = when {
        status == DealStatus.COMPLETED -> IosColors.BuyGreen
        status == DealStatus.CANCELLED -> IosColors.TextSecondary
        status == DealStatus.DISPUTED -> IosColors.Warning
        else -> IosColors.Accent
    }
    Box(
        Modifier
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(status.title, color = tint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** `$1,234.50`. Minor units in, never a float. */
private fun money(minor: Long, symbol: String): String {
    val sign = if (minor < 0) "-" else ""
    val abs = kotlin.math.abs(minor)
    return "%s%s%,d.%02d".format(sign, symbol, abs / 100, abs % 100)
}

/** Built per call — see the note in `ChatScreens.formatTime`. */
private val dayMonth: java.text.SimpleDateFormat
    get() = java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault())
