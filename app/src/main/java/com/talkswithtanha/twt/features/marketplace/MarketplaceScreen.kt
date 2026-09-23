package com.talkswithtanha.twt.features.marketplace

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalHapticFeedback
import com.talkswithtanha.twt.core.designsystem.AnimatedNumber
import com.talkswithtanha.twt.core.designsystem.Haptics
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.designsystem.pressScale
import com.talkswithtanha.twt.core.designsystem.staggeredAppear
import com.talkswithtanha.twt.core.model.Deal
import com.talkswithtanha.twt.core.model.ExchangeRate
import com.talkswithtanha.twt.core.model.MarketplaceSeller
import java.util.Date
import java.util.concurrent.TimeUnit

/** The card fill this screen uses throughout. */
private val Surface = Color(0xFF212121)

/** The rate card sits a shade above everything else, so it reads as the subject. */
private val RateCardTop = Color(0xFF303032)
private val RateCardBottom = Color(0xFF242426)

/**
 * The primary action's violet.
 *
 * Brightest in the middle rather than at one end, which is what makes it read
 * as lit rather than as a flat fill with a tint.
 */
private val ActionGradient = listOf(
    Color(0xFF6C2BE1),
    Color(0xFF9151FF),
    Color(0xFF7E3FEB)
)

/** The tinted disc behind a step number. */
private val StepChip = Color(0xFF1D2E3C)

/**
 * The USD/PKR desk.
 *
 * The rate is a number Tanha types into the admin panel — deliberately not
 * derived from a market feed, because he sets the spread he is willing to
 * honour and the app must show exactly that. It also must not move under a
 * member who is deciding whether to trade at it, which is why opening a deal
 * locks the rate at that moment.
 *
 * Reading order answers, in turn: what is the rate · do I already have a deal
 * running · how do I start one · who am I about to send money to · what happens
 * after I do · and what would a scam look like.
 */
@Composable
fun MarketplaceScreen(
    onBack: () -> Unit,
    onOpenWhatsApp: (String) -> Unit,
    onNewDeal: () -> Unit,
    onMyDeals: () -> Unit,
    onOpenDeal: (String) -> Unit = {},
    onOpenSellerChat: (String) -> Unit = {},
    viewModel: MarketplaceViewModel = hiltViewModel()
) {
    val rate by viewModel.rate.collectAsStateWithLifecycle()
    val seller by viewModel.seller.collectAsStateWithLifecycle()
    val activeDeal by viewModel.activeDeal.collectAsStateWithLifecycle()
    val sellerRoomId by viewModel.sellerRoomId.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(IosColors.Background)
            .statusBarsPadding()
    ) {
        ExchangeHeader(seller = seller, onBack = onBack)

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { LiveRateCard(rate, Modifier.staggeredAppear(0)) }

            activeDeal?.let { deal ->
                item {
                    Column(Modifier.staggeredAppear(1)) {
                        SectionLabel("YOUR ACTIVE DEAL")
                        Spacer(Modifier.height(8.dp))
                        ActiveDealCard(deal) { onOpenDeal(deal.id) }
                    }
                }
            }

            item {
                Column(Modifier.staggeredAppear(2)) {
                    // Chat first. Every deal here starts as a conversation about
                    // an amount, and a member who opens one without asking is
                    // the member most likely to cancel it.
                    ActionButton(
                        title = "Continue to chat",
                        subtitle = rate?.let { current ->
                            buildString {
                                append("Rate ${ExchangeRate.formatPaisa(current.buyPaisa)} PKR")
                                seller.releaseLabel()?.let { append(" · $it release") }
                            }
                        },
                        icon = Icons.Outlined.Chat,
                        onClick = { sellerRoomId?.let(onOpenSellerChat) }
                    )
                    Spacer(Modifier.height(10.dp))
                    SecondaryButton("Start a new deal", onClick = onNewDeal)
                }
            }

            item { SellerCard(seller, Modifier.staggeredAppear(3), onClick = onMyDeals) }

            item {
                Column(Modifier.staggeredAppear(4)) {
                    SectionLabel("HOW IT WORKS")
                    Spacer(Modifier.height(8.dp))
                    HowItWorks()
                }
            }

            item { SafetyNote(Modifier.staggeredAppear(5)) }
        }
    }
}

