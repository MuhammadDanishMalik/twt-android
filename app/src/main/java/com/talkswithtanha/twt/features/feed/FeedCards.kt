package com.talkswithtanha.twt.features.feed

import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import com.talkswithtanha.twt.R
import com.talkswithtanha.twt.core.data.LiveApi
import com.talkswithtanha.twt.core.designsystem.Haptics
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.designsystem.WebFonts
import com.talkswithtanha.twt.core.designsystem.bouncyClick
import com.talkswithtanha.twt.core.designsystem.brand.FeedIcons
import com.talkswithtanha.twt.core.designsystem.brand.PairFlagBadge
import com.talkswithtanha.twt.core.designsystem.components.ImageViewer
import com.talkswithtanha.twt.core.designsystem.edgeShine
import com.talkswithtanha.twt.core.images.Cloudinary
import com.talkswithtanha.twt.core.model.FeedPost
import com.talkswithtanha.twt.core.model.LinkCard
import com.talkswithtanha.twt.core.model.PostType
import com.talkswithtanha.twt.core.model.Reaction
import com.talkswithtanha.twt.core.model.Signal
import com.talkswithtanha.twt.core.model.SignalStatus
import com.talkswithtanha.twt.core.model.SignalType
import com.talkswithtanha.twt.core.model.SignalUpdate
import com.talkswithtanha.twt.core.model.TradeStyle
import com.talkswithtanha.twt.core.model.VideoChapter
import com.talkswithtanha.twt.core.voice.VoicePlayer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

// The website's member feed card (twt-web/components/feed/cards.tsx), drawn
// for the phone: who and what, the post by its type, and what to do with it.

private const val HANDLE = "@tradewithtanha"
private val CardShape = RoundedCornerShape(20.dp)
private val CardFill = Color(0xFF111111)

/** Everything a card needs from outside itself, so the feed and a post's own page behave the same. */
data class CardActions(
    val onOpen: () -> Unit = {},
    val onReact: (Reaction) -> Unit = {},
    /** Saves into the given collections (empty = saved), or unsaves with null. */
    val onSave: (List<String>?) -> Unit = {},
    val onPickPair: (String) -> Unit = {},
    val onFollow: (Signal) -> Unit = {},
    val onUnfollow: (String) -> Unit = {},
    val onOpenSignal: (Signal) -> Unit = {},
    val onAsk: () -> Unit = {},
    val onTogglePin: () -> Unit = {},
    val onDelete: () -> Unit = {}
)

@Composable
fun FeedCard(
    post: FeedPost,
    signal: Signal?,
    quote: LiveApi.Quote?,
    myReaction: Reaction?,
    unread: Boolean,
    savedIn: List<String>?,
    collections: List<String>,
    isFollowingSignal: Boolean,
    canModerate: Boolean,
    updates: Flow<List<SignalUpdate>>,
    actions: CardActions,
    modifier: Modifier = Modifier,
    updatedBadge: Boolean = false,
    /** On a post's own page: the whole text, no "Show more". */
    expanded: Boolean = false
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(if (post.type == PostType.ALERT) Color(0xFF1A1206) else CardFill)
            .border(0.8.dp, Color.White.copy(alpha = 0.09f), CardShape)
            .edgeShine(CardShape, strength = 0.5f)
            .clickable(onClick = actions.onOpen)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PostHeader(post, unread, updatedBadge, canModerate, actions)
        if (post.type == PostType.SETUP) {
            SetupBody(post, signal, quote, isFollowingSignal, updates, actions, expanded)
        } else {
            PostBody(post, actions, expanded)
        }
        ActionBar(post, myReaction, savedIn, collections, actions)
    }
}

// ── Header ──────────────────────────────────────────────────────────────────

