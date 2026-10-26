package com.talkswithtanha.twt.features.marketplace

import com.talkswithtanha.twt.core.designsystem.WebFonts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.talkswithtanha.twt.core.designsystem.Haptics
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.designsystem.Motion
import com.talkswithtanha.twt.core.designsystem.staggeredAppear
import com.talkswithtanha.twt.core.model.Deal
import com.talkswithtanha.twt.core.model.DealStatus
import com.talkswithtanha.twt.core.model.Turn
import com.talkswithtanha.twt.core.model.progress
import com.talkswithtanha.twt.core.model.ExchangeRate
import java.text.SimpleDateFormat
import java.util.Locale

private val Surface = Color(0xFF1C1C1E)
private val FieldFill = Color(0xFF141416)

/**
 * One deal, from opened to done.
 *
 * The screen a member sits on while their money is somewhere between them and
 * Tanha, which is the most anxious minute the app has. Everything here answers
 * one of three questions: where has it got to, what do I do next, and who do I
 * talk to if it stops.
 *
 * The tracker is first because "where has it got to" is the one a member opens
 * this screen to ask. Payment instructions come next and only while they are
 * relevant; after that the deal is Tanha's move and the screen says so rather
 * than leaving a button that would do nothing.
 */
@Composable
fun DealDetailScreen(
    onClose: () -> Unit,
    /** Opens the member's chat with the dealer: the room id is theirs. */
    onOpenChat: (roomId: String) -> Unit,
    viewModel: DealDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    var confirming by remember { mutableStateOf(false) }
    var addingAccount by remember { mutableStateOf(false) }
    var askCancel by remember { mutableStateOf(false) }

    val deal = state.deal
    val seller by viewModel.seller.collectAsStateWithLifecycle()
    val openChat = { deal?.let { onOpenChat(com.talkswithtanha.twt.core.firebase.FirestorePaths.ChatRoom.support(it.userId)) }; Unit }

    LaunchedEffect(state.error) {
        if (state.error != null) Haptics.failure(haptics)
    }

    // The sheets close when the server says the deal moved on, not when the
    // button is tapped: success is the snapshot, never the request.
    LaunchedEffect(deal?.status) {
        if (deal?.status != DealStatus.AWAITING_PAYMENT) confirming = false
        if (deal?.status != DealStatus.PAYMENT_APPROVED) addingAccount = false
        if (deal?.status?.isTerminal == true) Haptics.success(haptics)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(IosColors.Background)
            .statusBarsPadding()
    ) {
        // ── Header ───────────────────────────────────────────────────────
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Surface)
                    .align(Alignment.CenterStart)
                    .clickable {
                        Haptics.tap(haptics)
                        onClose()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(17.dp)
                )
            }
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "YOUR DEAL",
                    color = Color.White.copy(alpha = 0.38f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.1.sp
                )
                Text(
                    text = deal?.reference ?: "—",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (deal == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Loading this deal…",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 15.sp
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val progress = deal.progress()
            if (!state.online) item { ReconnectingBar() }
            if (progress.turn != Turn.CLOSED) item { DealStepper(progress, Modifier.staggeredAppear(0)) }
            item {
                if (deal.status == DealStatus.COMPLETED) {
                    FundsSentCard(
                        amount = deal.receiveLabel(),
                        to = deal.receivingAccount?.method ?: "account",
                        onFeedback = openChat,
                        modifier = Modifier.staggeredAppear(1)
                    )
                } else {
                    TurnCard(progress, state.sound, Modifier.staggeredAppear(1))
                }
            }

            // Only while there is a payment to make. Past that it is history,
            // and leaving it at the top implies there is still something to do.
            if (deal.status == DealStatus.AWAITING_PAYMENT) {
                deal.sellerAccount?.let { account ->
                    item {
                        PayIntoCard(
                            deal = deal,
                            modifier = Modifier.staggeredAppear(2)
                        ) { label, value ->
                            clipboard.setText(AnnotatedString(value))
                            Haptics.success(haptics)
                        }
                    }
                }
            }

            item { SummaryCard(deal, Modifier.staggeredAppear(3)) }
            item { DealerRow(seller, Modifier.staggeredAppear(4), openChat) }

            if (deal.events.isNotEmpty()) {
                item {
                    Column(Modifier.staggeredAppear(5)) {
                        SectionLabel("ACTIVITY")
                        Spacer(Modifier.height(8.dp))
                        ActivityTimeline(deal)
                    }
                }
            }

            item { FooterActions(deal, openChat, onCancel = { askCancel = true }) }
        }

        // ── The member's move, when it is their turn ─────────────────────
        if (deal.status.memberCanReportPayment) {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                ConfirmButton(
                    label = "I have paid",
                    enabled = state.busy == null && state.online,
                    loading = state.reportingPayment,
                    onClick = { confirming = true }
                )
            }
        }
        if (deal.status == DealStatus.PAYMENT_APPROVED) {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                ConfirmButton(
                    label = "Add where to receive ${deal.receiveLabel()}",
                    enabled = state.busy == null && state.online,
                    loading = state.busy == DealAction.ACCOUNT,
                    onClick = { addingAccount = true }
                )
            }
        }

        state.error?.let { message ->
            Text(
                text = message,
                color = IosColors.SellRed,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }
    }

    if (confirming && deal != null) {
        ConfirmPaymentSheet(
            deal = deal,
            isSubmitting = state.reportingPayment,
            error = state.error,
            onDismiss = {
                confirming = false
                viewModel.clearError()
            },
            onConfirm = { transactionId -> viewModel.reportPayment(transactionId) }
        )
    }

    if (addingAccount) {
        ReceiveAccountSheet(
            isSubmitting = state.busy == DealAction.ACCOUNT,
            error = state.error,
            onDismiss = {
                addingAccount = false
                viewModel.clearError()
            },
            onSave = viewModel::setReceivingAccount
        )
    }

    if (askCancel) {
        AlertDialog(
            onDismissRequest = { askCancel = false },
            title = { Text("Cancel this deal?") },
            text = { Text("Nothing has moved yet, so nothing needs to be returned.") },
            confirmButton = {
                TextButton(enabled = state.online, onClick = {
                    askCancel = false
                    viewModel.cancel()
                }) { Text("Cancel deal", color = IosColors.SellRed) }
            },
            dismissButton = { TextButton(onClick = { askCancel = false }) { Text("Keep it") } }
        )
    }
}

