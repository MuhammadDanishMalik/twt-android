package com.talkswithtanha.twt.features.marketplace

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.talkswithtanha.twt.core.designsystem.AnimatedNumber
import com.talkswithtanha.twt.core.designsystem.Haptics
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.designsystem.components.SegmentedControl
import com.talkswithtanha.twt.core.model.Deal
import com.talkswithtanha.twt.core.model.DealSide
import com.talkswithtanha.twt.core.model.ExchangeRate
import com.talkswithtanha.twt.core.model.PaymentAccount

/** Amounts most members pick, so most members never open the keyboard. */
private val QUICK_AMOUNTS = listOf(50, 100, 250, 500, 1000)

private val Surface = Color(0xFF1C1C1E)
private val FieldFill = Color(0xFF141416)

/**
 * Opening a deal.
 *
 * Buy and sell are the same form with two words swapped, so they are one screen
 * with a segment rather than two screens that drift apart. The rate is locked
 * at the moment the deal opens and the button says which rate, because a member
 * who taps it and later sees a different number will assume they were moved.
 *
 * The totals are computed as the member types rather than on submit. The whole
 * question this screen answers is "what do I get", and making somebody commit
 * before it will answer is how a form loses people.
 */
@Composable
fun NewDealScreen(
    onClose: () -> Unit,
    onOpened: (String) -> Unit,
    viewModel: NewDealViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rate by viewModel.rate.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(state.createdDealId) {
        state.createdDealId?.let {
            Haptics.success(haptics)
            onOpened(it)
        }
    }
    LaunchedEffect(state.error) {
        if (state.error != null) Haptics.failure(haptics)
    }

    val buying = state.side == DealSide.BUY
    val cents = viewModel.amountCents
    val lockedPaisa = rate?.paisaFor(state.side)

    // What the two legs come to, live. Null until both an amount and a rate
    // exist, which is what keeps zeroes off the screen before there is an
    // answer to show.
    val payMinor = cents?.let { c ->
        lockedPaisa?.let { r -> if (buying) c * r / 100 else c }
    }
    val receiveMinor = cents?.let { c ->
        lockedPaisa?.let { r -> if (buying) c else c * r / 100 }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(IosColors.Background)
            .statusBarsPadding()
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
            Text(
                text = "Cancel",
                color = IosColors.Accent,
                fontSize = 16.sp,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        Haptics.tap(haptics)
                        onClose()
                    }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
            Text(
                text = "New deal",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp)
        ) {
            SegmentedControl(
                options = listOf("Buy USD", "Sell USD"),
                selectedIndex = if (buying) 0 else 1,
                onSelect = { viewModel.onSideChange(if (it == 0) DealSide.BUY else DealSide.SELL) }
            )

            Spacer(Modifier.height(26.dp))

            // ── The amount ───────────────────────────────────────────────
            Text(
                text = if (buying) "AMOUNT TO BUY" else "AMOUNT TO SELL",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.1.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            AmountField(
                value = state.amount,
                onValueChange = viewModel::onAmountChange
            )

            Spacer(Modifier.height(22.dp))

            // ── What it comes to ─────────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Surface)
                    .padding(16.dp)
            ) {
                Leg(
                    label = if (buying) "YOU PAY" else "YOU RECEIVE",
                    value = payOrReceiveLabel(buying, payMinor, receiveMinor),
                    modifier = Modifier.weight(1f)
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "AT RATE",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.7.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    AnimatedNumber(
                        value = lockedPaisa?.let(ExchangeRate::formatPaisa) ?: "—",
                        style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                        color = Color.White
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Quick amounts ────────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QUICK_AMOUNTS.forEach { dollars ->
                    QuickChip(
                        label = "$$dollars",
                        selected = state.amount == dollars.toString(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Haptics.tap(haptics)
                        viewModel.onQuickAmount(dollars)
                    }
                }
            }

            Spacer(Modifier.height(26.dp))

            // ── Where the money moves ────────────────────────────────────
            Text(
                text = if (buying) "PAY WITH" else "RECEIVE VIA",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.1.sp
            )
            Spacer(Modifier.height(10.dp))

            if (accounts.isEmpty()) {
                // Deliberately not a placeholder account number: the one screen
                // where inventing one would send money to nobody.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Surface)
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Tanha will send you the account details in your chat once " +
                            "the deal is open.",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Surface)
                ) {
                    accounts.forEachIndexed { index, account ->
                        AccountRow(
                            account = account,
                            selected = index == state.accountIndex,
                            onClick = {
                                Haptics.tap(haptics)
                                viewModel.onAccountChange(index)
                            }
                        )
                        if (index != accounts.lastIndex) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 52.dp)
                                    .height(0.5.dp)
                                    .background(Color.White.copy(alpha = 0.08f))
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(22.dp))

            // ── The summary ──────────────────────────────────────────────
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Surface)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                SummaryRow(
                    "You pay",
                    payMinor?.let {
                        Deal.minorWithCurrency(it, state.side.payCurrency)
                    } ?: "—"
                )
                Spacer(Modifier.height(10.dp))
                SummaryRow(
                    "You receive",
                    receiveMinor?.let {
                        Deal.minorWithCurrency(it, state.side.receiveCurrency)
                    } ?: "—"
                )
            }

            state.error?.let { message ->
                Spacer(Modifier.height(14.dp))
                Text(
                    text = message,
                    color = IosColors.SellRed,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }

            Spacer(Modifier.height(24.dp))
        }

        // ── Commit ───────────────────────────────────────────────────────
        Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            ConfirmButton(
                // Naming the rate on the button is the point: this is the
                // number the deal locks, and a member who taps without seeing
                // it will think they were requoted later.
                label = when {
                    lockedPaisa == null -> "Rate unavailable"
                    buying -> "Confirm buy at ${ExchangeRate.formatPaisa(lockedPaisa)}"
                    else -> "Confirm sell at ${ExchangeRate.formatPaisa(lockedPaisa)}"
                },
                enabled = cents != null && lockedPaisa != null && !state.isSubmitting,
                loading = state.isSubmitting,
                onClick = viewModel::open
            )
        }
    }
}