@Composable
private fun PostHeader(post: FeedPost, unread: Boolean, updated: Boolean, canModerate: Boolean, actions: CardActions) {
    var menu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF0B0B0B)),
            contentAlignment = Alignment.Center
        ) {
            Image(painterResource(R.drawable.twt_logo), contentDescription = post.authorName, modifier = Modifier.size(36.dp))
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(post.authorName, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = WebFonts.Body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(4.dp))
                FeedIcons.Verified(Modifier.size(14.dp), IosColors.Accent)
                Spacer(Modifier.width(5.dp))
                Text("$HANDLE · ${shortAgo(post.createdAt)}", color = IosColors.TextSecondary, fontSize = 12.5.sp, fontFamily = WebFonts.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (post.editedAt != null) {
                    Spacer(Modifier.width(6.dp))
                    Text("Edited", color = IosColors.TextSecondary, fontSize = 11.sp, fontFamily = WebFonts.Body)
                }
            }
            Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                val eyebrow = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.Black, letterSpacing = 0.9.sp, fontFamily = WebFonts.Body)
                if (post.isPinned) Text("PINNED · ", color = IosColors.Warning, style = eyebrow)
                Text(
                    post.type.label.uppercase(),
                    color = when (post.type) {
                        PostType.SETUP -> IosColors.BuyGreen
                        PostType.ALERT -> IosColors.Warning
                        else -> IosColors.TextSecondary
                    },
                    style = eyebrow
                )
                if (post.pairs.isNotEmpty()) Text(" · ${post.pairs.joinToString(", ")}", color = IosColors.TextSecondary, style = eyebrow, maxLines = 1)
                if (updated) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "UPDATED",
                        color = Color.Black,
                        style = eyebrow,
                        modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(IosColors.Warning).padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
        }
        if (unread) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(IosColors.BuyGreen))
            Spacer(Modifier.width(8.dp))
        }
        Box {
            FeedIcons.More(Modifier.size(20.dp).clickable { menu = true }, IosColors.TextSecondary)
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Color(0xFF161616)) {
                if (post.body.isNotBlank()) {
                    MenuRow("Copy text") {
                        menu = false
                        clipboard.setText(AnnotatedString(post.text.ifBlank { post.body }))
                    }
                }
                MenuRow("Share") {
                    menu = false
                    val body = buildString {
                        post.pairs.firstOrNull()?.let { append("$it — ") }
                        append(post.text.ifBlank { "A post from Trade With Tanha." })
                    }
                    runCatching {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, body)
                        }, null))
                    }
                }
                post.chartUrl?.let { url ->
                    MenuRow("Open ${post.pairs.firstOrNull() ?: post.pair} on TradingView") {
                        menu = false
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
                    }
                }
                if (canModerate) {
                    MenuRow(if (post.isPinned) "Unpin from top" else "Pin to top") {
                        menu = false
                        actions.onTogglePin()
                    }
                    MenuRow("Delete post", tint = IosColors.SellRed) {
                        menu = false
                        actions.onDelete()
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(text: String, tint: Color = Color.White, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(text, color = tint, fontSize = 14.sp, fontFamily = WebFonts.Body) }, onClick = onClick)
}

// ── Notes, charts, video, voice ─────────────────────────────────────────────

@Composable
private fun PostBody(post: FeedPost, actions: CardActions, expanded: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (post.body.isNotBlank()) Collapsible(post.body, lines = 6, expanded = expanded)
        PairChips(post.pairs, actions.onPickPair)
        if (post.hasVoiceNote) {
            VoiceNote(post.audioUrl.orEmpty(), post.audioTitle.orEmpty().ifBlank { "Voice note" }, post.audioDurationSec, post.transcript)
        }
        if (!post.videoUrl.isNullOrBlank()) {
            VideoCard(post.videoUrl, post.images.firstOrNull(), post.videoStart, post.chapters)
        } else {
            ChartGallery(post.images, post.imageAlts)
        }
        post.link?.let { LinkPreview(it) }
    }
}

/**
 * The composer's rich text: # headings, - bullets, **bold**. Drawn as one
 * text so it folds after a few lines with a single "Show more".
 */
fun markdown(text: String): AnnotatedString = buildAnnotatedString {
    val lines = text.trimEnd().split('\n')
    lines.forEachIndexed { i, raw ->
        val line = raw.trimEnd()
        val bullet = Regex("^\\s*[-•*]\\s+(.*)$").find(line)
        val heading = Regex("^#{1,3}\\s+(.*)$").find(line)
        when {
            bullet != null -> {
                append("•  ")
                inline(bullet.groupValues[1])
            }
            heading != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 16.5.sp)) { inline(heading.groupValues[1]) }
            else -> inline(line)
        }
        if (i < lines.lastIndex) append('\n')
    }
}