/**
 * Where to send the money.
 *
 * Every line copyable, because the alternative is a member retyping an account
 * number from one app into another — which is exactly where a digit gets
 * dropped and the transfer lands nowhere.
 */
@Composable
private fun PayIntoCard(
    deal: Deal,
    modifier: Modifier = Modifier,
    onCopy: (String, String) -> Unit
) {
    val account = deal.sellerAccount ?: return

    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.talkswithtanha.twt.core.designsystem.components.PaymentMethodIcon(account.method, size = 20.dp)
            SectionLabel("SEND TO ${account.method.uppercase().ifBlank { "THE SELLER" }}")
        }
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Surface)
        ) {
            CopyRow("AMOUNT", deal.payLabel(), onCopy)
            RowDivider()
            CopyRow("MOBILE NUMBER", account.accountNumber, onCopy)
            if (account.accountTitle.isNotBlank()) {
                RowDivider()
                CopyRow("ACCOUNT TITLE", account.accountTitle, onCopy)
            }
            RowDivider()
            CopyRow("REFERENCE", deal.reference, onCopy)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Send the exact amount and include the reference so Tanha can match " +
                "your transfer quickly.",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
    }
}

@Composable
private fun CopyRow(label: String, value: String, onCopy: (String, String) -> Unit) {
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(1400)
            copied = false
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onCopy(label, value)
                copied = true
            }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.38f),
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.7.sp
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = value,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = WebFonts.Mono
            )
        }
        AnimatedContent(
            targetState = copied,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "copied"
        ) { wasCopied ->
            Icon(
                if (wasCopied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                contentDescription = if (wasCopied) "Copied" else "Copy $label",
                tint = if (wasCopied) IosColors.BuyGreen else Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun SummaryCard(deal: Deal, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        SummaryLine("Reference", deal.reference, monospaced = true)
        SummaryLine("You pay", deal.payLabel())
        SummaryLine("You receive", deal.receiveLabel())
        SummaryLine("Locked rate", ExchangeRate.formatPaisa(deal.lockedRatePaisa))
        SummaryLine("Opened", openedAt(deal))
        deal.paymentReference?.takeIf { it.isNotBlank() }?.let {
            SummaryLine("Transaction ID", it, monospaced = true)
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String, monospaced: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            color = Color.White,
            fontSize = if (monospaced) 13.sp else 14.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = if (monospaced) WebFonts.Mono else WebFonts.Body
        )
    }
}

@Composable
private fun DealerRow(
    seller: com.talkswithtanha.twt.core.model.MarketplaceSeller,
    modifier: Modifier = Modifier,
    onOpenChat: () -> Unit
) {
    // The person on the other side of the deal, with his face and the badge,
    // so a member knows exactly who to message — and one tap opens the chat.
    val haptics = LocalHapticFeedback.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .clickable {
                Haptics.tap(haptics)
                onOpenChat()
            }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            com.talkswithtanha.twt.core.designsystem.components.MemberAvatar(
                photoUrl = seller.photoUrl,
                name = seller.displayName,
                size = 46.dp
            )
            Box(
                Modifier
                    .size(11.dp)
                    .clip(CircleShape)
                    .background(IosColors.BuyGreen)
                    .border(2.dp, Surface, CircleShape)
            )
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = seller.displayName,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                if (seller.isVerified) {
                    Spacer(Modifier.width(5.dp))
                    Icon(
                        androidx.compose.material.icons.Icons.Filled.Verified,
                        contentDescription = "Verified",
                        tint = IosColors.Accent,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            Text(
                text = (if (seller.isVerified) "Verified dealer" else "Your dealer") +
                    (seller.releaseMinutes?.let { " · usually replies in $it min" } ?: " · message about this deal"),
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 12.sp
            )
        }
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(IosColors.Accent)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(Icons.Outlined.Chat, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            Text("Chat", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ActivityTimeline(deal: Deal) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Newest first: what just happened is what a member came back to check.
        deal.events.sortedByDescending { it.timestamp }.forEach { event ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .padding(top = 5.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.35f))
                )
                Column {
                    Text(
                        text = event.note,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    Text(
                        text = timeOnly(event.timestamp.time),
                        color = Color.White.copy(alpha = 0.35f),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun FooterActions(deal: Deal, onOpenChat: () -> Unit, onCancel: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // A member may back out only before paying — the rules allow exactly
        // that. After it, cancelling is a conversation with the desk.
        if (deal.status == DealStatus.AWAITING_PAYMENT) {
            FooterAction(
                icon = Icons.Outlined.Close,
                label = "Cancel deal",
                tint = IosColors.SellRed,
                modifier = Modifier.weight(1f)
            ) {
                Haptics.tap(haptics)
                onCancel()
            }
        }
        FooterAction(
            icon = Icons.Outlined.Flag,
            label = "Report a problem",
            tint = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.weight(1f)
        ) {
            Haptics.tap(haptics)
            onOpenChat()
        }
    }
}

@Composable
private fun FooterAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(text = label, color = tint, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * Declaring the payment sent.
 *
 * A sheet rather than an inline field, because this is the irreversible step:
 * it moves the deal into Tanha's queue and cannot be taken back from the phone.
 * The warning is blunt on purpose — a false confirmation is somebody claiming
 * to have sent money they have not sent, and that is the fraud this flow has.
 */
@Composable
private fun ConfirmPaymentSheet(
    deal: Deal,
    isSubmitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var transactionId by remember { mutableStateOf("") }
    val haptics = LocalHapticFeedback.current
    val account = deal.sellerAccount

    Dialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(IosColors.Background)
                .statusBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
        ) {
            Box(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
                Text(
                    text = "Cancel",
                    color = IosColors.Accent,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .clickable(enabled = !isSubmitting) {
                            Haptics.tap(haptics)
                            onDismiss()
                        }
                )
                Text(
                    text = "Confirm payment",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            Spacer(Modifier.height(24.dp))

            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "YOU SENT",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.1.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = deal.payLabel(),
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )
                account?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = listOfNotNull(
                            it.accountTitle.takeIf(String::isNotBlank),
                            it.accountNumber.takeIf(String::isNotBlank)
                        ).joinToString(" · "),
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(Modifier.height(30.dp))

            Text(
                text = "TRANSACTION ID",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.1.sp
            )
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(FieldFill)
                    .padding(horizontal = 14.dp, vertical = 16.dp)
            ) {
                if (transactionId.isEmpty()) {
                    Text(
                        text = "e.g. 4829173645",
                        color = Color.White.copy(alpha = 0.25f),
                        fontSize = 16.sp,
                        fontFamily = WebFonts.Mono
                    )
                }
                BasicTextField(
                    value = transactionId,
                    onValueChange = { transactionId = it.filter(Char::isLetterOrDigit).take(32) },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 16.sp,
                        fontFamily = WebFonts.Mono
                    ),
                    cursorBrush = SolidColor(IosColors.Accent),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Find this in your ${account?.method ?: "payment"} receipt. Tanha uses " +
                    "it to match your transfer.",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 12.sp,
                lineHeight = 17.sp
            )

            Spacer(Modifier.height(20.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(IosColors.SellRed.copy(alpha = 0.08f))
                    .padding(13.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Icon(
                    Icons.Outlined.Flag,
                    contentDescription = null,
                    tint = IosColors.SellRed.copy(alpha = 0.8f),
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "Only confirm if you have actually sent the money. Falsely " +
                        "confirming a payment will get your account suspended.",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }

            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = IosColors.SellRed, fontSize = 13.sp, lineHeight = 18.sp)
            }

            Spacer(Modifier.weight(1f))

            ConfirmButton(
                label = "I have paid",
                // The id is the whole point of the sheet, so it is required:
                // a report with nothing to match against lands on Tanha as a
                // claim he has to chase.
                enabled = transactionId.isNotBlank() && !isSubmitting,
                loading = isSubmitting,
                onClick = { onConfirm(transactionId) }
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RowDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(0.5.dp)
            .background(Color.White.copy(alpha = 0.08f))
    )
}

@Composable
private fun SectionLabel(title: String) {
    Text(
        text = title,
        color = Color.White.copy(alpha = 0.4f),
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.1.sp
    )
}

/** Built per call — see the note in `ChatScreens.formatTime`. */
private fun openedAt(deal: Deal): String =
    SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(deal.createdAt)

private fun timeOnly(epochMillis: Long): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(java.util.Date(epochMillis))