@Composable
private fun ExchangeHeader(seller: MarketplaceSeller, onBack: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Surface)
                    .clickable {
                        Haptics.tap(haptics)
                        onBack()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Exchange",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            // Shown only when staff have actually marked the seller verified.
            // A badge the app draws for itself verifies nothing.
            if (seller.isVerified) VerifiedBadge()
        }

        seller.name?.let { name ->
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Buy and sell USD with $name",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 48.dp)
            )
        }
    }
}

@Composable
private fun VerifiedBadge() {
    Row(
        Modifier
            .clip(CircleShape)
            .background(IosColors.BuyGreen.copy(alpha = 0.16f))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Shield,
            contentDescription = null,
            tint = IosColors.BuyGreen,
            modifier = Modifier.size(11.dp)
        )
        Text(
            text = "VERIFIED",
            color = IosColors.BuyGreen,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )
    }
}

/**
 * The rate, and the spread behind it.
 *
 * Both sides are shown together because the spread is the seller's margin, and
 * hiding it is what makes an exchange feel like it is working against you. The
 * headline is the buy rate, since buying USD is what nearly everyone here opens
 * this screen to do.
 */
@Composable
private fun LiveRateCard(rate: ExchangeRate?, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(RateCardTop, RateCardBottom)))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulsingDot(live = rate != null)
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (rate == null) "RATE UNAVAILABLE" else "LIVE RATE",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.1.sp
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = rate?.pair ?: "USD/PKR",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "1 USD",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 15.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Spacer(Modifier.weight(1f))
            AnimatedNumber(
                value = rate?.let { "${ExchangeRate.formatPaisa(it.buyPaisa)} PKR" } ?: "—",
                style = LocalTextStyle.current.copy(
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = Color.White
            )
        }

        if (rate != null) {
            Spacer(Modifier.height(8.dp))
            DeltaChip(rate)
        }

        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(Color.White.copy(alpha = 0.10f))
        )
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.Top) {
            RateSide(
                label = "YOU BUY AT",
                value = rate?.let { ExchangeRate.formatPaisa(it.buyPaisa) } ?: "—",
                modifier = Modifier.weight(1f)
            )
            RateSide(
                label = "YOU SELL AT",
                value = rate?.let { ExchangeRate.formatPaisa(it.sellPaisa) } ?: "—",
                modifier = Modifier.weight(1f)
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Updated",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = rate?.let { relativeTime(it.updatedAt) } ?: "—",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }
        }

        if (rate != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Spread ${ExchangeRate.formatPaisa(rate.spreadPaisa)}",
                color = Color.White.copy(alpha = 0.35f),
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun RateSide(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp
        )
        Spacer(Modifier.height(4.dp))
        AnimatedNumber(
            value = value,
            style = LocalTextStyle.current.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
            color = Color.White
        )
    }
}

/** How far the rate moved since Tanha last changed it, and which way. */
@Composable
private fun DeltaChip(rate: ExchangeRate) {
    val tint = when (rate.direction) {
        ExchangeRate.Direction.UP -> IosColors.BuyGreen
        ExchangeRate.Direction.DOWN -> IosColors.SellRed
        ExchangeRate.Direction.FLAT -> Color.White.copy(alpha = 0.45f)
    }
    val sign = if (rate.deltaPaisa > 0) "+" else ""

    Row(
        Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.07f))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (rate.direction != ExchangeRate.Direction.FLAT) {
            Icon(
                if (rate.direction == ExchangeRate.Direction.UP) Icons.Filled.ArrowUpward
                else Icons.Filled.ArrowDownward,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(11.dp)
            )
        }
        Text(
            text = "$sign%.2f%%".format(rate.deltaPercent),
            color = tint,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "$sign${ExchangeRate.formatPaisa(rate.deltaPaisa)}",
            color = tint.copy(alpha = 0.7f),
            fontSize = 12.sp
        )
    }
}

/** The deal already running, so a member never opens a second one by accident. */
@Composable
private fun ActiveDealCard(deal: Deal, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Surface)
            .clickable {
                Haptics.tap(haptics)
                onClick()
            }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = deal.status.title,
                    color = IosColors.Warning,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = deal.reference,
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 12.sp
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            DealLeg("YOU PAY", deal.payLabel(), Modifier.weight(1f))
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "for",
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(14.dp)
            )
            DealLeg("YOU GET", deal.receiveLabel(), Modifier.weight(1f), TextAlign.Center)
            DealLeg(
                "RATE",
                ExchangeRate.formatPaisa(deal.lockedRatePaisa),
                Modifier.weight(0.7f),
                TextAlign.End
            )
        }
    }
}

