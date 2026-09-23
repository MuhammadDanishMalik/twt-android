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
                            DealRow(deal) { onOpenDeal(deal.id) }
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
private fun DealRow(deal: Deal, onClick: () -> Unit) {
    Row(
        Modifier
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

/** Opening a deal: which way, how much, at the rate on screen. */
@Composable
fun NewDealScreen(
    onClose: () -> Unit,
    onOpened: (String) -> Unit,
    viewModel: NewDealViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rate by viewModel.rate.collectAsStateWithLifecycle()

    LaunchedEffect(state.createdDealId) {
        state.createdDealId?.let(onOpened)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(IosColors.Background)
            .statusBarsPadding()
            .imePadding()
    ) {
        SheetHeader("New deal", onClose)

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SettingsMetrics.pageInset)
        ) {
            SettingsSectionHeader("Which way")

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DealSide.entries.forEach { side ->
                    SideChip(
                        side = side,
                        selected = state.side == side,
                        modifier = Modifier.weight(1f)
                    ) { viewModel.onSideChange(side) }
                }
            }

            SettingsSectionHeader("Amount in USD")

            SettingsCard {
                BasicTextField(
                    value = state.amount,
                    onValueChange = viewModel::onAmountChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = IosColors.TextPrimary,
                        fontSize = 20.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    cursorBrush = SolidColor(IosColors.Accent),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(SettingsMetrics.rowPadding),
                    decorationBox = { inner ->
                        if (state.amount.isEmpty()) {
                            Text(
                                "0.00",
                                color = IosColors.TextSecondary.copy(alpha = 0.6f),
                                fontSize = 20.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        inner()
                    }
                )
            }

            val cents = viewModel.amountCents
            val live = rate
            if (cents != null && live != null) {
                Spacer(Modifier.height(16.dp))
                SettingsCard {
                    QuoteRow("Rate", "PKR ${ExchangeRate.formatPaisa(live.buyPaisa)} per USD")
                    SettingsDivider(SettingsMetrics.rowPadding)
                    QuoteRow(
                        label = if (state.side == DealSide.BUY) "You pay" else "You send",
                        value = if (state.side == DealSide.BUY) {
                            "PKR ${ExchangeRate.formatPaisa(live.pkrPaisaToBuy(cents))}"
                        } else {
                            money(cents, "$")
                        }
                    )
                    SettingsDivider(SettingsMetrics.rowPadding)
                    QuoteRow(
                        label = "You receive",
                        value = if (state.side == DealSide.BUY) {
                            money(cents, "$")
                        } else {
                            "PKR ${ExchangeRate.formatPaisa(live.pkrPaisaToSell(cents))}"
                        }
                    )
                }
                Text(
                    // Said plainly, because it is the reason to open a deal
                    // rather than just message him.
                    text = "This rate is locked to the deal when you open it.",
                    color = IosColors.TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                )
            }

            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = IosColors.SellRed, fontSize = 14.sp)
            }

            Spacer(Modifier.height(24.dp))

            SheetPrimaryButton(
                text = if (state.isSubmitting) "Opening…" else "Open deal",
                enabled = cents != null && !state.isSubmitting,
                onClick = viewModel::open
            )

            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun SideChip(
    side: DealSide,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val tint = if (side == DealSide.BUY) IosColors.BuyGreen else IosColors.SellRed
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) tint else IosColors.SecondaryBackground)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = side.title,
            color = if (selected) Color.Black else IosColors.TextSecondary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun QuoteRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = SettingsMetrics.rowPadding, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = IosColors.TextSecondary, fontSize = 15.sp)
        Text(
            value,
            color = IosColors.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}

