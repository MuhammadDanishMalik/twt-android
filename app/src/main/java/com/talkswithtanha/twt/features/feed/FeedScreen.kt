package com.talkswithtanha.twt.features.feed

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.talkswithtanha.twt.R
import com.talkswithtanha.twt.core.designsystem.Haptics
import com.talkswithtanha.twt.core.designsystem.brand.FeedIcons
import com.talkswithtanha.twt.core.designsystem.brand.TabIcons
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.designsystem.components.EmptyState
import com.talkswithtanha.twt.core.designsystem.components.MemberAvatar
import com.talkswithtanha.twt.features.signals.SignalTicketCard
import com.talkswithtanha.twt.core.images.Cloudinary
import com.talkswithtanha.twt.core.model.FeedKind
import com.talkswithtanha.twt.core.model.FeedPost
import com.talkswithtanha.twt.core.model.Signal
import com.talkswithtanha.twt.core.model.Reaction
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * The feed: what Tanha has put in front of the community, and what they made
 * of it.
 *
 * ### Why it is built like X
 *
 * The client asked for X, and X is right for this: a single column, no cards,
 * a hairline between rows, the avatar on the left with everything else hanging
 * off one text column, and the actions as small outlined glyphs at the bottom.
 * It reads fast because nothing is boxed — a feed of cards is a feed you scan
 * for where one thing ends and the next begins.
 *
 * What is deliberately *not* X: no retweet and no view count. Nobody can
 * forward a post anywhere, and a view count on a room of a few hundred members
 * is a number that only ever reads as small.
 *
 * The chat lives behind the icon at the top right. It used to be this tab; the
 * feed is what people open the app for, and the conversations are one tap away.
 */
/** The room's account, shown on every post: the feed is the desk, not a person. */
private const val HANDLE = "@tradewithtanha"

/** How many lines of a long post the feed shows before "Show more". */
private const val COLLAPSED_LINES = 6

@Composable
fun FeedScreen(
    onOpenPost: (String) -> Unit,
    onOpenSignal: (String) -> Unit,
    onOpenChats: () -> Unit,
    onCompose: () -> Unit,
    viewModel: FeedViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(IosColors.Background)
            .statusBarsPadding()
    ) {
        FeedTopBar(onOpenChats = onOpenChats)

        LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
            state.error?.let { message ->
                item {
                    Text(
                        text = message,
                        color = IosColors.SellRed,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            if (state.posts.isEmpty() && !state.isLoading) {
                item {
                    EmptyState(
                        icon = Icons.AutoMirrored.Outlined.Chat,
                        title = "Nothing posted yet",
                        message = if (state.canPost) {
                            "Anything you post here reaches every member — a chart, a clip, a note on the day."
                        } else {
                            "Tanha's notes, charts and clips will appear here."
                        }
                    )
                }
            }

            items(state.posts, key = { it.id }) { post ->
                PostRow(
                    post = post,
                    myReaction = state.myReactions[post.id],
                    canModerate = state.canPost,
                    onOpen = { onOpenPost(post.id) },
                    onReact = { viewModel.react(post, it) },
                    onDelete = { viewModel.delete(post) },
                    isSaved = post.id in state.mySaves,
                    onSave = { viewModel.toggleSave(post) },
                    signal = post.signalId?.let(state.signals::get),
                    isFollowingSignal = post.signalId in state.followedSignalIds,
                    onOpenSignal = { onOpenSignal(it.id) },
                    onTogglePin = { viewModel.togglePin(post) },
                    onFollowSignal = viewModel::follow,
                    onUnfollowSignal = viewModel::unfollow
                )
                Divider()
            }
        }
    }

    // Only Tanha sees it. The rules refuse a post from anybody else, so this is
    // about not offering what cannot be done rather than about security.
    if (state.canPost) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
            Box(
                Modifier
                    .padding(end = 20.dp, bottom = 120.dp)
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(IosColors.Accent)
                    .clickable(onClick = onCompose),
                contentAlignment = Alignment.Center
            ) {
                FeedIcons.Compose(Modifier.size(28.dp), Color.White)
            }
        }
    }
}

@Composable
private fun FeedTopBar(onOpenChats: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The app's display face, as on Home and Signals. The feed was the one
        // screen still setting its own type, which is most of why it read as
        // somebody else's app.
        Text(
            text = "Feed",
            color = Color.White,
            style = MaterialTheme.typography.headlineLarge
        )
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(IosColors.SecondaryBackground)
                .clickable(onClick = onOpenChats),
            contentAlignment = Alignment.Center
        ) {
            // The tab bar's own chat glyph, so the icon that opens the chats
            // is the one that used to sit in the bar for them.
            TabIcons.Chat(Modifier.size(19.dp), Color.White)
        }
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(Color.White.copy(alpha = 0.10f))
    )
}