@Composable
private fun DealLeg(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    align: TextAlign = TextAlign.Start
) {
    Column(
        modifier,
        horizontalAlignment = when (align) {
            TextAlign.End -> Alignment.End
            TextAlign.Center -> Alignment.CenterHorizontally
            else -> Alignment.Start
        }
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

/** The lit violet button. The one thing on this screen that asks to be pressed. */
@Composable
private fun ActionButton(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    Column(
        Modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(ActionGradient))
            .clickable(interactionSource = interaction, indication = null) {
                Haptics.tap(haptics)
                onClick()
            }
            .padding(vertical = 15.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        subtitle?.let {
            Spacer(Modifier.height(3.dp))
            Text(text = it, color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun SecondaryButton(title: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    Box(
        Modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .clickable(interactionSource = interaction, indication = null) {
                Haptics.tap(haptics)
                onClick()
            }
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Who the money is going to.
 *
 * Statistics appear only when staff have set them. A row of zeroes, or worse a
 * flattering constant, on the screen where somebody decides whether to trust a
 * stranger with a bank transfer is the one place this app must not guess.
 */
@Composable
private fun SellerCard(
    seller: MarketplaceSeller,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    if (seller.name == null && !seller.hasStats) return
    val haptics = LocalHapticFeedback.current

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Surface)
            .clickable {
                Haptics.tap(haptics)
                onClick()
            }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(IosColors.AvatarGradient)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = seller.name?.take(1)?.uppercase() ?: "T",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = seller.name ?: "Seller",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (seller.isVerified) {
                        Spacer(Modifier.width(5.dp))
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "Verified",
                            tint = IosColors.BuyGreen,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
                seller.handle?.let {
                    Text(text = it, color = Color.White.copy(alpha = 0.45f), fontSize = 13.sp)
                }
            }
        }

        if (seller.hasStats) {
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth()) {
                seller.dealsLabel()?.let { SellerStat(it, "DEALS", Modifier.weight(1f)) }
                seller.ratingLabel()?.let { SellerStat(it, "RATING", Modifier.weight(1f)) }
                seller.releaseLabel()?.let { SellerStat(it, "RELEASE", Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SellerStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp
        )
    }
}

@Composable
private fun HowItWorks() {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Surface)
            .padding(vertical = 4.dp)
    ) {
        HowItWorksStep(1, "Agree in chat", "Message the seller and confirm the amount and rate.")
        HowItWorksStep(2, "Send payment", "Transfer to the seller's account, then confirm in the app.")
        HowItWorksStep(3, "Receive funds", "Once approved, the seller releases to your account.")
    }
}

@Composable
private fun HowItWorksStep(number: Int, title: String, detail: String) {
    Row(
        Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(StepChip),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number.toString(),
                color = IosColors.Accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Column {
            Text(text = title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(text = detail, color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
        }
    }
}

/**
 * The scam warning.
 *
 * Last on the screen and phrased as what the seller will never do, because that
 * is the sentence a member needs to recall later — when somebody is messaging
 * them a different account number and sounding convincing about it.
 */
@Composable
private fun SafetyNote(modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(IosColors.BuyGreen.copy(alpha = 0.07f))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            Icons.Filled.Shield,
            contentDescription = null,
            tint = IosColors.BuyGreen.copy(alpha = 0.8f),
            modifier = Modifier.size(16.dp)
        )
        Column {
            Text(
                text = "Only ever send payment to the account shown inside a deal.",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "The seller will never ask you to pay a different number.",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(
        text = title,
        color = Color.White.copy(alpha = 0.4f),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.0.sp
    )
}

@Composable
private fun PulsingDot(live: Boolean) {
    val alpha by rememberInfiniteTransition(label = "live").animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "alpha"
    )
    Box(
        Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(
                if (live) IosColors.BuyGreen.copy(alpha = alpha)
                else Color.White.copy(alpha = 0.3f)
            )
    )
}

/** "4m ago". Coarse on purpose — a rate that changed 94 seconds ago changed minutes ago. */
private fun relativeTime(then: Date): String {
    val millis = (System.currentTimeMillis() - then.time).coerceAtLeast(0)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val days = TimeUnit.MILLISECONDS.toDays(millis)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        else -> "${days}d ago"
    }
}
