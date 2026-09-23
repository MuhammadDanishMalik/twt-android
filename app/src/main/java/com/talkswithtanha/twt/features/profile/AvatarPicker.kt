package com.talkswithtanha.twt.features.profile

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.images.Cloudinary
import com.talkswithtanha.twt.core.images.cropSquare
import com.talkswithtanha.twt.core.images.decodeForCrop
import kotlinx.coroutines.launch
import java.io.File

/**
 * The avatar, and everything a member can do to it.
 *
 * Tapping opens the three real choices — library, camera, remove — as a sheet
 * rather than cycling through them, because "remove" is destructive and must
 * never be one stray tap away from "change".
 *
 * The picked image goes to [AvatarCropScreen] before it goes anywhere near the
 * network. Nothing is uploaded until the member has seen the square they are
 * actually publishing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarEditor(
    photoUrl: String?,
    initial: String,
    isUploading: Boolean,
    onCropped: (Bitmap) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 96.dp
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showSheet by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<Uri?>(null) }
    var source by remember { mutableStateOf<Bitmap?>(null) }
    var decodeFailed by remember { mutableStateOf(false) }

    // Where a camera capture lands. Remembered so the same file is used by the
    // launcher and the decode that follows it.
    val captureUri = remember {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "capture.jpg")
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) picked = uri }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { saved -> if (saved) picked = captureUri }

    // Decoding is off the main thread and downsampled; see decodeForCrop.
    LaunchedEffect(picked) {
        val uri = picked ?: return@LaunchedEffect
        decodeFailed = false
        val bitmap = decodeForCrop(context, uri)
        if (bitmap == null) {
            decodeFailed = true
            picked = null
        } else {
            source = bitmap
        }
    }

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(Brush.linearGradient(IosColors.AvatarGradient))
                .clickable(enabled = !isUploading) { showSheet = true },
            contentAlignment = Alignment.Center
        ) {
            val rendered = Cloudinary.avatar(photoUrl, size.value.toInt())
            if (rendered != null) {
                AsyncImage(
                    model = rendered,
                    contentDescription = "Profile photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            } else {
                Text(
                    text = initial,
                    color = Color.White,
                    fontSize = (size.value * 0.4f).sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (isUploading) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // The badge that says the circle is a button at all.
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .size(30.dp)
                .clip(CircleShape)
                .background(IosColors.Accent)
                .clickable(enabled = !isUploading) { showSheet = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.PhotoCamera,
                contentDescription = "Change profile photo",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }

    if (decodeFailed) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(2600)
            decodeFailed = false
        }
        Text(
            text = "That image could not be opened. Try another.",
            color = IosColors.SellRed,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
    }

    if (showSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            containerColor = IosColors.SecondaryBackground
        ) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
                PickerAction(Icons.Outlined.PhotoLibrary, "Choose from Library") {
                    showSheet = false
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
                PickerAction(Icons.Outlined.PhotoCamera, "Take Photo") {
                    showSheet = false
                    cameraLauncher.launch(captureUri)
                }
                if (photoUrl != null) {
                    PickerAction(
                        icon = Icons.Outlined.Delete,
                        title = "Remove Current Photo",
                        tint = IosColors.SellRed
                    ) {
                        showSheet = false
                        onRemove()
                    }
                }
            }
        }
    }

    source?.let { bitmap ->
        // In a dialog rather than inline. This component is used inside a
        // scrolling column, where a fillMaxSize child is measured against an
        // unbounded height and collapses to a band a few pixels tall — the crop
        // screen has to escape its parent's layout to be a screen at all.
        Dialog(
            onDismissRequest = {
                if (!isUploading) {
                    source = null
                    picked = null
                }
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = !isUploading,
                dismissOnClickOutside = false
            )
        ) {
            AvatarCropScreen(
                source = bitmap,
                isBusy = isUploading,
                onCancel = {
                    source = null
                    picked = null
                },
                onConfirm = { zoom, offsetX, offsetY, windowPx ->
                    // The cut happens here rather than in the caller, so what
                    // leaves this component is the square the member framed and
                    // nothing else has to know how the crop screen works.
                    scope.launch {
                        val square = cropSquare(bitmap, windowPx, zoom, offsetX, offsetY)
                        source = null
                        picked = null
                        onCropped(square)
                    }
                }
            )
        }
    }
}

@Composable
private fun PickerAction(
    icon: ImageVector,
    title: String,
    tint: Color = IosColors.TextPrimary,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Text(text = title, color = tint, fontSize = 17.sp)
    }
}
