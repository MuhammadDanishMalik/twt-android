package com.talkswithtanha.twt.features.academy

import com.talkswithtanha.twt.core.designsystem.WebFonts
import android.annotation.SuppressLint
import android.content.Intent
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.talkswithtanha.twt.core.designsystem.Haptics
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.navigation.AppRoute
import com.talkswithtanha.twt.core.notes.VideoNote
import com.talkswithtanha.twt.core.notes.VideoNotesStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class LessonPlayerViewModel @Inject constructor(
    private val store: VideoNotesStore,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val videoId: String = checkNotNull(savedStateHandle[AppRoute.Lesson.ARG])
    val title: String = savedStateHandle.get<String>(AppRoute.Lesson.TITLE_ARG).orEmpty()
        .ifBlank { "Lesson" }
    val startSeconds: Int = savedStateHandle.get<String>(AppRoute.Lesson.START_ARG)?.toIntOrNull() ?: 0

    /** This lesson's notes, in the order they happen in the video. */
    val notes: StateFlow<List<VideoNote>> = store.notes
        .map { all -> all.filter { it.videoId == videoId }.sortedBy { it.timestamp } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), store.notesFor(videoId))

    fun add(timestamp: Double, text: String) {
        if (text.isBlank()) return
        store.add(videoId, title, timestamp, text)
    }

    fun delete(id: String) = store.delete(id)
}

/**
 * A lesson, played in the app so notes can be pinned to moments in it — the
 * Android half of iOS's `VideoLearningView`.
 *
 * "Add a note" stamps it with wherever the video is right now. Tapping a note's
 * time jumps the video back to it. Notes stay on this phone, and can be shared
 * into any chat from the paperclip.
 */
@Composable
fun LessonPlayerScreen(
    onBack: () -> Unit,
    viewModel: LessonPlayerViewModel = hiltViewModel()
) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val player = remember { YouTubePlayerHandle() }
    var draft by remember { mutableStateOf("") }

    fun addNote() {
        val text = draft.trim()
        if (text.isEmpty()) return
        player.currentTime { seconds ->
            viewModel.add(seconds, text)
            Haptics.success(haptics)
        }
        draft = ""
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(IosColors.Background)
            .statusBarsPadding()
            .imePadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
            Text(
                viewModel.title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                Haptics.tap(haptics)
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, "https://www.youtube.com/watch?v=${viewModel.videoId}".toUri())
                    )
                }
            }) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Open in YouTube", tint = IosColors.TextSecondary)
            }
        }

        YouTubePlayer(
            videoId = viewModel.videoId,
            startSeconds = viewModel.startSeconds,
            handle = player,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
        )

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Your notes", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            if (notes.isNotEmpty()) {
                Text(
                    "${notes.size}",
                    color = IosColors.Accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(IosColors.Accent.copy(alpha = 0.14f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }

        if (notes.isEmpty()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Outlined.EditNote,
                    contentDescription = null,
                    tint = IosColors.Accent.copy(alpha = 0.5f),
                    modifier = Modifier.size(44.dp)
                )
                Spacer(Modifier.height(10.dp))
                Text("No notes yet", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Take notes while watching to jump back to\nimportant moments — and share them in chat.",
                    color = IosColors.TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(notes, key = { it.id }) { note ->
                    NoteRow(
                        note = note,
                        onSeek = {
                            Haptics.tap(haptics)
                            player.seekTo(note.timestamp)
                        },
                        onDelete = {
                            Haptics.toggleOff(haptics)
                            viewModel.delete(note.id)
                        },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }

        // The note composer, stamped with the video's current time on send.
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF1C1C1E))
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF2C2C2E))
                    .padding(horizontal = 16.dp, vertical = 11.dp)
                    .animateContentSize()
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    maxLines = 4,
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    cursorBrush = SolidColor(IosColors.Accent),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { addNote() }),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (draft.isEmpty()) {
                            Text("Add a note at current time…", color = IosColors.TextSecondary, fontSize = 16.sp)
                        }
                        inner()
                    }
                )
            }
            val canAdd = draft.isNotBlank()
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (canAdd) IosColors.Accent else Color(0xFF2C2C2E))
                    .clickable(enabled = canAdd) { addNote() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Add note",
                    tint = if (canAdd) Color.White else IosColors.TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun NoteRow(note: VideoNote, onSeek: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onSeek)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            note.formattedTimestamp,
            color = IosColors.Accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = WebFonts.Mono,
            modifier = Modifier
                .clip(CircleShape)
                .background(IosColors.Accent.copy(alpha = 0.13f))
                .padding(horizontal = 10.dp, vertical = 5.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            note.text,
            color = Color.White,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            modifier = Modifier.weight(1f).padding(top = 3.dp)
        )
        Icon(
            Icons.Outlined.Delete,
            contentDescription = "Delete note",
            tint = IosColors.TextSecondary,
            modifier = Modifier
                .padding(start = 8.dp)
                .size(20.dp)
                .clip(CircleShape)
                .clickable(onClick = onDelete)
        )
    }
}

