package com.talkswithtanha.twt.features.admin

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talkswithtanha.twt.core.designsystem.IosColors
import kotlinx.coroutines.launch

/**
 * Two desk steps that now carry proof, so the member and the desk both see
 * what happened:
 *  - the money arrived: a screenshot of the bank or wallet message on the
 *    dealer's own phone;
 *  - the member's funds were sent: a screenshot of the transfer, plus its
 *    transaction id or hash.
 * The button stays off until the screenshot is attached.
 */
@Composable
internal fun DeskProofDialog(
    title: String,
    body: String,
    shotLabel: String,
    referenceLabel: String?,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (reference: String, proof: ByteArray) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var shot by remember { mutableStateOf<Pair<Bitmap, ByteArray>?>(null) }
    var reference by remember { mutableStateOf("") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { shot = com.talkswithtanha.twt.core.images.prepareScreenshot(context, uri) }
    }
    val ready = shot != null && (referenceLabel == null || reference.isNotBlank())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(body)
                Box(
                    Modifier.fillMaxWidth().height(if (shot == null) 110.dp else 220.dp).clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, if (shot == null) IosColors.BuyGreen else Color.Transparent, RoundedCornerShape(14.dp))
                        .clickable { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    contentAlignment = Alignment.Center
                ) {
                    val s = shot
                    if (s == null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("＋", color = IosColors.BuyGreen, fontSize = 26.sp)
                            Text(shotLabel, color = IosColors.BuyGreen, fontSize = 14.sp)
                            Text("Required", color = IosColors.TextSecondary, fontSize = 12.sp)
                        }
                    } else {
                        Image(s.first.asImageBitmap(), shotLabel, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(220.dp))
                    }
                }
                if (shot != null) Text("Tap the picture to change it.", color = IosColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 0.dp))
                referenceLabel?.let {
                    OutlinedTextField(reference, { reference = it }, label = { Text(it) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(enabled = ready, onClick = { shot?.let { onConfirm(reference.trim(), it.second) } }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not yet") } }
    )
}