private fun AnnotatedString.Builder.inline(text: String) {
    var rest = text
    while (true) {
        val m = Regex("\\*\\*([^*]+)\\*\\*").find(rest) ?: break
        append(rest.substring(0, m.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(m.groupValues[1]) }
        rest = rest.substring(m.range.last + 1)
    }
    append(rest)
}

/** Text that folds after a few lines, with one "Show more" that opens it where it is. */
@Composable
fun Collapsible(text: String, lines: Int = 6, expanded: Boolean = false) {
    var open by remember(text) { mutableStateOf(expanded) }
    var overflows by remember(text) { mutableStateOf(false) }
    Column(Modifier.animateContentSize()) {
        Text(
            markdown(text),
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 15.sp,
            lineHeight = 22.sp,
            fontFamily = WebFonts.Body,
            maxLines = if (open) Int.MAX_VALUE else lines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!open) overflows = it.hasVisualOverflow }
        )
        if (overflows && !open) {
            Text(
                "Show more",
                color = IosColors.BuyGreen,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = WebFonts.Body,
                modifier = Modifier.padding(top = 4.dp).clickable { open = true }
            )
        }
    }
}

/** Pairs as chips; tapping one filters the feed to it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PairChips(pairs: List<String>, onPick: (String) -> Unit) {
    if (pairs.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        pairs.forEach { p ->
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(0.8.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(50))
                    .clickable { onPick(p) }
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PairFlagBadge(p, size = 14.dp)
                Spacer(Modifier.width(5.dp))
                Text(p, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = WebFonts.Body)
            }
        }
    }
}

/** A voice note with a speed control, and its transcript to read instead. */
@Composable
fun VoiceNote(url: String, title: String, seconds: Int, transcript: String?) {
    val player by VoicePlayer.state.collectAsState()
    var reading by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VoiceNotePlayer(url = url, title = title, seconds = seconds, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(
                "${if (player.speed % 1f == 0f) player.speed.toInt() else player.speed}×",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = WebFonts.Mono,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable { VoicePlayer.cycleSpeed() }
                    .padding(horizontal = 9.dp, vertical = 6.dp)
            )
        }
        if (!transcript.isNullOrBlank()) {
            Text(
                if (reading) "Hide transcript" else "Show transcript",
                color = IosColors.BuyGreen,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = WebFonts.Body,
                modifier = Modifier.clickable { reading = !reading }
            )
            if (reading) {
                Text(
                    transcript,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontFamily = WebFonts.Body,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.05f)).padding(12.dp)
                )
            }
        }
    }
}

private fun youTubeId(url: String): String? =
    Regex("(?:youtu\\.be/|youtube\\.com/(?:watch\\?v=|embed/|shorts/|live/))([\\w-]{11})").find(url)?.groupValues?.get(1)

/** A video's frame with a play button; it opens in YouTube, chapters at their moment. */
@Composable
private fun VideoCard(url: String, poster: String?, start: Int, chapters: List<VideoChapter>) {
    val context = LocalContext.current
    val id = youTubeId(url)
    fun open(at: Int) {
        val target = if (id != null && at > 0) "https://youtu.be/$id?t=$at" else url
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, target.toUri())) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
                .clickable { open(start) },
            contentAlignment = Alignment.Center
        ) {
            val thumb = poster?.let { Cloudinary.wide(it, width = 900) } ?: id?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }
            if (thumb != null) AsyncImage(model = thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            FeedIcons.Play(Modifier.size(56.dp), Color.White.copy(alpha = 0.92f))
        }
        chapters.forEach { c ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { open(maxOf(start, c.at)) }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("%d:%02d".format(c.at / 60, c.at % 60), color = IosColors.BuyGreen, fontSize = 13.sp, fontFamily = WebFonts.Mono)
                Spacer(Modifier.width(10.dp))
                Text(c.label, color = Color.White, fontSize = 14.sp, fontFamily = WebFonts.Body)
            }
        }
    }
}

