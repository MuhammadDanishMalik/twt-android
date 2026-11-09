package com.talkswithtanha.twt.core.designsystem.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

/**
 * Opens the phone's gallery and hands back the picked picture, shrunk and
 * ready to upload. Pictures come from the gallery only — never a pasted link,
 * which is how a post ends up pointing at an image that later disappears.
 */
@Composable
fun rememberGalleryPhotoPicker(onPicked: (ByteArray) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            com.talkswithtanha.twt.core.images.prepareScreenshot(context, uri)?.let { onPicked(it.second) }
        }
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}