/**
 * One post.
 *
 * The avatar is its own column and everything else hangs off the text column to
 * its right — the shape that lets a name, a timestamp, two lines of text and a
 * row of actions sit in 140 points without feeling crowded.
 */
@Composable
fun PostRow(
    post: FeedPost,
    myReaction: Reaction?,
    canModerate: Boolean,
    onOpen: () -> Unit,
    onReact: (Reaction) -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit = {},
    isSaved: Boolean = false,
    onSave: () -> Unit = {},
    showActions: Boolean = true,
    /** The trade behind a signal post, once the feed has it. */
    signal: Signal? = null,
    isFollowingSignal: Boolean = false,
    onOpenSignal: (Signal) -> Unit = {},
    onFollowSignal: (Signal) -> Unit = {},
    onUnfollowSignal: (String) -> Unit = {}
) {
    var menuOpen by remember { mutableStateOf(false) }
    var expanded by remember(post.id) { mutableStateOf(false) }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    // Tapping the play badge leaves for YouTube or the browser. Playing it in
    // place would mean a player, a lifecycle and a surface for something that is
    // a link to somebody else's video — and the app the member already has open
    // for it does a better job.
    val openVideo: () -> Unit = {
        post.videoUrl?.takeIf { it.isNotBlank() }?.let { url ->
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // The house mark, not an initial. Every post here is from the desk, and
        // a grey "D" circle is what every feed in every app looks like.
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color(0xFF101010)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.twt_logo),
                contentDescription = post.authorName,
                modifier = Modifier.size(38.dp)
            )
        }

        Column(Modifier.padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = post.authorName,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(4.dp))
                // The owner's mark, then the handle, then the age — the order X
                // uses, and the reason its headers scan in one pass.
                FeedIcons.Verified(Modifier.size(15.dp), IosColors.Accent)
                Spacer(Modifier.width(5.dp))
                Text(
                    text = HANDLE,
                    color = IosColors.TextSecondary,
                    fontSize = 14.sp,
                    maxLines = 1
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "· ${shortAgo(post.createdAt)}",
                    color = IosColors.TextSecondary,
                    fontSize = 14.sp,
                    maxLines = 1
                )
                Spacer(Modifier.weight(1f))
                // The overflow is for everyone, not only staff. A member's
                // options are the ones that make sense on somebody else's post —
                // keep it, copy the levels, send it on, open the chart — and
                // Tanha's add the two that change the post itself.
                Box {
                    FeedIcons.More(
                        Modifier
                            .size(18.dp)
                            .clickable { menuOpen = true },
                        IosColors.TextSecondary
                    )
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        containerColor = Color(0xFF161616)
                    ) {
                        MenuRow(if (isSaved) "Remove from saved" else "Save for later") {
                            menuOpen = false
                            onSave()
                        }
                        if (post.text.isNotBlank()) {
                            MenuRow("Copy text") {
                                menuOpen = false
                                clipboard.setText(AnnotatedString(post.text))
                            }
                        }
                        MenuRow("Share") {
                            menuOpen = false
                            // Plain text on purpose. Signals are the product;
                            // a deep link out of a members-only feed is not
                            // something to hand to a group chat.
                            val body = buildString {
                                post.pair?.let { append("$it — ") }
                                append(post.text.ifBlank { "A post from Trade With Tanha." })
                            }
                            runCatching {
                                context.startActivity(
                                    Intent.createChooser(
                                        Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, body)
                                        },
                                        null
                                    )
                                )
                            }
                        }
                        post.chartUrl?.let { url ->
                            MenuRow("Open ${post.pair} chart") {
                                menuOpen = false
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                            }
                        }
                        if (canModerate) {
                            MenuRow(if (post.isPinned) "Unpin from top" else "Pin to top") {
                                menuOpen = false
                                onTogglePin()
                            }
                            MenuRow("Delete post", tint = IosColors.SellRed) {
                                menuOpen = false
                                onDelete()
                            }
                        }
                    }
                }
            }

            // What it is, before what it says. A member scrolling at the open
            // needs to know in one glance whether this is a trade to act on or
            // a note to read later.
            KindLabel(post)

            if (post.text.isNotBlank()) {
                var overflows by remember(post.id) { mutableStateOf(false) }

                Text(
                    text = post.text,
                    color = Color.White,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    maxLines = if (expanded || !showActions) Int.MAX_VALUE else COLLAPSED_LINES,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { overflows = it.hasVisualOverflow || overflows },
                    modifier = Modifier.padding(top = 3.dp)
                )

                // Long analysis is the normal case here, so the feed shows the
                // first few lines and gets out of the way — X's own answer, and
                // the reason its timeline stays scannable when somebody writes
                // six paragraphs.
                if (overflows && !expanded && showActions) {
                    Text(
                        text = "Show more",
                        color = IosColors.Accent,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .clickable { expanded = true }
                    )
                }
            }

            // A shared trade draws the real ticket — the same card as the
            // Signals tab, with its levels, its colour and its Follow button.
            // A sentence describing a trade goes stale the moment it moves.
            if (signal != null) {
                Spacer(Modifier.height(10.dp))
                SignalTicketCard(
                    signal = signal,
                    isFollowing = isFollowingSignal,
                    onOpen = { onOpenSignal(signal) },
                    onFollow = { onFollowSignal(signal) },
                    onRequestUnfollow = { onUnfollowSignal(signal.id) }
                )
            } else if (post.hasVoiceNote) {
                // Tanha talking. The player carries the note's own title, so
                // the post's words above it are context rather than a label.
                Spacer(Modifier.height(10.dp))
                VoiceNotePlayer(
                    url = post.audioUrl.orEmpty(),
                    title = post.audioTitle.orEmpty().ifBlank { "Voice note" },
                    seconds = post.audioDurationSec
                )
            } else if (post.hasMedia) {
                Spacer(Modifier.height(10.dp))
                Media(post, onPlay = openVideo)
            }

            if (showActions) {
                Spacer(Modifier.height(10.dp))
                ActionRow(
                    post = post,
                    myReaction = myReaction,
                    isSaved = isSaved,
                    onReply = onOpen,
                    onReact = onReact,
                    onSave = onSave
                )
            }
        }
    }
}