private fun payOrReceiveLabel(buying: Boolean, payMinor: Long?, receiveMinor: Long?): String {
    val minor = if (buying) payMinor else receiveMinor
    return minor?.let { Deal.minorWithCurrency(it, "PKR") } ?: "—"
}

/** The big centred number, with its own currency suffix. */
@Composable
private fun AmountField(value: String, onValueChange: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            if (value.isEmpty()) {
                Text(
                    text = "0",
                    color = Color.White.copy(alpha = 0.25f),
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold
                ),
                cursorBrush = SolidColor(IosColors.Accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.width(AmountFieldWidth)
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "USD",
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** Wide enough for six digits, so the caret has somewhere to sit when empty. */
private val AmountFieldWidth = 140.dp

@Composable
private fun Leg(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.7.sp
        )
        Spacer(Modifier.height(4.dp))
        AnimatedNumber(
            value = value,
            style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
            color = Color.White
        )
    }
}

@Composable
private fun QuickChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) IosColors.Accent else FieldFill)
            .border(
                0.5.dp,
                if (selected) Color.Transparent else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(10.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.7f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun AccountRow(account: PaymentAccount, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Color.White.copy(alpha = 0.07f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (account.method.contains("bank", ignoreCase = true)) {
                    Icons.Filled.AccountBalance
                } else {
                    Icons.Filled.Smartphone
                },
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(13.dp)
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = account.method.ifBlank { "Account" },
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            if (account.accountTitle.isNotBlank()) {
                Text(
                    text = account.accountTitle,
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 12.sp
                )
            }
        }
        // A tick rather than a radio: the row is already the target, and a
        // radio invites somebody to hunt for the small circle.
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(
                    if (selected) IosColors.BuyGreen else Color.White.copy(alpha = 0.10f)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
        AnimatedNumber(
            value = value,
            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            color = Color.White
        )
    }
}

/** The green commit button, matching the reference. */
@Composable
internal fun ConfirmButton(
    label: String,
    enabled: Boolean,
    loading: Boolean,
    onClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(
                if (enabled || loading) IosColors.BuyGreen
                else IosColors.BuyGreen.copy(alpha = 0.30f)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled && !loading
            ) {
                Haptics.tap(haptics)
                onClick()
            }
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = loading,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "confirm"
        ) { busy ->
            if (busy) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    text = label,
                    color = Color.White.copy(alpha = if (enabled) 1f else 0.6f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
