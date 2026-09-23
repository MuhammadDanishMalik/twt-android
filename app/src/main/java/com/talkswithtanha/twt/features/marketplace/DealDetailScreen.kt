package com.talkswithtanha.twt.features.marketplace

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Flag
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
    onOpenChat: () -> Unit,
    viewModel: DealDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    var confirming by remember { mutableStateOf(false) }

    val deal = state.deal

    LaunchedEffect(state.error) {
        if (state.error != null) Haptics.failure(haptics)
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
            item { StatusHero(deal, Modifier.staggeredAppear(0)) }
            item { StepTrack(deal.status, Modifier.staggeredAppear(1)) }

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
            item { MessageSellerRow(Modifier.staggeredAppear(4), onOpenChat) }

            if (deal.events.isNotEmpty()) {
                item {
                    Column(Modifier.staggeredAppear(5)) {
                        SectionLabel("ACTIVITY")
                        Spacer(Modifier.height(8.dp))
                        ActivityTimeline(deal)
                    }
                }
            }

            item { FooterActions(deal, onOpenChat) }
        }

        // ── The one move this app can make ───────────────────────────────
        if (deal.status.memberCanReportPayment) {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                ConfirmButton(
                    label = "I have sent the payment",
                    enabled = !state.reportingPayment,
                    loading = state.reportingPayment,
                    onClick = { confirming = true }
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
            onDismiss = { confirming = false },
            onConfirm = { transactionId ->
                viewModel.reportPayment(transactionId)
                confirming = false
            }
        )
    }
}

/** What is happening right now, in a sentence. */
@Composable
private fun StatusHero(deal: Deal, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = deal.status.title,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = deal.status.detail(deal.side),
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

/**
 * Payment, Approval, Account, Release, Done.
 *
 * A deal that went sideways has no position on this track, so cancelled and
 * disputed show their own line instead of a dot stranded between two steps.
 */
@Composable
private fun StepTrack(status: DealStatus, modifier: Modifier = Modifier) {
    val step = status.stepIndex

    if (step == null) {
        Row(
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(IosColors.Warning.copy(alpha = 0.10f))
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Flag,
                contentDescription = null,
                tint = IosColors.Warning,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = status.title,
                color = IosColors.Warning,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        return
    }

    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .padding(vertical = 14.dp, horizontal = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        DealStatus.happyPath.forEachIndexed { index, label ->
            val done = index < step
            val current = index == step

            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.Center) {
                    // The connector sits behind the dot and stops at the last
                    // step, so the track does not trail off the right edge.
                    if (index != DealStatus.happyPath.lastIndex) {
                        Box(
                            Modifier
                                .padding(start = 44.dp)
                                .width(44.dp)
                                .height(2.dp)
                                .background(
                                    if (done) IosColors.BuyGreen
                                    else Color.White.copy(alpha = 0.12f)
                                )
                        )
                    }
                    StepDot(done = done, current = current)
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    text = label,
                    color = when {
                        current -> Color.White
                        done -> Color.White.copy(alpha = 0.6f)
                        else -> Color.White.copy(alpha = 0.3f)
                    },
                    fontSize = 9.sp,
                    fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun StepDot(done: Boolean, current: Boolean) {
    val fill by animateColorAsState(
        targetValue = when {
            done -> IosColors.BuyGreen
            current -> Color.White
            else -> Color.White.copy(alpha = 0.14f)
        },
        animationSpec = Motion.gentle(),
        label = "dot"
    )
    Box(
        Modifier
            .size(if (current) 18.dp else 16.dp)
            .clip(CircleShape)
            .background(fill),
        contentAlignment = Alignment.Center
    ) {
        if (done) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(10.dp)
            )
        }
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
        SectionLabel("SEND TO ${account.method.uppercase().ifBlank { "THE SELLER" }}")
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
                fontFamily = FontFamily.Monospace
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
            fontFamily = if (monospaced) FontFamily.Monospace else FontFamily.Default
        )
    }
}

@Composable
private fun MessageSellerRow(modifier: Modifier = Modifier, onOpenChat: () -> Unit) {
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
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(IosColors.Accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Chat,
                contentDescription = null,
                tint = IosColors.Accent,
                modifier = Modifier.size(16.dp)
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = "Message the seller",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Anything about this deal goes here",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 12.sp
            )
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
private fun FooterActions(deal: Deal, onOpenChat: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Cancelling is Tanha's to do — the rules refuse it from a client — so
        // this opens the conversation rather than pretending to a power the app
        // does not have and failing silently.
        if (deal.status.isActive) {
            FooterAction(
                icon = Icons.Outlined.Close,
                label = "Cancel deal",
                tint = IosColors.SellRed,
                modifier = Modifier.weight(1f)
            ) {
                Haptics.tap(haptics)
                onOpenChat()
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
                        fontFamily = FontFamily.Monospace
                    )
                }
                BasicTextField(
                    value = transactionId,
                    onValueChange = { transactionId = it.filter(Char::isLetterOrDigit).take(32) },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace
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

            Spacer(Modifier.weight(1f))

            ConfirmButton(
                label = "I have sent the payment",
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