/** Charts on a card: one image, or a swipeable row with a 1/3 counter. Tapping opens it full screen. */
@Composable
private fun ChartGallery(images: List<String>, alts: List<String>) {
    if (images.isEmpty()) return
    var viewing by remember { mutableStateOf<Int?>(null) }
    val pager = rememberPagerState { images.size }
    Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(16.dp)).background(Color(0xFF0B0B0B))) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            AsyncImage(
                model = Cloudinary.wide(images[page], width = 1000),
                contentDescription = alts.getOrNull(page)?.ifBlank { null } ?: "Chart",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clickable { viewing = page }
            )
        }
        if (images.size > 1) {
            Text(
                "${pager.currentPage + 1}/${images.size}",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = WebFonts.Body,
                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
    viewing?.let { ImageViewer(images = images, startIndex = it, onDismiss = { viewing = null }) }
}

/** A shared link as a card: picture, site and title. */
@Composable
private fun LinkPreview(link: LinkCard) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(0.8.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
            .clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, link.url.toUri())) } }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!link.image.isNullOrBlank()) {
            AsyncImage(model = link.image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(width = 96.dp, height = 60.dp).clip(RoundedCornerShape(9.dp)))
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(link.site, color = IosColors.TextSecondary, fontSize = 11.5.sp, fontFamily = WebFonts.Body, maxLines = 1)
            Text(link.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = WebFonts.Body, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ── Setup ───────────────────────────────────────────────────────────────────

/** Pips per unit of price: JPY pairs ×100, metals, coins and indices ×1, the rest ×10,000. */
fun pipScale(pair: String): Double {
    val p = pair.uppercase()
    return when {
        "JPY" in p -> 100.0
        Regex("XAU|GOLD|XAG|SILVER|BTC|ETH|US30|NAS|SPX|GER|UK100").containsMatchIn(p) -> 1.0
        else -> 10_000.0
    }
}

/** R: the result over what was risked. Null when the risk is unknowable. */
fun rMultiple(signal: Signal): Double? {
    val pips = signal.pipsGained ?: return null
    val risk = abs(signal.entryPriceValue - signal.stopLossValue) * pipScale(signal.pair)
    return if (risk > 0) pips / risk else null
}

/** 12.5, 1,240 — never 1.24E3. */
private fun pipText(v: Double): String = java.text.DecimalFormat("#,##0.#").format(v)

private fun num(v: String): Double? = v.filter { it.isDigit() || it == '.' || it == '-' }.toDoubleOrNull()

private data class StatusLook(val label: String, val color: Color)

private fun statusLook(status: SignalStatus): StatusLook = when (status) {
    SignalStatus.PENDING -> StatusLook("Pending", IosColors.Warning)
    SignalStatus.ACTIVE -> StatusLook("Active", IosColors.Accent)
    SignalStatus.TP1_HIT -> StatusLook("TP1 hit", IosColors.BuyGreen)
    SignalStatus.TP2_HIT -> StatusLook("TP2 hit", IosColors.BuyGreen)
    SignalStatus.WON -> StatusLook("Closed", IosColors.BuyGreen)
    SignalStatus.LOST -> StatusLook("Stopped", IosColors.SellRed)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetupBody(
    post: FeedPost,
    signal: Signal?,
    quote: LiveApi.Quote?,
    isFollowing: Boolean,
    updatesFlow: Flow<List<SignalUpdate>>,
    actions: CardActions,
    expanded: Boolean
) {
    if (signal == null) {
        Text("This setup is no longer on the board.", color = IosColors.TextSecondary, fontSize = 14.sp, fontFamily = WebFonts.Body)
        return
    }
    val updates by remember(updatesFlow) { updatesFlow }.collectAsState(initial = emptyList())
    var showAll by remember { mutableStateOf(false) }

    val buy = signal.type == SignalType.BUY
    val entry = signal.entryPriceValue.takeIf { it != 0.0 } ?: num(signal.entryPrice)
    val sl = signal.stopLossValue.takeIf { it != 0.0 } ?: num(signal.stopLoss)
    val risk = if (entry != null && sl != null) abs(entry - sl) else null
    val live = signal.status.isOngoing
    val r = if (!live) rMultiple(signal) else null
    val price = if (live) quote?.price else null
    val finalTp = signal.takeProfits.lastOrNull()?.price?.let(::num)
    val scale = pipScale(signal.pair)
    fun pips(a: Double, b: Double) = Math.round((b - a) * scale * (if (buy) 1 else -1) * 10) / 10.0
    // A level typed wrong (an entry of 4545454 on a pair at 0.70) would print
    // a distance in the millions; the line is left out rather than shown wrong.
    val sane = price != null && entry != null && abs(pips(entry, price)) < 100_000
    val look = statusLook(signal.status)
    val decimals = LiveApi.find(signal.pair)?.decimals ?: 2

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Direction, pair, timeframe, style and status.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            Text(
                if (buy) "▲ BUY" else "▼ SELL",
                color = if (buy) IosColors.BuyGreen else IosColors.SellRed,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Black,
                fontFamily = WebFonts.Body,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background((if (buy) IosColors.BuyGreen else IosColors.SellRed).copy(alpha = 0.14f)).padding(horizontal = 9.dp, vertical = 4.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                PairFlagBadge(signal.pair, size = 18.dp)
                Spacer(Modifier.width(6.dp))
                Text(signal.pair, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = WebFonts.Display)
            }
            if (signal.timeframe.isNotBlank() && signal.timeframe != "—") Text(signal.timeframe, color = IosColors.TextSecondary, fontSize = 12.5.sp, fontFamily = WebFonts.Body)
            Text(
                "${if (signal.tradeStyle == TradeStyle.LIMIT_ORDER) "Limit" else "Market"} · ${signal.tradeStyle.stored}",
                color = IosColors.TextSecondary,
                fontSize = 12.5.sp,
                fontFamily = WebFonts.Body
            )
            Text(
                look.label + (r?.let { " (${if (it >= 0) "+" else "−"}${"%.1f".format(abs(it))}R)" } ?: ""),
                color = look.color,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = WebFonts.Body,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(look.color.copy(alpha = 0.14f)).padding(horizontal = 9.dp, vertical = 4.dp)
            )
        }

        // The levels, with the R each target pays.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Level("Entry", signal.entryPrice.ifBlank { "—" }, null, Color.White, false)
            Level("Stop loss", signal.stopLoss.ifBlank { "—" }, null, IosColors.SellRed, false)
            signal.takeProfits.forEach { tp ->
                val t = num(tp.price)
                val rr = if (t != null && entry != null && risk != null && risk > 0) abs(t - entry) / risk else null
                Level("${tp.label.ifBlank { "TP" }}${if (tp.isHit) " ✓" else ""}", tp.price.ifBlank { "—" }, rr?.let { "%.1fR".format(it) }, Color.White, tp.isHit)
            }
        }

        // Where price is now, against the levels.
        if (live && price != null && entry != null && sane) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.04f)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(IosColors.SellRed))
                    Text(String.format(Locale.US, "%.${decimals}f", price), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = WebFonts.Mono)
                    val fromEntry = pips(entry, price)
                    Text("${if (fromEntry >= 0) "+" else ""}${pipText(fromEntry)} pips from entry", color = if (fromEntry >= 0) IosColors.BuyGreen else IosColors.SellRed, fontSize = 12.5.sp, fontFamily = WebFonts.Body)
                    if (sl != null) Text("${pipText(abs(pips(price, sl)))} to SL", color = IosColors.TextSecondary, fontSize = 12.5.sp, fontFamily = WebFonts.Body)
                    if (finalTp != null) Text("${pipText(abs(pips(price, finalTp)))} to final TP", color = IosColors.TextSecondary, fontSize = 12.5.sp, fontFamily = WebFonts.Body)
                }
                if (sl != null && finalTp != null && finalTp != sl) {
                    val progress = ((price - sl) / (finalTp - sl)).toFloat().coerceIn(0f, 1f)
                    val entryAt = ((entry - sl) / (finalTp - sl)).toFloat().coerceIn(0f, 1f)
                    ProgressTrack(progress, entryAt)
                }
            }
        }

        // What the desk changed, newest first.
        if (updates.isNotEmpty()) {
            val fmt = remember { SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (if (showAll) updates else updates.take(3)).forEach { u ->
                    Row {
                        Box(Modifier.padding(top = 6.dp).size(7.dp).clip(CircleShape).background(IosColors.BuyGreen))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(fmt.format(u.at), color = IosColors.TextSecondary, fontSize = 11.5.sp, fontFamily = WebFonts.Mono)
                            Text(
                                buildAnnotatedString {
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(u.label) }
                                    u.price?.let { append(" at $it") }
                                    if (u.note.isNotBlank()) append(" — ${u.note}")
                                },
                                color = Color.White,
                                fontSize = 14.sp,
                                fontFamily = WebFonts.Body
                            )
                            u.audioUrl?.let { VoiceNote(it, "Voice update", u.audioDurationSec, u.transcript) }
                        }
                    }
                }
                if (updates.size > 3 && !showAll) {
                    Text("Show all ${updates.size} updates", color = IosColors.BuyGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = WebFonts.Body, modifier = Modifier.clickable { showAll = true })
                }
            }
        }

        val reasoning = post.body.ifBlank { signal.notes.orEmpty() }
        if (reasoning.isNotBlank()) Collapsible(reasoning, lines = 4, expanded = expanded)
        ChartGallery(signal.teamImages + post.images, post.imageAlts)
        if (post.hasVoiceNote) VoiceNote(post.audioUrl.orEmpty(), "Tanha explains", post.audioDurationSec, post.transcript)
        post.link?.let { LinkPreview(it) }
        Text("Trading involves risk. Not financial advice.", color = Color.White.copy(alpha = 0.32f), fontSize = 11.5.sp, fontFamily = WebFonts.Body)

        // The things a trader does with a setup, one tap each.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            SetupAction(if (isFollowing) "🔔 Alerts on" else "🔔 Alert me", on = isFollowing) {
                if (isFollowing) actions.onUnfollow(signal.id) else actions.onFollow(signal)
            }
            if (live && LiveApi.find(signal.pair) != null) SetupAction("📈 Live chart") { actions.onOpenSignal(signal) }
            SetupAction("💬 Ask") { actions.onAsk() }
        }
    }
}

