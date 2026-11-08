package com.talkswithtanha.twt.features.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.model.Deal
import com.talkswithtanha.twt.core.model.DealStatus
import com.talkswithtanha.twt.core.model.ExchangeRate

/**
 * The top of the dealer's Home: what the day looks like and the three things
 * they set — open or closed, today's rate, today's fund — each one tap away.
 * Nothing about signals or promotions; this account only runs the desk.
 */
@Composable
internal fun DeskDashboard(
    state: AdminDealsUiState,
    busy: Boolean,
    onToggle: (Boolean) -> Unit,
    onRates: () -> Unit,
    onQuickFund: (Long) -> Unit,
    onCustomFund: () -> Unit,
    onJump: (DeskTab) -> Unit
) {
    val rate = state.rate
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Today at a glance — each tile opens its queue.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Stat("New", state.count(DeskTab.REQUESTS), hot = true, Modifier.weight(1f)) { onJump(DeskTab.REQUESTS) }
            Stat("Needs you", state.count(DeskTab.NEEDS), hot = true, Modifier.weight(1f)) { onJump(DeskTab.NEEDS) }
            Stat("Waiting", state.count(DeskTab.WAITING), hot = false, Modifier.weight(1f)) { onJump(DeskTab.WAITING) }
            Stat("Done today", state.doneToday.sumOf { it.second }, hot = false, Modifier.weight(1f)) { onJump(DeskTab.HISTORY) }
        }

        // Open or closed.
        val open = rate?.isAcceptingDeals != false
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(if (open) IosColors.BuyGreen else IosColors.SellRed))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (open) "Desk is open" else "Desk is closed", color = IosColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (open) "Members can ask for new deals." else "No new deals. Open ones carry on.",
                        color = IosColors.TextSecondary, fontSize = 12.5.sp
                    )
                }
                Switch(
                    checked = open,
                    enabled = !busy && rate != null,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(checkedTrackColor = IosColors.BuyGreen)
                )
            }
        }

        // Today's rate.
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Label("Today's rate · 1 USD")
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        RateFigure("Buy", rate?.let { ExchangeRate.formatPaisa(it.buyPaisa) } ?: "—", IosColors.BuyGreen)
                        RateFigure("Sell", rate?.let { ExchangeRate.formatPaisa(it.sellPaisa) } ?: "—", IosColors.SellRed)
                    }
                }
                Pill("Change", enabled = !busy && rate != null, onClick = onRates)
            }
        }

        // Today's fund.
        Card {
            val left = rate?.fundLeftUsd()
            val total = rate?.dailyFundUsd ?: 0
            Label("Today's fund · USDT")
            if (left == null) {
                Text("Not set — no daily limit", color = IosColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            } else {
                Text("${"%,d".format(left)} left of ${"%,d".format(total)}", color = IosColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                val used = if (total > 0) ((total - left).toFloat() / total).coerceIn(0f, 1f) else 0f
                Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.1f))) {
                    Box(Modifier.fillMaxWidth(used).height(6.dp).clip(RoundedCornerShape(3.dp)).background(if (left == 0L) IosColors.SellRed else IosColors.BuyGreen))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Set today's fund", color = IosColors.TextSecondary, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                listOf(1_000L, 5_000L, 10_000L).forEach { amount ->
                    Pill("%,d".format(amount), enabled = !busy && rate != null, selected = total == amount && left != null) { onQuickFund(amount) }
                }
                Pill("Other", enabled = !busy && rate != null, onClick = onCustomFund)
            }
        }
    }
}

/**
 * Where a deal is, as four segments: accepted, member paid, money confirmed,
 * dollars sent. Filled up to the step it has reached.
 */
@Composable
internal fun DealSteps(deal: Deal) {
    val done = when (deal.status) {
        DealStatus.AWAITING_APPROVAL -> 0
        DealStatus.AWAITING_PAYMENT -> 1
        DealStatus.PAYMENT_SENT -> 2
        DealStatus.PAYMENT_APPROVED, DealStatus.RELEASING -> 3
        DealStatus.COMPLETED -> 4
        DealStatus.CANCELLED, DealStatus.DISPUTED -> -1
    }
    if (done < 0) return
    val labels = listOf("Accepted", "Member paid", "Confirmed", "Sent")
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            labels.indices.forEach { i ->
                Box(
                    Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp))
                        .background(
                            when {
                                i < done -> IosColors.BuyGreen
                                i == done -> IosColors.BuyGreen.copy(alpha = 0.35f)
                                else -> Color.White.copy(alpha = 0.10f)
                            }
                        )
                )
            }
        }
        Row(Modifier.padding(top = 4.dp)) {
            labels.forEachIndexed { i, l ->
                Text(
                    l,
                    color = if (i < done) IosColors.TextPrimary else IosColors.TextSecondary,
                    fontSize = 10.5.sp,
                    fontWeight = if (i == done) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** The one thing to do next on a deal, full width, so it cannot be missed. */
@Composable
internal fun PrimaryAction(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) IosColors.BuyGreen else Color.White.copy(alpha = 0.08f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (enabled) Color.Black else IosColors.TextSecondary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

/** Buy and sell for today, both at once. */
@Composable
internal fun RatesDialog(rate: ExchangeRate?, onDismiss: () -> Unit, onSave: (Double, Double) -> Unit) {
    fun rupees(p: Long?) = p?.let { "%.2f".format(it / 100.0) }.orEmpty()
    var buy by remember { mutableStateOf(rupees(rate?.buyPaisa)) }
    var sell by remember { mutableStateOf(rupees(rate?.sellPaisa)) }
    val b = buy.toDoubleOrNull()
    val s = sell.toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Today's rate") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("PKR for 1 USD. Members see the new rate straight away; deals already open keep theirs.")
                OutlinedTextField(buy, { buy = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Buy — members pay") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(sell, { sell = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Sell — members receive") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                if (b != null && s != null && s > b) Text("Sell cannot be above buy.", color = IosColors.SellRed, fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(enabled = b != null && s != null && b > 0 && s > 0 && s <= b, onClick = { onSave(b!!, s!!) }) { Text("Save rates") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } }
    )
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(IosColors.SecondaryBackground).padding(14.dp)) { content() }
}

@Composable
private fun Label(text: String) = Text(text, color = IosColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))

@Composable
private fun RateFigure(label: String, value: String, tint: Color) {
    Column {
        Text(label, color = tint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(value, color = IosColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Stat(label: String, value: Int, hot: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(IosColors.SecondaryBackground).clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("$value", color = if (hot && value > 0) IosColors.SellRed else IosColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(label, color = IosColors.TextSecondary, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun Pill(text: String, enabled: Boolean = true, selected: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) Color.White else Color.Transparent)
            .border(1.dp, Color.White.copy(alpha = if (enabled) 0.25f else 0.1f), RoundedCornerShape(999.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text, color = if (selected) Color.Black else if (enabled) IosColors.TextPrimary else IosColors.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
