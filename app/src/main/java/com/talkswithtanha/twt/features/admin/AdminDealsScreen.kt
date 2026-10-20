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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.model.Deal
import com.talkswithtanha.twt.core.model.DealSide
import com.talkswithtanha.twt.core.model.DealStatus
import com.talkswithtanha.twt.features.settings.SettingsMetrics

/**
 * The exchange, from the other side of the counter.
 *
 * This is the screen the marketplace area unlocks: somebody has sent money and
 * is waiting to be told it arrived, or has been approved and is waiting for
 * their dollars. Both are people standing still until a human acts, which is
 * exactly the work that should not have to wait for a laptop.
 *
 * The buttons say "Money arrived" and "I've sent theirs" rather than "approve"
 * and "release". The person tapping them is confirming something they did in
 * their banking app a moment ago, and the question they are answering is not
 * "do you approve" — it is "did the money turn up". Each one asks once before
 * it writes, because every one of them moves real money.
 */
@Composable
fun AdminDealsScreen(
    onBack: () -> Unit,
    viewModel: AdminDealsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<PendingDealAction?>(null) }

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
            Text("Marketplace", color = IosColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(40.dp))
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

            if (state.deals.isEmpty()) {
                item {
                    Text(
                        text = "No deals yet. One appears here the moment a member opens an exchange in the app.",
                        color = IosColors.TextSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }

            if (state.needsAction.isNotEmpty()) {
                item { Label("Waiting on you") }
                items(state.needsAction, key = { it.id }) { deal ->
                    DealCard(deal, state.workingId == deal.id) { pending = it }
                }
            }
            if (state.rest.isNotEmpty()) {
                item { Label("Everything else") }
                items(state.rest, key = { it.id }) { deal ->
                    DealCard(deal, state.workingId == deal.id) { pending = it }
                }
            }
        }
    }

    pending?.let { action ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(action.question) },
            text = { Text(action.detail) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.apply(action.deal, action.status, action.note)
                    pending = null
                }) {
                    Text(
                        action.confirmTitle,
                        color = if (action.isDestructive) IosColors.SellRed else IosColors.BuyGreen
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) { Text("Not yet") }
            }
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text = text.uppercase(),
        color = IosColors.TextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 12.dp)
    )
}

@Composable
private fun DealCard(deal: Deal, isWorking: Boolean, onAction: (PendingDealAction) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF141414))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = deal.reference,
                color = IosColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.size(8.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (deal.side == DealSide.BUY) IosColors.BuyGreen else IosColors.SellRed)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (deal.side == DealSide.BUY) "BUY USD" else "SELL USD",
                    color = Color.Black,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black
                )
            }
            Spacer(Modifier.weight(1f))
            if (isWorking) {
                CircularProgressIndicator(
                    color = IosColors.TextSecondary,
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.size(8.dp))
            }
            Text(deal.status.title, color = IosColors.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        Text(
            text = deal.userName.ifEmpty { "Member" },
            color = IosColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = "${deal.payLabel()} → ${deal.receiveLabel()}",
            color = IosColors.TextSecondary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 2.dp)
        )
        deal.paymentReference?.takeIf { it.isNotBlank() }?.let {
            Text("Their payment ref: $it", color = IosColors.TextSecondary, fontSize = 12.sp)
        }
        deal.receivingAccount?.let {
            Text(
                text = "Send to: ${it.accountTitle} · ${it.accountNumber} (${it.method})",
                color = IosColors.TextSecondary,
                fontSize = 12.sp
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            when (deal.status) {
                DealStatus.PAYMENT_SENT -> Action("Money arrived", IosColors.BuyGreen) {
                    onAction(
                        PendingDealAction(
                            deal = deal,
                            status = DealStatus.PAYMENT_APPROVED,
                            note = "Payment confirmed by Trade with Tanha",
                            question = "Confirm their payment arrived?",
                            detail = "${deal.userName} says they sent ${deal.payLabel()}. Check your account before confirming — they are asked for their dollars next.",
                            confirmTitle = "It arrived"
                        )
                    )
                }
                DealStatus.PAYMENT_APPROVED, DealStatus.RELEASING -> Action("I've sent theirs", IosColors.BuyGreen) {
                    onAction(
                        PendingDealAction(
                            deal = deal,
                            status = DealStatus.COMPLETED,
                            note = "Funds released",
                            question = "Mark this deal as finished?",
                            detail = "Only once ${deal.receiveLabel()} has actually left your account. This closes the deal for them.",
                            confirmTitle = "Sent — close it"
                        )
                    )
                }
                DealStatus.DISPUTED -> Text(
                    text = "Raised for review — settle this one with them directly.",
                    color = IosColors.Warning,
                    fontSize = 12.sp
                )
                else -> Unit
            }

            Spacer(Modifier.weight(1f))

            if (deal.status != DealStatus.COMPLETED && deal.status != DealStatus.CANCELLED) {
                Action("Cancel", IosColors.SellRed) {
                    onAction(
                        PendingDealAction(
                            deal = deal,
                            status = DealStatus.CANCELLED,
                            note = "Cancelled by Trade with Tanha — cancelled from the app",
                            question = "Cancel this deal?",
                            detail = "The member is told it is off. If they have already sent money, sort that out with them first.",
                            confirmTitle = "Cancel the deal",
                            isDestructive = true
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun Action(text: String, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp)
    ) {
        Text(text, color = tint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Every write on this screen moves somebody's money, so each one is described in
 * full before it happens rather than being a button that fires.
 */
private data class PendingDealAction(
    val deal: Deal,
    val status: DealStatus,
    val note: String,
    val question: String,
    val detail: String,
    val confirmTitle: String,
    val isDestructive: Boolean = false
)