@Composable
private fun Level(label: String, value: String, rr: String?, color: Color, hit: Boolean) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (hit) IosColors.BuyGreen.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.04f))
            .border(0.8.dp, if (hit) IosColors.BuyGreen.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label.uppercase(), color = IosColors.TextSecondary, fontSize = 9.5.sp, letterSpacing = 0.8.sp, fontWeight = FontWeight.Bold, fontFamily = WebFonts.Body)
        Text(value, color = color, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, fontFamily = WebFonts.Mono)
        if (rr != null) Text(rr, color = IosColors.BuyGreen, fontSize = 11.sp, fontFamily = WebFonts.Mono)
    }
}

/** Price between the stop (left) and the final target (right), with the entry marked. */
@Composable
private fun ProgressTrack(progress: Float, entryAt: Float) {
    Column {
        BoxWithConstraints(Modifier.fillMaxWidth().height(14.dp)) {
            val w = maxWidth
            Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f)))
            Box(Modifier.align(Alignment.CenterStart).fillMaxWidth(progress.coerceAtLeast(0.01f)).height(4.dp).clip(RoundedCornerShape(50)).background(IosColors.BuyGreen.copy(alpha = 0.7f)))
            Box(Modifier.align(Alignment.CenterStart).offset(x = w * entryAt - 1.dp).width(2.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.6f)))
            Box(Modifier.align(Alignment.CenterStart).offset(x = w * progress - 6.dp).size(12.dp).clip(CircleShape).background(Color.White).border(2.dp, IosColors.BuyGreen, CircleShape))
        }
        Row(Modifier.fillMaxWidth().padding(top = 3.dp)) {
            Text("SL", color = IosColors.SellRed, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, fontFamily = WebFonts.Body)
            Spacer(Modifier.weight(1f))
            Text("TP", color = IosColors.BuyGreen, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, fontFamily = WebFonts.Body)
        }
    }
}

