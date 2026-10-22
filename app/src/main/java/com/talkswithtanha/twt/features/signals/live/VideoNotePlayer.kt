package com.talkswithtanha.twt.features.signals.live

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
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
import com.talkswithtanha.twt.core.data.LiveDrawing
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.designsystem.WebFonts
import com.talkswithtanha.twt.core.designsystem.bouncyClick
import com.talkswithtanha.twt.core.designsystem.components.LoadingLabel

/**
 * Tanha on camera about one candle, played with sound — the tap that opened
 * it is the go-ahead. The platform's own MediaPlayer, so no player library.
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
                // A TextureView rather than VideoView: a SurfaceView draws on its
                // own layer and ignores the squircle clip. Cropped to fill, like
                // the website's object-fit: cover.
                AndroidView(
                    factory = { context ->
                        TextureView(context).apply {
                            val view = this
                            var player: MediaPlayer? = null
                            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                                    player = MediaPlayer().apply {
                                        setSurface(Surface(texture))
                                        isLooping = true
                                        setOnPreparedListener { mp ->
                                            fill(view, mp.videoWidth, mp.videoHeight)
                                            ready = true
                                            mp.start()
                                        }
                                        setOnErrorListener { _, _, _ ->
                                            failed = true
                                            true
                                        }
                                        runCatching {
                                            setDataSource(note.url.orEmpty())
                                            prepareAsync()
                                        }.onFailure { failed = true }
                                    }
                                }

                                override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
                                    player?.let { fill(view, it.videoWidth, it.videoHeight) }
                                }

                                override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                                    player?.release()
                                    player = null
                                    return true
                                }

                                override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                            }
                        }
                    },
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

/** Scales the video so it covers the view, centred, cropping what falls outside. */
private fun fill(view: TextureView, videoW: Int, videoH: Int) {
    if (videoW <= 0 || videoH <= 0 || view.width == 0 || view.height == 0) return
    val vw = view.width.toFloat()
    val vh = view.height.toFloat()
    val scale = maxOf(vw / videoW, vh / videoH)
    view.setTransform(Matrix().apply { setScale(videoW * scale / vw, videoH * scale / vh, vw / 2f, vh / 2f) })
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
