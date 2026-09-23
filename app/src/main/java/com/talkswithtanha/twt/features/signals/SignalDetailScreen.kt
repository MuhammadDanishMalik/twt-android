package com.talkswithtanha.twt.features.signals

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.talkswithtanha.twt.core.designsystem.Haptics
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.notifications.rememberNotificationPermissionRequest
import com.talkswithtanha.twt.core.model.FollowOutcome
import com.talkswithtanha.twt.core.model.Signal
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val CardSurface = Color(0xFF141414)
private val CardRim = Color(0x14FFFFFF)

/**
 * One signal, in full: the ticket as a hero, the actions, the milestone track,
 * the chart, Tanha's note and the result.
 *
 * Ported from `SignalDetailSheet.swift`, including the staggered reveal — each
 * block rises, fades and unblurs a beat after the one above it.
 */
@Composable
fun SignalDetailScreen(
    onBack: () -> Unit,
    onRecordResult: (String) -> Unit,
    viewModel: SignalDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var confirmUnfollow by remember { mutableStateOf(false) }

    // Asked at the moment a member follows their first trade — the first point
    // at which they have an obvious reason to say yes, having just asked to be
    // told what happens to it.
    val askForNotifications = rememberNotificationPermissionRequest()

    val signal = state.signal

    Column(
        Modifier
            .fillMaxSize()
            .background(IosColors.Background)
            .statusBarsPadding()
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(IosColors.SecondaryBackground)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(17.dp)
                )
            }
            Text(
                text = signal?.pair ?: "Signal",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (signal == null) {
            Text(
                text = state.error ?: "Loading…",
                color = IosColors.TextSecondary,
                modifier = Modifier.padding(24.dp)
            )
            return@Column
        }

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Reveal(0) {
                SignalTicketCard(
                    signal = signal,
                    isFollowing = state.isFollowing,
                    showsFollowButton = false
                )
            }

            Reveal(1) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FollowActionButton(
                            state = state,
                            modifier = Modifier.weight(1f),
                            onFollow = {
                                askForNotifications()
                                viewModel.toggleFollow()
                            },
                            onUnfollow = { confirmUnfollow = true },
                            onRecord = { onRecordResult(signal.id) }
                        )
                        signal.tradingViewUrl()?.let { url ->
                            DetailButton(
                                text = "TradingView",
                                icon = Icons.Filled.ShowChart,
                                primary = false,
                                modifier = Modifier.weight(1f)
                            ) {
                                Haptics.tap(haptics)
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                                }
                            }
                        }
                    }
                    if (state.isFollowing) {
                        Text(
                            text = "You will hear about every change to this trade — moves, edits and results.",
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Reveal(2) { SignalMilestoneTrack(signal) }

            val teamImages = signal.teamImages
            if (teamImages.isNotEmpty()) {
                Reveal(3) {
                    DetailCard(
                        title = "From the team",
                        accessory = {
                            Text(
                                text = teamImages.size.toString(),
                                color = Color.White.copy(alpha = 0.45f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    ) {
                        // A row rather than one big image: Tanha attaches the
                        // chart he drew on *and* whatever else shows the setup,
                        // and stacking those full-width pushes the details off
                        // the screen.
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(teamImages) { url ->
                                AsyncImage(
                                    model = url,
                                    contentDescription = "Chart for ${signal.pair}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .width(172.dp)
                                        .height(112.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFF1A1A1A))
                                        .border(
                                            0.5.dp,
                                            Color.White.copy(alpha = 0.10f),
                                            RoundedCornerShape(14.dp)
                                        )
                                )
                            }
                        }
                    }
                }
            }

            Reveal(4) {
                DetailCard(title = "Details") {
                    DetailRow(Icons.Filled.Bolt, "Trade style", signal.tradeStyle.stored)
                    DetailDivider()
                    DetailRow(Icons.Filled.Schedule, "Timeframe", signal.timeframe)
                    DetailDivider()
                    DetailRow(Icons.Filled.Scale, "Risk / reward", signal.riskReward)
                    DetailDivider()
                    DetailRow(Icons.Filled.CalendarMonth, "Posted", postedUtc(signal))
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = signal.tradeStyle.explanation,
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 13.sp
                    )
                }
            }

            signal.notes?.let { notes ->
                Reveal(5) {
                    DetailCard(title = "Tanha's note") {
                        Text(
                            text = notes,
                            color = Color.White.copy(alpha = 0.88f),
                            fontSize = 15.sp,
                            lineHeight = 21.sp
                        )
                    }
                }
            }

            if (signal.status.isWon || signal.status.isLost) {
                Reveal(6) { ResultCard(signal) }
            }

            state.follow?.takeIf { it.isSettled }?.let { follow ->
                Reveal(7) {
                    DetailCard(title = "Your result") {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                follow.outcome.label,
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            follow.resultPips?.let {
                                Text(
                                    text = "${if (it >= 0) "+" else ""}${it.toInt()} pips",
                                    color = if (it >= 0) IosColors.BuyGreen else IosColors.SellRed,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(48.dp))
        }
    }

    if (confirmUnfollow) {
        AlertDialog(
            onDismissRequest = { confirmUnfollow = false },
            containerColor = IosColors.SecondaryBackground,
            title = { Text("Stop following ${signal?.pair}?", color = Color.White) },
            text = {
                Text(
                    "You will not be told about updates to this trade. Your record of it is kept.",
                    color = IosColors.TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmUnfollow = false
                    viewModel.toggleFollow()
                }) { Text("Stop following", color = IosColors.SellRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmUnfollow = false }) {
                    Text("Keep following", color = IosColors.TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun FollowActionButton(
    state: SignalDetailUiState,
    modifier: Modifier,
    onFollow: () -> Unit,
    onUnfollow: () -> Unit,
    onRecord: () -> Unit
) {
    val follow = state.follow
    when {
        state.signal?.status?.isOngoing == true -> DetailButton(
            text = if (state.isFollowing) "Following" else "Follow",
            icon = if (state.isFollowing) Icons.Filled.Check else Icons.Filled.Notifications,
            primary = !state.isFollowing,
            modifier = modifier,
            onClick = if (state.isFollowing) onUnfollow else onFollow
        )

        follow != null && follow.outcome == FollowOutcome.OPEN -> DetailButton(
            text = "Record result",
            icon = Icons.Filled.Check,
            primary = true,
            modifier = modifier,
            onClick = onRecord
        )

        follow != null && follow.isSettled -> Box(
            modifier
                .height(52.dp)
                .clip(CircleShape)
                .background(Color(0xFF1A1A1A)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Recorded · ${follow.outcome.label}",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        else -> Spacer(modifier)
    }
}

@Composable
private fun DetailButton(
    text: String,
    icon: ImageVector,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier
            .height(52.dp)
            .clip(CircleShape)
            .background(if (primary) Color.White else Color(0xFF212121))
            .then(
                if (primary) Modifier
                else Modifier.border(0.5.dp, Color.White.copy(alpha = 0.10f), CircleShape)
            )
            .clickable {
                Haptics.tap(haptics)
                onClick()
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (primary) Color.Black else Color.White,
            modifier = Modifier.size(17.dp)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = text,
            color = if (primary) Color.Black else Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun DetailCard(
    title: String? = null,
    accessory: @Composable (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(CardSurface)
            .border(0.5.dp, CardRim, RoundedCornerShape(22.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (title != null) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title.uppercase(),
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp
                )
                accessory?.invoke()
            }
        }
        content()
    }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().height(40.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.45f),
            modifier = Modifier.size(20.dp)
        )
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp)
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun DetailDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 32.dp)
            .height(0.5.dp)
            .background(Color.White.copy(alpha = 0.07f))
    )
}

@Composable
private fun ResultCard(signal: Signal) {
    val won = signal.status.isWon
    val tint = if (won) IosColors.BuyGreen else IosColors.SellRed
    DetailCard {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (won) Icons.Filled.Check else Icons.Filled.Close,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column {
                Text(
                    text = if (won) "Closed in profit" else "Closed at the stop",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                signal.pipsGained?.let {
                    Text(
                        text = "${if (it >= 0) "+" else ""}${it.toInt()} pips",
                        color = tint,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

/** Each block rises and unblurs a beat after the one above it. */
@Composable
private fun Reveal(index: Int, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(60L + index.coerceAtMost(6) * 55L)
        shown = true
    }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 300f),
        label = "reveal"
    )
    Box(
        Modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * 14f * density
        }
    ) {
        content()
    }
}

private fun postedUtc(signal: Signal): String =
    SimpleDateFormat("d MMM, HH:mm 'UTC'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(signal.timestamp)
