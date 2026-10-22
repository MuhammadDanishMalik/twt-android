package com.talkswithtanha.twt.features.signals.live

import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import com.talkswithtanha.twt.core.data.LiveDrawing
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.designsystem.WebFonts
import com.talkswithtanha.twt.core.designsystem.bouncyClick
import com.talkswithtanha.twt.core.designsystem.components.LoadingLabel

/**
 * Tanha on camera about one candle, played with sound — the tap that opened
 * it is the go-ahead. The platform's own VideoView, so no player library.
 */
@Composable
fun VideoNotePlayer(note: LiveDrawing, onClose: () -> Unit) {
    var ready by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val accent = drawColor(note.color)

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // The same squircle as the website's video notes, in the note's colour.
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(SquircleShape)
                    .background(accent)
                    .padding(3.dp)
                    .clip(SquircleShape)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { context ->
                        VideoView(context).apply {
                            setMediaController(MediaController(context).also { it.setAnchorView(this) })
                            setOnPreparedListener { player ->
                                player.isLooping = true
                                ready = true
                                start()
                            }
                            setOnErrorListener { _, _, _ ->
                                failed = true
                                true
                            }
                            setVideoURI(note.url.orEmpty().toUri())
                        }
                    },
                    onRelease = { it.stopPlayback() },
                    modifier = Modifier.fillMaxSize()
                )
                when {
                    failed -> Text("The video could not play.", color = IosColors.TextSecondary, fontSize = 13.sp, fontFamily = WebFonts.Body)
                    !ready -> LoadingLabel("Loading video")
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xE60B0B0C))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f, fill = false)) {
                    Text(
                        note.label.ifBlank { "Tanha on this candle" },
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = WebFonts.Body
                    )
                    note.dur?.let {
                        Text("%d:%02d".format(it / 60, it % 60), color = IosColors.TextSecondary, fontSize = 12.sp, fontFamily = WebFonts.Mono)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.12f))
                        .bouncyClick(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}

/** A superellipse: the iOS app-icon shape. */
private val SquircleShape = GenericShape { size, _ ->
    val cx = size.width / 2f
    val cy = size.height / 2f
    val k = 0.76f
    moveTo(cx, 0f)
    cubicTo(cx + cx * k, 0f, size.width, cy - cy * k, size.width, cy)
    cubicTo(size.width, cy + cy * k, cx + cx * k, size.height, cx, size.height)
    cubicTo(cx - cx * k, size.height, 0f, cy + cy * k, 0f, cy)
    cubicTo(0f, cy - cy * k, cx - cx * k, 0f, cx, 0f)
    close()
}
