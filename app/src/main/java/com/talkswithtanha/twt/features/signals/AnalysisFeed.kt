package com.talkswithtanha.twt.features.signals

import com.talkswithtanha.twt.core.designsystem.edgeShine
import com.talkswithtanha.twt.core.designsystem.WebFonts
import androidx.compose.animation.animateContentSize
import com.talkswithtanha.twt.core.designsystem.Motion
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SouthEast
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.talkswithtanha.twt.core.designsystem.Haptics
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.designsystem.staggeredAppear
import com.talkswithtanha.twt.core.images.Cloudinary
import com.talkswithtanha.twt.core.model.Signal
import com.talkswithtanha.twt.core.model.SignalType

/**
 * Tanha's chart analysis, as a feed.
 *
 * The same signals the Trades tab lists, turned inside out: there the chart is
 * a detail you reach by opening a ticket, here it is the whole card and the
 * numbers are the caption. A member scrolling this is deciding whether they
 * believe the setup, which is a different question from "what are my levels",
 * and it wants the picture first.
 *
 * Only signals Tanha actually attached a chart to appear. A card with an empty
 * frame where the analysis should be is worse than one fewer card.
 */
@Composable
fun AnalysisFeed(
    signals: List<Signal>,
    onOpenSignal: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth().animateContentSize(Motion.gentle())) {
        signals.forEachIndexed { index, signal ->
            AnalysisCard(
                signal = signal,
                onOpen = { onOpenSignal(signal.id) },
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .staggeredAppear(index)
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AnalysisCard(
    signal: Signal,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val images = signal.teamImages
    val tint = signalTint(signal)

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardFill)
            .border(0.5.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(20.dp))
            .edgeShine(RoundedCornerShape(20.dp))
    ) {
        // ── The chart ────────────────────────────────────────────────────
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .background(Color(0xFF0E0E0E))
                .clickable {
                    Haptics.tap(haptics)
                    onOpen()
                }
        ) {
            if (images.size > 1) {
                // Swipeable when Tanha attached more than one, because a setup
                // is often two timeframes and flattening them loses the point.
                val pager = rememberPagerState { images.size }
                HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                    AsyncImage(
                        model = Cloudinary.wide(images[page], width = 900),
                        contentDescription = "Chart ${page + 1} for ${signal.pair}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                PageDots(
                    count = images.size,
                    current = pager.currentPage,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp)
                )
            } else {
                AsyncImage(
                    model = Cloudinary.wide(images.first(), width = 900),
                    contentDescription = "Chart for ${signal.pair}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // A scrim under the pair badge, so white text survives a chart that
            // happens to be pale in that corner.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)
                        )
                    )
            )

            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = signal.pair,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(tint.copy(alpha = 0.22f))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        if (signal.type == SignalType.BUY) Icons.Filled.NorthEast
                        else Icons.Filled.SouthEast,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(9.dp)
                    )
                    Text(
                        text = signal.type.stored,
                        color = tint,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // ── The caption ──────────────────────────────────────────────────
        Column(Modifier.padding(14.dp)) {
            signal.notes?.takeIf { it.isNotBlank() }?.let { note ->
                Text(
                    text = note,
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLines = 3
                )
                Spacer(Modifier.height(12.dp))
            }

            // When the analysis was posted, and on what timeframe.
            //
            // The chart carries TradingView's own timestamp burned into the
            // image, which is when the *screenshot* was taken — not when Tanha
            // called it, and the two can be hours apart. UTC for the same
            // reason the ticket uses it: forex runs on it, and a time in the
            // reader's own zone is ambiguous the moment the card is forwarded
            // to somebody in another one.
            //
            // The rule above it is what makes this read as a caption rather
            // than as two labels left over from something that used to be
            // here — which, since the levels came off, is exactly what it
            // would otherwise look like.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(Color.White.copy(alpha = 0.07f))
            )
            Spacer(Modifier.height(11.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = utcPosted(signal.timestamp.time),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    // Tabular digits, so a column of these cards lines its
                    // times up instead of wobbling at every 1.
                    fontFamily = WebFonts.Mono,
                    letterSpacing = (-0.2).sp,
                    maxLines = 1
                )
                Spacer(Modifier.weight(1f))
                // A chip, like the status pill on the ticket. The timeframe is
                // a value, and a bare word at the end of a row reads as a
                // caption that ran out rather than as the answer to "on what?".
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.07f))
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = signal.timeframe,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * `19:33 UTC 29 September, 2026` — the same format and the same clock as the
 * ticket card, so a member comparing the two is not asked to reconcile them.
 */
private fun utcPosted(epochMillis: Long): String =
    SimpleDateFormat("HH:mm 'UTC' d MMMM, yyyy", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(java.util.Date(epochMillis))

@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(count) { index ->
            Box(
                Modifier
                    .size(if (index == current) 6.dp else 5.dp)
                    .clip(CircleShape)
                    .background(
                        Color.White.copy(alpha = if (index == current) 0.95f else 0.4f)
                    )
            )
        }
    }
}

/**
 * Nothing posted yet.
 *
 * Says where the charts come from rather than just that there are none, so a
 * member does not read an empty tab as a broken one.
 */
@Composable
fun AnalysisEmpty(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Insights,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "No analysis yet",
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "When Tanha attaches a chart to a signal, the breakdown shows up here.",
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center
        )
    }
}

private val CardFill = Color(0xFF141414)