// ── The player ────────────────────────────────────────────────────────────

/** What the screen can ask of the player. Holds the WebView once it exists. */
class YouTubePlayerHandle {
    internal var webView: WebView? = null

    fun seekTo(seconds: Double) {
        webView?.evaluateJavascript("seekTo($seconds)", null)
    }

    /** The playback position, in seconds; 0 before the player is ready. */
    fun currentTime(onResult: (Double) -> Unit) {
        val view = webView ?: return onResult(0.0)
        view.evaluateJavascript("getCurrentTime()") { raw ->
            onResult(raw?.trim('"')?.toDoubleOrNull() ?: 0.0)
        }
    }
}

/**
 * YouTube's iframe player in a WebView — the same page iOS loads, with the
 * same youtube.com base URL (the embed refuses to play without an origin).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YouTubePlayer(
    videoId: String,
    startSeconds: Int,
    handle: YouTubePlayerHandle,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier.background(Color.Black),
        factory = { ctx ->
            // Lets chrome://inspect see the player in debug builds.
            WebView.setWebContentsDebuggingEnabled(com.talkswithtanha.twt.BuildConfig.DEBUG)
            WebView(ctx).apply {
                // Match the Compose box. Left at the default wrap-content, the
                // page sizes itself to its content, and a 100%-high iframe inside
                // a zero-high page is invisible.
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(android.graphics.Color.BLACK)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                webChromeClient = WebChromeClient()
                loadDataWithBaseURL(
                    "https://youtube.com",
                    playerHtml(videoId, startSeconds),
                    "text/html",
                    "utf-8",
                    null
                )
                handle.webView = this
            }
        }
    )
    DisposableEffect(Unit) {
        onDispose {
            handle.webView?.destroy()
            handle.webView = null
        }
    }
}

private fun playerHtml(videoId: String, start: Int) = """
<!DOCTYPE html>
<html>
<head>
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
  <style>
    html, body { margin: 0; height: 100%; background: black; overflow: hidden; }
    iframe { width: 100%; height: 100%; border: none; }
  </style>
</head>
<body>
  <iframe id="player"
    src="https://www.youtube.com/embed/$videoId?enablejsapi=1&playsinline=1&modestbranding=1&rel=0&fs=0&start=$start&origin=https://youtube.com"
    allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"></iframe>
  <script src="https://www.youtube.com/iframe_api"></script>
  <script>
    var player;
    function onYouTubeIframeAPIReady() { player = new YT.Player('player'); }
    function seekTo(s) { if (player && player.seekTo) { player.seekTo(s, true); player.playVideo(); } }
    function getCurrentTime() { return (player && player.getCurrentTime) ? player.getCurrentTime() : 0; }
  </script>
</body>
</html>
""".trimIndent()