@Composable
private fun SetupAction(label: String, on: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        color = if (on) Color.Black else Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = WebFonts.Body,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (on) IosColors.BuyGreen else Color.White.copy(alpha = 0.07f))
            .border(0.8.dp, Color.White.copy(alpha = if (on) 0f else 0.1f), RoundedCornerShape(50))
            .bouncyClick(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp)
    )
}

// ── Actions ─────────────────────────────────────────────────────────────────

@Composable
private fun ActionBar(post: FeedPost, mine: Reaction?, savedIn: List<String>?, collections: List<String>, actions: CardActions) {
    val haptics = LocalHapticFeedback.current
    var saveMenu by remember { mutableStateOf(false) }
    val isSaved = savedIn != null
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Reaction.entries.filter { !it.setupsOnly || post.type == PostType.SETUP }.forEach { r ->
            val count = (post.reactionCounts[r.stored] ?: 0).coerceAtLeast(0)
            val on = mine == r
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (on) IosColors.BuyGreen.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f))
                    .border(0.8.dp, if (on) IosColors.BuyGreen.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(50))
                    .bouncyClick {
                        Haptics.tap(haptics)
                        actions.onReact(r)
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(r.glyph, fontSize = 14.sp)
                if (count > 0) {
                    Spacer(Modifier.width(5.dp))
                    Text("$count", color = if (on) IosColors.BuyGreen else IosColors.TextSecondary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, fontFamily = WebFonts.Body)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (post.commentsEnabled) {
            Row(Modifier.clip(RoundedCornerShape(50)).clickable(onClick = actions.onOpen).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                FeedIcons.Reply(Modifier.size(17.dp), IosColors.TextSecondary)
                if (post.commentCount > 0) {
                    Spacer(Modifier.width(5.dp))
                    Text("${post.commentCount}", color = IosColors.TextSecondary, fontSize = 12.5.sp, fontFamily = WebFonts.Body)
                }
            }
        } else {
            Text("Comments off", color = IosColors.TextSecondary.copy(alpha = 0.7f), fontSize = 11.5.sp, fontFamily = WebFonts.Body)
        }
        Box {
            Box(Modifier.clip(CircleShape).clickable { saveMenu = true }.padding(6.dp)) {
                FeedIcons.Save(Modifier.size(17.dp), if (isSaved) IosColors.BuyGreen else IosColors.TextSecondary, filled = isSaved)
            }
            DropdownMenu(expanded = saveMenu, onDismissRequest = { saveMenu = false }, containerColor = Color(0xFF161616)) {
                MenuRow(if (isSaved) "✓ Saved" else "Save") {
                    saveMenu = false
                    Haptics.tap(haptics)
                    actions.onSave(if (isSaved) null else emptyList())
                }
                collections.forEach { c ->
                    val inIt = savedIn?.contains(c) == true
                    MenuRow("${if (inIt) "✓ " else ""}$c") {
                        saveMenu = false
                        actions.onSave(if (inIt) savedIn.orEmpty() - c else savedIn.orEmpty() + c)
                    }
                }
            }
        }
    }
}

/** "2h" — the feed's shorthand, so nobody has to read it twice. */
internal fun shortAgo(date: Date): String {
    val seconds = ((Date().time - date.time) / 1000).coerceAtLeast(0)
    return when {
        seconds < 60 -> "now"
        seconds < 3_600 -> "${TimeUnit.SECONDS.toMinutes(seconds)}m"
        seconds < 86_400 -> "${TimeUnit.SECONDS.toHours(seconds)}h"
        seconds < 604_800 -> "${TimeUnit.SECONDS.toDays(seconds)}d"
        else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(date)
    }
}

/** For a card with no trade behind it. */
val NoUpdates: Flow<List<SignalUpdate>> = flowOf(emptyList())

@Composable
internal fun SeenOnce(postId: String, onSeen: (String) -> Unit) {
    LaunchedEffect(postId) { onSeen(postId) }
}
