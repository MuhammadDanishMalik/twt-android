package com.talkswithtanha.twt.features.feed

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import coil3.compose.AsyncImage
import com.talkswithtanha.twt.core.model.Signal
import com.talkswithtanha.twt.core.model.SignalType
import com.talkswithtanha.twt.core.data.VoiceNote
import com.talkswithtanha.twt.core.voice.VoicePlayer
import com.talkswithtanha.twt.core.voice.VoiceRecorder
import com.talkswithtanha.twt.core.voice.formatVoiceDuration
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.designsystem.brand.FeedIcons
import com.talkswithtanha.twt.core.designsystem.components.MemberAvatar
import com.talkswithtanha.twt.core.designsystem.components.TwtButton
import com.talkswithtanha.twt.core.images.Cloudinary

/**
 * Writing a post.
 *
 * Text first and biggest, because most of what goes here is a sentence about
 * the day. Images and a video are links rather than an upload: Firebase Storage
 * is not enabled on this project, and the same paste-a-link arrangement already
 * carries the home banners and the signal screenshots.
 */
@Composable
fun NewPostScreen(
    onClose: () -> Unit,
    viewModel: FeedViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    var text by remember { mutableStateOf("") }
    var images by remember { mutableStateOf(listOf<String>()) }
    var pendingImage by remember { mutableStateOf("") }
    var videoUrl by remember { mutableStateOf("") }
    var pair by remember { mutableStateOf("") }
    var isPosting by remember { mutableStateOf(false) }
    /** A live trade carried into the post, so the row draws its real ticket. */
    var attached by remember { mutableStateOf<Signal?>(null) }
    var isPinned by remember { mutableStateOf(false) }

    // ── The voice note ────────────────────────────────────────────────────
    val context = LocalContext.current
    val recorder = remember { VoiceRecorder(context) }
    var isRecording by remember { mutableStateOf(false) }
    var elapsed by remember { mutableIntStateOf(0) }
    var voiceSeconds by remember { mutableIntStateOf(0) }
    var voiceUrl by remember { mutableStateOf<String?>(null) }
    var voiceTitle by remember { mutableStateOf("") }
    var isUploadingVoice by remember { mutableStateOf(false) }
    var voiceError by remember { mutableStateOf<String?>(null) }

    val startRecording = {
        voiceError = null
        if (recorder.start()) {
            isRecording = true
            elapsed = 0
        } else {
            voiceError = "The microphone could not be opened. Close anything else using it and try again."
        }
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecording()
        else voiceError = "Recording needs the microphone. Allow it in Settings to post a voice note."
    }

    // The recorder has no tick of its own, so the timer is driven from here.
    LaunchedEffect(isRecording) {
        while (isRecording) {
            elapsed = recorder.elapsedSeconds
            delay(250)
            // Nobody listens to a nine-minute voice note on a trading app, and
            // an accidental recording left running would upload all of it.
            if (recorder.elapsedSeconds >= MAX_VOICE_SECONDS) {
                val done = recorder.stop()
                isRecording = false
                if (done != null) {
                    voiceSeconds = done.seconds
                    isUploadingVoice = true
                    viewModel.uploadVoice(done.file) { url, error ->
                        isUploadingVoice = false
                        voiceUrl = url
                        voiceError = error
                    }
                }
            }
        }
    }

    // A half-finished recording must not outlive the screen, and the player is
    // shared with the feed — a preview still talking after Close is somebody
    // else's bug to find.
    DisposableEffect(Unit) {
        onDispose {
            recorder.cancel()
            VoicePlayer.stop()
        }
    }

    // Trades still running. A closed one shared into the feed is a history
    // lesson, and the ticket would invite a member to follow something that is
    // already over.
    val live = remember(state.signals) {
        state.signals.values.filter { it.status.isOngoing }.sortedBy { it.pair }
    }

    // A voice note is only postable once it has uploaded and been given a
    // title. Everything else on this screen is optional next to it.
    val voiceReady = voiceUrl != null && voiceTitle.isNotBlank()
    val voicePending = isRecording || isUploadingVoice || (voiceUrl != null && !voiceReady)

    val canPost = !voicePending && (
        voiceReady || text.isNotBlank() || images.isNotEmpty() ||
            videoUrl.isNotBlank() || attached != null
        )

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
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(IosColors.SecondaryBackground)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                FeedIcons.Close(Modifier.size(17.dp), Color.White)
            }
            Spacer(Modifier.weight(1f))
            Text("New post", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(38.dp))
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Row {
                MemberAvatar(
                    photoUrl = state.user?.profilePhoto,
                    name = state.user?.fullName,
                    size = 42.dp
                )
                Box(Modifier.padding(start = 12.dp, top = 8.dp)) {
                    if (text.isEmpty()) {
                        Text("What is happening?", color = IosColors.TextSecondary, fontSize = 19.sp)
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        textStyle = LocalTextStyle.current.copy(
                            color = Color.White,
                            fontSize = 19.sp,
                            lineHeight = 26.sp
                        ),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (images.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(images) { url ->
                        Box {
                            AsyncImage(
                                model = Cloudinary.wide(url, width = 600),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(140.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF151515))
                            )
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .clickable { images = images - url },
                                contentAlignment = Alignment.Center
                            ) {
                                FeedIcons.Close(Modifier.size(13.dp), Color.White)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Label("VOICE NOTE")
            VoiceComposer(
                isRecording = isRecording,
                elapsed = elapsed,
                isUploading = isUploadingVoice,
                url = voiceUrl,
                seconds = voiceSeconds,
                title = voiceTitle,
                error = voiceError,
                onTitleChange = { voiceTitle = it },
                onStart = {
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        startRecording()
                    } else {
                        micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onStop = {
                    val done = recorder.stop()
                    isRecording = false
                    if (done == null) {
                        voiceError = "That was too short to send. Hold on a moment longer."
                    } else {
                        voiceSeconds = done.seconds
                        isUploadingVoice = true
                        viewModel.uploadVoice(done.file) { url, error ->
                            isUploadingVoice = false
                            voiceUrl = url
                            voiceError = error
                        }
                    }
                },
                onDiscard = {
                    VoicePlayer.stop()
                    voiceUrl = null
                    voiceTitle = ""
                    voiceSeconds = 0
                    voiceError = null
                }
            )

            // Most signals reach the feed on their own — publishing one from the
            // admin area shares it. This is for the other case: pulling a trade
            // from earlier back up with something to say about it.
            if (live.isNotEmpty()) {
                Label("SHARE A LIVE TRADE")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(live) { signal ->
                        val picked = attached?.id == signal.id
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (picked) IosColors.Accent.copy(alpha = 0.22f)
                                    else Color(0xFF171717)
                                )
                                .clickable {
                                    attached = if (picked) null else signal
                                    // The pair rides along, so the post keeps its
                                    // chart button either way.
                                    if (!picked) pair = signal.pair
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (signal.type == SignalType.BUY) IosColors.BuyGreen
                                        else IosColors.SellRed
                                    )
                            )
                            Spacer(Modifier.size(7.dp))
                            Text(
                                text = signal.pair,
                                color = if (picked) Color.White else IosColors.TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.size(5.dp))
                            Text(
                                text = signal.type.stored,
                                color = IosColors.TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Label("IMAGE LINK")
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinkField(
                    value = pendingImage,
                    hint = "https://…",
                    modifier = Modifier.weight(1f)
                ) { pendingImage = it }
                Spacer(Modifier.size(10.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (pendingImage.isNotBlank()) IosColors.Accent else Color(0xFF222222))
                        .clickable(enabled = pendingImage.isNotBlank()) {
                            images = images + pendingImage.trim()
                            pendingImage = ""
                        }
                        .padding(horizontal = 16.dp, vertical = 11.dp)
                ) {
                    Text("Add", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Label("VIDEO LINK (OPTIONAL)")
            LinkField(value = videoUrl, hint = "YouTube or a direct link") { videoUrl = it }

            // Sets the chart button on the post. Left empty for a note about the
            // week ahead, which is about no instrument in particular.
            Label("PAIR (OPTIONAL)")
            LinkField(value = pair, hint = "XAU/USD") { pair = it.uppercase() }

            // For the one post that has to outlive the scroll: the week's plan,
            // or a warning before a rate decision.
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF171717))
                    .clickable { isPinned = !isPinned }
                    .padding(horizontal = 13.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Pin to the top", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "Stays above everything until you unpin it.",
                        color = IosColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }
                Switch(checked = isPinned, onCheckedChange = { isPinned = it })
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = "Everyone with access sees this. They can react and reply; only you can post.",
                color = IosColors.TextSecondary,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(24.dp))
        }

        Box(Modifier.padding(16.dp)) {
            TwtButton(
                text = if (isPosting) "Posting…" else "Post",
                enabled = canPost && !isPosting,
                loading = isPosting,
                onClick = {
                    isPosting = true
                    viewModel.post(
                        text = text,
                        images = images,
                        videoUrl = videoUrl.ifBlank { null },
                        pair = pair.ifBlank { null },
                        signalId = attached?.id,
                        isPinned = isPinned,
                        audio = voiceUrl?.let {
                            VoiceNote(url = it, title = voiceTitle.trim(), seconds = voiceSeconds)
                        }
                    ) {
                        isPosting = false
                        onClose()
                    }
                }
            )
        }
    }
}

/** A note longer than this is a video, and nobody listens to it either way. */
private const val MAX_VOICE_SECONDS = 300

/**
 * Record, hear it back, title it.
 *
 * Three states in one box rather than three screens: idle, recording, recorded.
 * A separate recording screen would be the obvious build and the wrong one —
 * the title is what makes a voice note usable, and it has to be typed with the
 * note in front of you, not after a sheet has closed over it.
 */
@Composable
private fun VoiceComposer(
    isRecording: Boolean,
    elapsed: Int,
    isUploading: Boolean,
    url: String?,
    seconds: Int,
    title: String,
    error: String?,
    onTitleChange: (String) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onDiscard: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF171717))
            .padding(13.dp)
    ) {
        when {
            // ── Recording ────────────────────────────────────────────────
            isRecording -> Row(verticalAlignment = Alignment.CenterVertically) {
                RecordingDot()
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = formatVoiceDuration(elapsed),
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Recording — up to ${MAX_VOICE_SECONDS / 60} minutes",
                        color = IosColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White)
                        .clickable(onClick = onStop)
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Text("Stop", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            isUploading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    Modifier.size(18.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Uploading ${formatVoiceDuration(seconds)}…",
                    color = IosColors.TextSecondary,
                    fontSize = 14.sp
                )
            }

            // ── Recorded: hear it back, and say what it is ───────────────
            url != null -> Column {
                VoiceNotePlayer(
                    url = url,
                    title = title.ifBlank { "Untitled note" },
                    seconds = seconds,
                    isMuted = title.isBlank()
                )
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF101010))
                        .padding(12.dp)
                ) {
                    if (title.isEmpty()) {
                        Text(
                            text = "Title this note — what is it about?",
                            color = IosColors.TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                    BasicTextField(
                        value = title,
                        onValueChange = { if (it.length <= 70) onTitleChange(it) },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 14.sp),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (title.isBlank()) "A title is needed before this can be posted."
                        else "${70 - title.length} characters left",
                        color = if (title.isBlank()) IosColors.Warning else IosColors.TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Discard",
                        color = IosColors.SellRed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onDiscard)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // ── Idle ─────────────────────────────────────────────────────
            else -> Row(
                Modifier.fillMaxWidth().clickable(onClick = onStart),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(50))
                        .background(IosColors.SellRed.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size(13.dp)
                            .clip(RoundedCornerShape(50))
                            .background(IosColors.SellRed)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Record a voice note", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "Members hear you before the open.",
                        color = IosColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        if (error != null) {
            Spacer(Modifier.height(10.dp))
            Text(error, color = IosColors.SellRed, fontSize = 12.sp)
        }
    }
}

/** The red dot, pulsing, so a recording that is running is never in doubt. */
@Composable
private fun RecordingDot() {
    val transition = rememberInfiniteTransition(label = "rec")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "recAlpha"
    )
    Box(
        Modifier
            .size(11.dp)
            .clip(RoundedCornerShape(50))
            .background(IosColors.SellRed.copy(alpha = alpha))
    )
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        color = IosColors.TextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)
    )
}

@Composable
private fun LinkField(
    value: String,
    hint: String,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF171717))
            .padding(13.dp)
    ) {
        if (value.isEmpty()) {
            Text(hint, color = IosColors.TextSecondary, fontSize = 14.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 14.sp),
            cursorBrush = SolidColor(Color.White),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