/** One deal: where it has got to, and the one thing the member can do next. */
@Composable
fun DealDetailScreen(
    onClose: () -> Unit,
    onOpenChat: () -> Unit,
    viewModel: DealDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    var reference by remember { mutableStateOf("") }

    val deal = state.deal

    Column(
        Modifier
            .fillMaxSize()
            .background(IosColors.Background)
            .statusBarsPadding()
            .imePadding()
    ) {
        SheetHeader(deal?.reference ?: "Deal", onClose)

        if (deal == null) {
            Text(
                "Loading…",
                color = IosColors.TextSecondary,
                modifier = Modifier.padding(SettingsMetrics.pageInset)
            )
            return@Column
        }

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SettingsMetrics.pageInset)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = deal.status.title,
                    color = IosColors.TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = deal.status.detail(deal.side),
                    color = IosColors.TextSecondary,
                    fontSize = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            StepTrack(deal.status)

            SettingsSectionHeader("The deal")
            SettingsCard {
                QuoteRow("Direction", deal.side.title)
                SettingsDivider(SettingsMetrics.rowPadding)
                QuoteRow("Amount", money(deal.amountUsdCents, "$"))
                SettingsDivider(SettingsMetrics.rowPadding)
                QuoteRow("Locked rate", "PKR ${ExchangeRate.formatPaisa(deal.lockedRatePaisa)}")
                SettingsDivider(SettingsMetrics.rowPadding)
                QuoteRow(
                    "You pay",
                    if (deal.side == DealSide.BUY) {
                        "PKR ${ExchangeRate.formatPaisa(deal.payTotalMinor)}"
                    } else {
                        money(deal.payTotalMinor, "$")
                    }
                )
            }

            SettingsSectionHeader("Pay into")
            val account = deal.sellerAccount
            if (account == null) {
                // No account configured. Deliberately not a placeholder number:
                // the one screen where a made-up account is worse than none.
                SettingsCard {
                    Column(Modifier.padding(SettingsMetrics.rowPadding)) {
                        Text(
                            "Tanha will send you the account details in your chat.",
                            color = IosColors.TextPrimary,
                            fontSize = 15.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Quote ${deal.reference} when you message him.",
                            color = IosColors.TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                SheetPrimaryButton("Open the chat", onClick = onOpenChat)
            } else {
                SettingsCard {
                    CopyableRow(account.method, account.accountTitle) {}
                    SettingsDivider(SettingsMetrics.rowPadding)
                    CopyableRow("Account", account.accountNumber) {
                        clipboard.setText(AnnotatedString(account.accountNumber))
                        Haptics.success(haptics)
                    }
                }
            }

            if (deal.status.memberCanReportPayment) {
                SettingsSectionHeader("Once you have paid")
                SettingsCard {
                    BasicTextField(
                        value = reference,
                        onValueChange = { reference = it },
                        singleLine = true,
                        textStyle = TextStyle(color = IosColors.TextPrimary, fontSize = 16.sp),
                        cursorBrush = SolidColor(IosColors.Accent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(SettingsMetrics.rowPadding),
                        decorationBox = { inner ->
                            if (reference.isEmpty()) {
                                Text(
                                    "Transaction ID or reference",
                                    color = IosColors.TextSecondary.copy(alpha = 0.6f),
                                    fontSize = 16.sp
                                )
                            }
                            inner()
                        }
                    )
                }
                Spacer(Modifier.height(16.dp))
                SheetPrimaryButton(
                    text = if (state.reportingPayment) "Sending…" else "I have sent the payment",
                    enabled = reference.isNotBlank() && !state.reportingPayment
                ) {
                    viewModel.reportPayment(reference)
                }
            }

            state.error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = IosColors.SellRed, fontSize = 14.sp)
            }

            if (deal.events.isNotEmpty()) {
                SettingsSectionHeader("History")
                SettingsCard {
                    deal.events.forEachIndexed { index, event ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = SettingsMetrics.rowPadding,
                                    vertical = 12.dp
                                ),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(event.note, color = IosColors.TextPrimary, fontSize = 14.sp)
                            Text(
                                dayMonth.format(event.timestamp),
                                color = IosColors.TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                        if (index < deal.events.lastIndex) {
                            SettingsDivider(SettingsMetrics.rowPadding)
                        }
                    }
                }
            }

            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun StepTrack(status: DealStatus) {
    val current = status.stepIndex
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DealStatus.happyPath.forEachIndexed { index, label ->
            val done = current != null && index <= current
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(
                            if (done) IosColors.Accent else Color.White.copy(alpha = 0.12f)
                        )
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = label,
                    color = if (done) IosColors.TextPrimary else IosColors.TextSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun CopyableRow(label: String, value: String, onCopy: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onCopy)
            .padding(horizontal = SettingsMetrics.rowPadding, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = IosColors.TextSecondary, fontSize = 13.sp)
            Text(
                value,
                color = IosColors.TextPrimary,
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Icon(
            Icons.Filled.ContentCopy,
            contentDescription = "Copy",
            tint = IosColors.TextSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** Minor units to money, without ever going through a Double. */
private fun money(minor: Long, symbol: String): String {
    val sign = if (minor < 0) "-" else ""
    val abs = kotlin.math.abs(minor)
    return "%s%s%,d.%02d".format(sign, symbol, abs / 100, abs % 100)
}

private val dayMonth = SimpleDateFormat("d MMM", Locale.getDefault())