@Composable
private fun MenuRow(text: String, tint: Color = Color.White, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, color = tint, fontSize = 14.sp) },
        onClick = onClick
    )
}

/**
 * The eyebrow over a post: SIGNAL, BREAKDOWN, ANALYSIS — or nothing.
 *
 * Notes get no label on purpose. They are most of the feed, and labelling the
 * ordinary case is how a screen ends up shouting at people in capitals.
 */
@Composable
private fun KindLabel(post: FeedPost) {
    val label = post.kind.label ?: return
    val tint = when (post.kind) {
        FeedKind.SIGNAL -> IosColors.BuyGreen
        else -> IosColors.TextSecondary
    }

    Row(
        Modifier.padding(top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (post.isPinned) {
            Text(
                text = "PINNED · ",
                color = IosColors.Warning,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
        }
        Text(
            text = label,
            color = tint,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
        post.pair?.let {
            Text(
                text = " · $it",
                color = IosColors.TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            )
        }
    }
}

@Composable
private fun Media(post: FeedPost, onPlay: () -> Unit) {
    val image = post.images.firstOrNull()
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 10f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF111111))
    ) {
        if (image != null) {
            AsyncImage(
                model = Cloudinary.wide(image, width = 900),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // A video is shown as a play badge over the first image, or over the
        // empty frame when there is none. Playing it happens on the post screen.
        if (!post.videoUrl.isNullOrBlank()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (image == null) 0f else 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                // The disc *is* the badge — the triangle is a hole in it, so the
                // frame shows through where the play arrow would be.
                FeedIcons.Play(
                    Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onPlay),
                    Color.White.copy(alpha = 0.92f)
                )
            }
        }

        if (post.images.size > 1) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("1/${post.images.size}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * What a member can do with a post.
 *
 * X's row is reply, repost, like, views, bookmark, share. Three of those make
 * no sense here and one is actively wrong: there is nowhere to repost to, a
 * view count on a few hundred members only ever reads as small, and *share*
 * on a feed of paid analysis is a button for giving it away.
 *
 * What a member of this room actually wants, in X's own layout and spacing:
 *
 *   • **Reply** — the conversation under the call.
 *   • **React** — a heart on tap, the other three on a long press.
 *   • **Save** — the one X action that transfers directly. People come back to
 *     Tanha's breakdowns before the next session.
 *   • **Chart** — straight to this pair on TradingView. It is what turns a
 *     timeline into a trader's feed, and it only appears when the post is
 *     about an instrument.
 */
@Composable
private fun ActionRow(
    post: FeedPost,
    myReaction: Reaction?,
    isSaved: Boolean,
    onReply: () -> Unit,
    onReact: (Reaction) -> Unit,
    onSave: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    var picking by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        // Spread across the column, as X does, rather than bunched on the left.
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        ActionButton(
            count = post.commentCount,
            tint = IosColors.TextSecondary,
            onClick = onReply,
            glyph = { tint, modifier -> FeedIcons.Reply(modifier, tint) }
        )

        Box {
            ActionButton(
                count = post.reactionCount,
                tint = if (myReaction != null) IosColors.SellRed else IosColors.TextSecondary,
                emoji = myReaction?.takeIf { it != Reaction.LOVE }?.glyph,
                glyph = { tint, modifier ->
                    // Filled once they have reacted, outlined until then: the
                    // same silhouette carrying the state, no colour needed.
                    FeedIcons.Heart(modifier, tint, filled = myReaction != null)
                },
                onClick = {
                    Haptics.tap(haptics)
                    onReact(myReaction ?: Reaction.LOVE)
                },
                onLongClick = { picking = true }
            )

            DropdownMenu(expanded = picking, onDismissRequest = { picking = false }) {
                Row(Modifier.padding(horizontal = 6.dp)) {
                    Reaction.entries.forEach { reaction ->
                        Box(
                            Modifier
                                .padding(4.dp)
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (myReaction == reaction) IosColors.Accent.copy(alpha = 0.22f) else Color.Transparent
                                )
                                .clickable {
                                    picking = false
                                    Haptics.tap(haptics)
                                    onReact(reaction)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(reaction.glyph, fontSize = 22.sp)
                        }
                    }
                }
            }
        }

        ActionButton(
            count = 0,
            tint = if (isSaved) IosColors.Accent else IosColors.TextSecondary,
            onClick = {
                Haptics.tap(haptics)
                onSave()
            },
            glyph = { tint, modifier -> FeedIcons.Save(modifier, tint, filled = isSaved) }
        )

        // Only for a post about an instrument. An empty slot would be a dead
        // button on every note about the week ahead.
        if (post.chartUrl != null) {
            ActionButton(
                count = 0,
                tint = IosColors.TextSecondary,
                label = post.pair,
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(post.chartUrl)))
                    }
                },
                glyph = { tint, modifier -> TabIcons.Signals(modifier, tint) }
            )
        } else {
            Spacer(Modifier.width(1.dp))
        }
    }
}

