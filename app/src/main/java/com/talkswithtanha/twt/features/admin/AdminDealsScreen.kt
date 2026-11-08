package com.talkswithtanha.twt.features.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.talkswithtanha.twt.core.data.DeskThread
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.model.Deal
import com.talkswithtanha.twt.core.model.DealStatus
import com.talkswithtanha.twt.core.model.ExchangeRate
import com.talkswithtanha.twt.features.settings.SettingsMetrics
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * The exchange desk on the phone: the same queues and the same buttons as the
 * panel's Deals and Chats, writing the same fields, so the dealer can run the
 * desk from either and both stay in step.
 *
 *  - New requests: accept (the member is then shown where to pay, and the
 *    dollars come off today's fund) or decline.
 *  - Needs you: check the money arrived, then send theirs with its reference.
 *  - Waiting on member, History, and the members' chats with the desk.
 *  - The desk open or closed, and today's fund.
 */
@Composable
fun AdminDealsScreen(
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit = {},
    /**
     * The dealer's Home: no way back (there is nowhere behind it), their
     * avatar for settings instead, and the queues on the app's tab bar
     * ([DeskShell]) rather than in a row of chips here.
     */
    deskMode: Boolean = false,
    userInitial: String = "",
    onOpenSettings: () -> Unit = {},
    /** The dealer's own line to Tanha and the team, pinned above the members' chats. */
    onOpenTeamChat: (() -> Unit)? = null,
    viewModel: AdminDealsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var localTab by rememberSaveable { mutableStateOf(DeskTab.REQUESTS) }
    val shellTab by DeskShell.selected.collectAsStateWithLifecycle()
    val tab = if (deskMode) shellTab else localTab
    if (deskMode) {
        androidx.compose.runtime.LaunchedEffect(state) {
            DeskShell.badges.value = DeskTab.entries
                .filter { it != DeskTab.HISTORY }
                .associate { DeskShell.route(it) to state.count(it) }
        }
    }
    var prompt by remember { mutableStateOf<DeskPrompt?>(null) }
    var editingRates by remember { mutableStateOf(false) }
    var accepting by remember { mutableStateOf<Deal?>(null) }
    var changingFor by remember { mutableStateOf<Deal?>(null) }
    val savedAccounts by viewModel.accounts.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(IosColors.Background)
            .statusBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = SettingsMetrics.pageInset, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!deskMode) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(IosColors.SecondaryBackground).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = IosColors.TextPrimary, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    if (deskMode && tab != DeskTab.REQUESTS) tab.title else "Exchange desk",
                    color = IosColors.TextPrimary,
                    fontSize = if (deskMode) 26.sp else 20.sp,
                    fontWeight = FontWeight.Bold
                )
                state.rate?.takeUnless { deskMode && tab == DeskTab.REQUESTS }?.let {
                    Text(
                        "Buy ${ExchangeRate.formatPaisa(it.buyPaisa)} · Sell ${ExchangeRate.formatPaisa(it.sellPaisa)}",
                        color = IosColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
            if (deskMode) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(IosColors.SecondaryBackground).clickable(onClick = onOpenSettings),
                    contentAlignment = Alignment.Center
                ) {
                    Text(userInitial.ifBlank { "•" }, color = IosColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (!deskMode) DeskBar(
            rate = state.rate,
            busy = state.workingId == "desk",
            onToggle = { viewModel.setTakingDeals(it) },
            onFund = { prompt = DeskPrompt.Fund }
        )

        // The dealer's Home has its own Done today tile.
        if (!deskMode && state.doneToday.isNotEmpty()) {
            Text(
                "Done today: " + state.doneToday.joinToString(" · ") { "${it.first} ${it.second}" },
                color = IosColors.TextSecondary,
                fontSize = 12.5.sp,
                modifier = Modifier.padding(horizontal = SettingsMetrics.pageInset, vertical = 4.dp)
            )
        }

        if (!deskMode) Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = SettingsMetrics.pageInset, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DeskTab.entries.forEach { t ->
                val count = state.count(t)
                val on = t == tab
                Row(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (on) Color.White else IosColors.SecondaryBackground)
                        .clickable { localTab = t }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(t.title, color = if (on) Color.Black else IosColors.TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                    if (count > 0 && t != DeskTab.HISTORY) {
                        Spacer(Modifier.width(6.dp))
                        val hot = t == DeskTab.REQUESTS || t == DeskTab.NEEDS || t == DeskTab.CHATS
                        Box(
                            Modifier.clip(RoundedCornerShape(999.dp)).background(if (hot) IosColors.SellRed else IosColors.TextSecondary).padding(horizontal = 7.dp, vertical = 1.dp)
                        ) { Text("$count", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }

        (state.error ?: state.notice)?.let { line ->
            Text(
                line,
                color = if (state.error != null) IosColors.SellRed else IosColors.BuyGreen,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = SettingsMetrics.pageInset, vertical = 4.dp).clickable { viewModel.dismissNotice() }
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(start = SettingsMetrics.pageInset, end = SettingsMetrics.pageInset, top = 6.dp, bottom = if (deskMode) 150.dp else 48.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (deskMode && tab == DeskTab.REQUESTS) {
                item {
                    DeskDashboard(
                        state = state,
                        busy = state.workingId == "desk",
                        onToggle = { viewModel.setTakingDeals(it) },
                        onRates = { editingRates = true },
                        onQuickFund = { viewModel.setFund(it) },
                        onCustomFund = { prompt = DeskPrompt.Fund },
                        onJump = { DeskShell.selected.value = it }
                    )
                }
                item {
                    Text("New requests", color = IosColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                }
            }
            if (tab == DeskTab.CHATS) {
                onOpenTeamChat?.let { open -> item(key = "team") { TeamChatRow(open) } }
                if (state.threads.isEmpty()) item { Empty("No chats yet. When a member messages the desk from the Exchange, it appears here.") }
                items(state.threads, key = { it.roomId }) { ThreadRow(it) { onOpenChat(it.roomId) } }
            } else {
                val deals = state.of(tab).let { if (tab == DeskTab.HISTORY) it else it.sortedBy { d -> d.createdAt } }
                if (deals.isEmpty()) {
                    item {
                        Empty(
                            when (tab) {
                                DeskTab.REQUESTS -> "No new requests. A deal a member asks for waits here for you to accept or decline it."
                                DeskTab.NEEDS -> "Nothing needs you. A payment lands here the moment a member taps I have paid."
                                DeskTab.WAITING -> "Nobody is mid-payment."
                                else -> "Nothing here yet."
                            }
                        )
                    }
                }
                items(deals, key = { it.id }) { deal ->
                    DeskDealCard(
                        deal = deal,
                        fundLeft = state.rate?.fundLeftUsd(),
                        working = state.workingId == deal.id,
                        onAccept = { accepting = deal },
                        onChangeAccount = { changingFor = deal },
                        onPrompt = { prompt = it }
                    )
                }
            }
        }
    }

    accepting?.let { deal ->
        AcceptDealSheet(
            deal = deal,
            saved = savedAccounts,
            onDismiss = { accepting = null },
            onAccept = { account, save -> accepting = null; viewModel.accept(deal, account, save) }
        )
    }

    changingFor?.let { deal ->
        AcceptDealSheet(
            deal = deal,
            saved = savedAccounts,
            changing = true,
            onDismiss = { changingFor = null },
            onAccept = { account, save -> changingFor = null; viewModel.changeAccount(deal, account, save) }
        )
    }

    if (editingRates) {
        RatesDialog(
            rate = state.rate,
            onDismiss = { editingRates = false },
            onSave = { b, sl -> editingRates = false; viewModel.setRates(b, sl) }
        )
    }

    when (val p = prompt) {
        is DeskPrompt.Arrived -> DeskProofDialog(
            title = "Did ${p.deal.payLabel()} arrive?",
            body = "Check your own bank or wallet app — never the member's screenshot or an SMS. Attach a screenshot of the message showing the money arrived. The member sees it too.",
            shotLabel = "Add the payment-received screenshot",
            referenceLabel = null,
            confirmLabel = "It arrived",
            onDismiss = { prompt = null },
            onConfirm = { _, proof -> prompt = null; viewModel.confirmPayment(p.deal, proof) }
        )
        is DeskPrompt.Release -> DeskProofDialog(
            title = "Sent ${p.deal.receiveLabel()}?",
            body = "Attach the screenshot of your transfer and paste its transaction id or hash. The member sees both.",
            shotLabel = "Add the money-sent screenshot",
            referenceLabel = "Transaction id or hash",
            confirmLabel = "Mark as sent",
            onDismiss = { prompt = null },
            onConfirm = { ref, proof -> prompt = null; viewModel.release(p.deal, ref, proof) }
        )
        else -> Unit
    }

    prompt?.takeIf { it !is DeskPrompt.Arrived && it !is DeskPrompt.Release }?.let { p ->
        DeskDialog(
            prompt = p,
            onDismiss = { prompt = null },
            onConfirm = { text ->
                when (p) {
                    is DeskPrompt.Decline -> viewModel.decline(p.deal, text)
                    is DeskPrompt.Cancel -> viewModel.cancel(p.deal, text)
                    is DeskPrompt.Dispute -> viewModel.dispute(p.deal, text)
                    is DeskPrompt.Arrived, is DeskPrompt.Release -> Unit
                    DeskPrompt.Fund -> text.filter { it.isDigit() }.toLongOrNull()?.let { viewModel.setFund(it) }
                }
                prompt = null
            }
        )
    }
}

/** A question the desk is asked before a write: a reason, a reference, an amount, or a plain yes. */
sealed interface DeskPrompt {
    data class Decline(val deal: Deal) : DeskPrompt
    data class Cancel(val deal: Deal) : DeskPrompt
    data class Dispute(val deal: Deal) : DeskPrompt
    data class Arrived(val deal: Deal) : DeskPrompt
    data class Release(val deal: Deal) : DeskPrompt
    data object Fund : DeskPrompt
}

@Composable
private fun DeskBar(rate: ExchangeRate?, busy: Boolean, onToggle: (Boolean) -> Unit, onFund: () -> Unit) {
    val open = rate?.isAcceptingDeals != false
    val left = rate?.fundLeftUsd()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = SettingsMetrics.pageInset, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(IosColors.SecondaryBackground)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(if (open) IosColors.BuyGreen else IosColors.SellRed))
        Spacer(Modifier.width(8.dp))
        Text(if (open) "Desk open" else "Desk closed", color = IosColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(6.dp))
        Switch(
            checked = open,
            enabled = !busy && rate != null,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedTrackColor = IosColors.BuyGreen)
        )
        Spacer(Modifier.weight(1f))
        Column(
            Modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                .clickable(enabled = rate != null, onClick = onFund).padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text("Today's fund", color = IosColors.TextSecondary, fontSize = 11.sp)
            Text(
                if (left == null) "Set" else "${"%,d".format(left)} of ${"%,d".format(rate.dailyFundUsd)}",
                color = IosColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun Empty(text: String) {
    Text(text, color = IosColors.TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(vertical = 24.dp))
}

/** Pinned: the dealer's private thread with Tanha, answered from the panel's Support chats. */
@Composable
private fun TeamChatRow(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(IosColors.SecondaryBackground)
            .border(1.dp, IosColors.BuyGreen.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(IosColors.BuyGreen), contentAlignment = Alignment.Center) {
            Text("TT", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Tanha · TWT team", color = IosColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("Your direct line to Tanha and Hafiz Sadiq", color = IosColors.TextSecondary, fontSize = 13.sp, maxLines = 1)
        }
        Text("📌", fontSize = 14.sp)
    }
}

@Composable
private fun ThreadRow(thread: DeskThread, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(IosColors.SecondaryBackground).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(IosColors.BuyGreen.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Text(thread.memberName.trim().take(1).uppercase(), color = IosColors.BuyGreen, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(thread.memberName, color = IosColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(thread.lastMessage.ifBlank { "No messages yet" }, color = IosColors.TextSecondary, fontSize = 13.sp, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            thread.lastMessageAt?.let { Text(timeOf(it), color = IosColors.TextSecondary, fontSize = 11.sp) }
            if (thread.unseen) {
                Spacer(Modifier.height(4.dp))
                Box(Modifier.size(10.dp).clip(CircleShape).background(IosColors.SellRed))
            }
        }
    }
}

@Composable
private fun DeskDealCard(
    deal: Deal,
    fundLeft: Long?,
    working: Boolean,
    onAccept: () -> Unit,
    onChangeAccount: () -> Unit = {},
    onPrompt: (DeskPrompt) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val uri = LocalUriHandler.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(IosColors.SecondaryBackground).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(deal.reference, color = IosColors.TextPrimary, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text(deal.userName.ifBlank { "Member" }, color = IosColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(deal.status.title, color = IosColors.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Text("${deal.payLabel()}  →  ${deal.receiveLabel()}", color = IosColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(
            "at ${ExchangeRate.formatPaisa(deal.lockedRatePaisa)} · opened ${timeOf(deal.createdAt)}" +
                (deal.sellerAccount?.let { " · pays into ${it.method} ${it.masked}" } ?: ""),
            color = IosColors.TextSecondary,
            fontSize = 12.5.sp
        )
        DealSteps(deal)

        deal.paymentReference?.let {
            Info("Their transaction id", it) { clipboard.setText(AnnotatedString(it)) }
        }
        // Every screenshot on the deal, so both sides can see what happened.
        val shots = listOfNotNull(
            deal.paymentProofUrl?.let { "Member paid" to it },
            deal.receiptProofUrl?.let { "You received" to it },
            deal.releaseProofUrl?.let { "You sent" to it }
        )
        if (shots.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                shots.forEach { (label, url) ->
                    Column(Modifier.weight(1f)) {
                        AsyncImage(
                            model = url,
                            contentDescription = label,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(if (shots.size == 1) 160.dp else 110.dp).clip(RoundedCornerShape(12.dp)).clickable { uri.openUri(url) }
                        )
                        Text(label, color = IosColors.TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
                    }
                }
            }
        }
        if (deal.status == DealStatus.RELEASING) {
            deal.receivingAccount?.let { acc ->
                Info("Send to · ${acc.method}", "${acc.accountTitle}\n${acc.accountNumber}") { clipboard.setText(AnnotatedString(acc.accountNumber)) }
            }
        }
        deal.releaseReference?.let { Info("Sent with ref", it) { clipboard.setText(AnnotatedString(it)) } }
        listOfNotNull(deal.approvedBy?.let { "Accepted by $it" }, deal.releasedBy?.let { "Sent by $it" })
            .takeIf { it.isNotEmpty() }
            ?.let { Text(it.joinToString(" · "), color = IosColors.TextSecondary, fontSize = 12.sp) }

        if (working) {
            CircularProgressIndicator(Modifier.size(22.dp).align(Alignment.CenterHorizontally), color = IosColors.BuyGreen, strokeWidth = 2.dp)
            return@Column
        }

        // One big button for the next step; the ways out sit small beside it.
        val short = deal.status == DealStatus.AWAITING_APPROVAL && fundLeft != null && fundLeft * 100 < deal.amountUsdCents
        when (deal.status) {
            DealStatus.AWAITING_APPROVAL -> PrimaryAction(if (short) "Today's fund is too low" else "Accept deal", enabled = !short, onClick = onAccept)
            DealStatus.PAYMENT_SENT -> PrimaryAction("Confirm the money arrived") { onPrompt(DeskPrompt.Arrived(deal)) }
            DealStatus.RELEASING -> PrimaryAction("I have sent it") { onPrompt(DeskPrompt.Release(deal)) }
            DealStatus.AWAITING_PAYMENT -> Text("Waiting for the member to pay.", color = IosColors.TextSecondary, fontSize = 13.sp)
            DealStatus.PAYMENT_APPROVED -> Text("Waiting for the member's receiving account.", color = IosColors.TextSecondary, fontSize = 13.sp)
            else -> Unit
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (deal.status) {
                DealStatus.AWAITING_APPROVAL -> Action("Decline", IosColors.SellRed) { onPrompt(DeskPrompt.Decline(deal)) }
                DealStatus.PAYMENT_SENT, DealStatus.RELEASING -> Action("Something's wrong", IosColors.SellRed) { onPrompt(DeskPrompt.Dispute(deal)) }
                DealStatus.AWAITING_PAYMENT -> {
                    Action("Change account", IosColors.BuyGreen, onClick = onChangeAccount)
                    Action("Cancel deal", IosColors.SellRed) { onPrompt(DeskPrompt.Cancel(deal)) }
                }
                DealStatus.PAYMENT_APPROVED -> Action("Cancel deal", IosColors.SellRed) { onPrompt(DeskPrompt.Cancel(deal)) }
                else -> Unit
            }
        }
    }
}

@Composable
private fun Info(label: String, value: String, onCopy: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.05f)).clickable(onClick = onCopy).padding(10.dp)
    ) {
        Text("$label · tap to copy", color = IosColors.TextSecondary, fontSize = 11.sp)
        Text(value, color = IosColors.TextPrimary, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun Action(text: String, tint: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = if (enabled) 0.18f else 0.08f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) { Text(text, color = if (enabled) tint else IosColors.TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun DeskDialog(prompt: DeskPrompt, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val (title, body, field, confirm) = when (prompt) {
        is DeskPrompt.Decline -> Quad("Decline ${prompt.deal.reference}?", "The member is told, with your reason.", "Reason", "Decline")
        is DeskPrompt.Cancel -> Quad("Cancel ${prompt.deal.reference}?", "The member is told, with your reason. Its dollars go back into today's fund.", "Reason", "Cancel deal")
        is DeskPrompt.Dispute -> Quad("Move ${prompt.deal.reference} to review?", "Nothing moves until it is decided.", "What is wrong", "Move to review")
        is DeskPrompt.Arrived -> Quad("Did ${prompt.deal.payLabel()} arrive?", "Check your bank or wallet app — never a screenshot or SMS. The member is then asked where to send theirs.", null, "It arrived")
        is DeskPrompt.Release -> Quad("Sent ${prompt.deal.receiveLabel()}?", "Paste the transaction id or hash you sent it with. The member sees it.", "Transaction id or hash", "Mark as sent")
        DeskPrompt.Fund -> Quad("Today's fund", "How many USDT the desk will deal today. Accepted deals come off it.", "USDT", "Save")
    }
    var text by remember { mutableStateOf(if (prompt is DeskPrompt.Decline) "The desk cannot take this deal right now." else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(body)
                field?.let {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { v -> text = v },
                        label = { Text(it) },
                        singleLine = prompt !is DeskPrompt.Decline && prompt !is DeskPrompt.Cancel,
                        keyboardOptions = if (prompt == DeskPrompt.Fund) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = field == null || text.isNotBlank(), onClick = { onConfirm(text.trim()) }) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not yet") } }
    )
}

private data class Quad(val a: String, val b: String, val c: String?, val d: String)

private fun timeOf(date: java.util.Date): String = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(date)
