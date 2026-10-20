package com.talkswithtanha.twt.features.feed

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.designsystem.brand.FeedIcons
import com.talkswithtanha.twt.core.designsystem.components.MemberAvatar
import com.talkswithtanha.twt.core.model.FeedComment

/**
 * One post and its replies.
 *
 * Threading is one level, like X: a reply to the post, and a reply to that.
 * Deeper nesting is a forum, and a forum is unreadable on a phone — the third
 * level would start at an indent that leaves room for four words a line.
 *
 * The reply bar sits at the bottom and remembers who is being answered, so the
 * member can tap "Reply" on somebody's comment and type without losing where
 * they were.
 */
@Composable
fun PostDetailScreen(
    onBack: () -> Unit,
    viewModel: PostDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<FeedComment?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(IosColors.Background)
            .statusBarsPadding()
            .imePadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
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
                FeedIcons.Back(Modifier.size(18.dp), Color.White)
            }
            Spacer(Modifier.width(14.dp))
            Text("Post", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        val post = state.post
        if (post == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = IosColors.TextSecondary)
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                PostRow(
                    post = post,
                    myReaction = state.myReaction,
                    canModerate = state.canModerate,
                    onOpen = {},
                    onReact = viewModel::react,
                    isSaved = state.isSaved,
                    onSave = viewModel::toggleSave,
                    signal = state.signal,
                    isFollowingSignal = state.isFollowingSignal,
                    onTogglePin = viewModel::togglePin,
                    onFollowSignal = viewModel::follow,
                    onUnfollowSignal = viewModel::unfollow,
                    // Deleting the post itself is done from the feed row; this
                    // screen only ever leaves.
                    onDelete = onBack
                )
                Hairline()
            }

            items(state.topLevel, key = { it.id }) { comment ->
                CommentRow(
                    comment = comment,
                    canModerate = state.canModerate,
                    isMine = comment.authorId == state.user?.id,
                    onReply = { replyingTo = comment },
                    onDelete = { viewModel.deleteComment(comment) }
                )

                // Replies to this reply, indented once and no further.
                state.repliesTo(comment.id).forEach { reply ->
                    CommentRow(
                        comment = reply,
                        canModerate = state.canModerate,
                        isMine = reply.authorId == state.user?.id,
                        indent = true,
                        onReply = { replyingTo = comment },
                        onDelete = { viewModel.deleteComment(reply) }
                    )
                }

                Hairline()
            }

            if (state.topLevel.isEmpty()) {
                item {
                    Text(
                        text = "No replies yet. Say something.",
                        color = IosColors.TextSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        }

        ReplyBar(
            value = draft,
            replyingTo = replyingTo,
            isSending = state.isSending,
            onChange = { draft = it },
            onCancelReply = { replyingTo = null },
            onSend = {
                viewModel.reply(draft, replyingTo?.id)
                draft = ""
                replyingTo = null
            }
        )
    }
}

@Composable
private fun Hairline() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(Color.White.copy(alpha = 0.10f))
    )
}

@Composable
private fun CommentRow(
    comment: FeedComment,
    canModerate: Boolean,
    isMine: Boolean,
    indent: Boolean = false,
    onReply: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = if (indent) 56.dp else 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
    ) {
        MemberAvatar(
            photoUrl = comment.authorPhoto,
            name = comment.authorName,
            size = if (indent) 28.dp else 34.dp
        )

        Column(Modifier.padding(start = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.authorName,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(6.dp))
                Text("· ${shortAgo(comment.createdAt)}", color = IosColors.TextSecondary, fontSize = 12.sp)
            }

            Text(
                text = comment.text,
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 14.5.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(top = 2.dp)
            )

            Row(
                Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Reply",
                    color = IosColors.TextSecondary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(onClick = onReply)
                )
                if (isMine || canModerate) {
                    Text(
                        text = "Delete",
                        color = IosColors.SellRed.copy(alpha = 0.8f),
                        fontSize = 12.5.sp,
                        modifier = Modifier.clickable(onClick = onDelete)
                    )
                }
            }
        }
    }
}

@Composable
private fun ReplyBar(
    value: String,
    replyingTo: FeedComment?,
    isSending: Boolean,
    onChange: (String) -> Unit,
    onCancelReply: () -> Unit,
    onSend: () -> Unit
) {
    val canSend = value.isNotBlank() && !isSending

    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F0F0F))
            .navigationBarsPadding()
    ) {
        // Who is being answered, with a way out of it — without this, a member
        // who taps Reply on the wrong comment has no way to tell, and no way
        // back.
        if (replyingTo != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 10.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Replying to ${replyingTo.authorName}",
                    color = IosColors.Accent,
                    fontSize = 12.5.sp,
                    modifier = Modifier.weight(1f)
                )
                FeedIcons.Close(
                    Modifier
                        .size(16.dp)
                        .clickable(onClick = onCancelReply),
                    IosColors.TextSecondary
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF1C1C1C))
                    .padding(horizontal = 14.dp, vertical = 11.dp)
            ) {
                if (value.isEmpty()) {
                    Text("Post your reply", color = IosColors.TextSecondary, fontSize = 15.sp)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onChange,
                    textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 15.sp),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (canSend) IosColors.Accent else Color(0xFF2A2A2A))
                    .clickable(enabled = canSend, onClick = onSend),
                contentAlignment = Alignment.Center
            ) {
                if (isSending) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    FeedIcons.Send(
                        Modifier.size(18.dp),
                        if (canSend) Color.White else IosColors.TextSecondary
                    )
                }
            }
        }
    }
}