@Composable
private fun ActionButton(
    count: Int,
    tint: Color,
    onClick: () -> Unit,
    glyph: @Composable (Color, Modifier) -> Unit,
    /** Shown instead of the glyph when they picked something other than love. */
    emoji: String? = null,
    /** Shown instead of a count — the pair, beside the chart glyph. */
    label: String? = null,
    onLongClick: (() -> Unit)? = null
) {
    val interaction = remember { MutableInteractionSource() }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .then(
                if (onLongClick == null) {
                    Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)
                } else {
                    Modifier.combinedClick(onClick = onClick, onLongClick = onLongClick)
                }
            )
            .padding(vertical = 4.dp, horizontal = 2.dp)
    ) {
        if (emoji != null) {
            Text(emoji, fontSize = 16.sp)
        } else {
            glyph(tint, Modifier.size(17.dp))
        }
        val caption = label ?: count.takeIf { it > 0 }?.toString()
        if (caption != null) {
            Spacer(Modifier.width(6.dp))
            Text(caption, color = tint, fontSize = 13.sp)
        }
    }
}

/** `combinedClickable` without the ripple, which fights the dark rows. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.combinedClick(onClick: () -> Unit, onLongClick: () -> Unit): Modifier =
    this.combinedClickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
        onLongClick = onLongClick
    )

/** "2h" — X's own shorthand, which is why nobody has to read it twice. */
internal fun shortAgo(date: Date): String {
    val seconds = ((Date().time - date.time) / 1000).coerceAtLeast(0)
    return when {
        seconds < 60 -> "now"
        seconds < 3_600 -> "${TimeUnit.SECONDS.toMinutes(seconds)}m"
        seconds < 86_400 -> "${TimeUnit.SECONDS.toHours(seconds)}h"
        seconds < 604_800 -> "${TimeUnit.SECONDS.toDays(seconds)}d"
        else -> "${TimeUnit.SECONDS.toDays(seconds) / 7}w"
    }
}
