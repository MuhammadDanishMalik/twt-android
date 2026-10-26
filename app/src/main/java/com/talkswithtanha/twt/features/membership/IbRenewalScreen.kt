package com.talkswithtanha.twt.features.membership

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.talkswithtanha.twt.core.data.IbRenewal
import com.talkswithtanha.twt.core.data.IbRenewalRepository
import com.talkswithtanha.twt.core.designsystem.WebBackdrop
import com.talkswithtanha.twt.core.designsystem.WebButton
import com.talkswithtanha.twt.core.designsystem.WebButtonStyle
import com.talkswithtanha.twt.core.designsystem.WebColors
import com.talkswithtanha.twt.core.designsystem.WebType
import com.talkswithtanha.twt.core.model.User
import com.talkswithtanha.twt.core.session.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

data class IbRenewalUiState(
    val user: User? = null,
    val latest: IbRenewal? = null,
    val picked: Bitmap? = null,
    val pickedJpeg: ByteArray? = null,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    /** A declined request is shown until they choose to send a new one. */
    val startedOver: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class IbRenewalViewModel @Inject constructor(
    private val session: SessionRepository,
    private val repository: IbRenewalRepository
) : ViewModel() {

    private val local = MutableStateFlow(IbRenewalUiState())

    val state: StateFlow<IbRenewalUiState> = combine(
        session.currentUser,
        session.currentUser.flatMapLatest { u -> if (u == null) flowOf(null) else repository.observeLatest(u.id) },
        local
    ) { user, latest, l -> l.copy(user = user, latest = latest) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IbRenewalUiState())

    fun onPicked(context: Context, uri: Uri) {
        viewModelScope.launch {
            val prepared = withContext(Dispatchers.IO) { runCatching { prepare(context, uri) }.getOrNull() }
            if (prepared == null) {
                local.update { it.copy(error = "That image could not be opened. Try another screenshot.") }
            } else {
                local.update { it.copy(picked = prepared.first, pickedJpeg = prepared.second, error = null) }
            }
        }
    }

    fun clearPicked() = local.update { it.copy(picked = null, pickedJpeg = null) }

    fun startOver() = local.update { it.copy(startedOver = true, picked = null, pickedJpeg = null, error = null) }

    fun submit() {
        val s = state.value
        val user = s.user ?: return
        val jpeg = s.pickedJpeg ?: return
        if (s.isSubmitting) return
        local.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            runCatching {
                repository.submit(user.id, user.fullName, user.email, user.membershipExpiresAt, jpeg)
            }.onSuccess {
                local.update { it.copy(isSubmitting = false, picked = null, pickedJpeg = null, startedOver = false) }
            }.onFailure {
                local.update { it.copy(isSubmitting = false, error = "That did not upload. Check your connection and try again.") }
            }
        }
    }

    fun signOut() = session.signOut()

    /** Downscaled to 1600px on the long edge and JPEG 85: legible, and light on data. */
    private fun prepare(context: Context, uri: Uri): Pair<Bitmap, ByteArray> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) sample *= 2
        val bitmap = context.contentResolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("undecodable")
        val scale = 1600f / maxOf(bitmap.width, bitmap.height)
        val sized = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
        val bytes = ByteArrayOutputStream().use { out ->
            sized.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.toByteArray()
        }
        return sized to bytes
    }
}

/**
 * Renewing an IB member's month.
 *
 * IB members do not pay: their month continues while they trade under
 * Tanha's IB link, which the team confirms from a screenshot of the IB
 * analytics in their broker's portal. Shown full screen when the month has
 * ended ([expired]), and from the reminder in the last three days before.
 */
@Composable
fun IbRenewalScreen(
    expired: Boolean,
    onClose: () -> Unit = {},
    viewModel: IbRenewalViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.onPicked(context, uri)
    }
    val latest = state.latest
    val sounds = com.talkswithtanha.twt.core.sound.rememberAppSounds()
    var lastStatus by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<IbRenewal.Status?>(null) }
    androidx.compose.runtime.LaunchedEffect(latest?.status) {
        val now = latest?.status
        if (lastStatus == IbRenewal.Status.PENDING && now == IbRenewal.Status.APPROVED) sounds.play(com.talkswithtanha.twt.core.sound.AppSounds.Sound.SUCCESS)
        if (lastStatus == IbRenewal.Status.PENDING && now == IbRenewal.Status.DECLINED) sounds.play(com.talkswithtanha.twt.core.sound.AppSounds.Sound.ALERT)
        lastStatus = now
    }
    // What to show: the request under review, the one declined (until they
    // choose to start over), or the form.
    val stage = when {
        latest?.status == IbRenewal.Status.PENDING -> "pending"
        latest?.status == IbRenewal.Status.DECLINED && !state.startedOver -> "declined"
        else -> "form"
    }
    val ends = state.user?.membershipExpiresAt?.let { SimpleDateFormat("d MMMM", Locale.getDefault()).format(it) }

    WebBackdrop {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!expired) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).border(1.dp, WebColors.Line2, CircleShape).clickable(onClick = onClose),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Outlined.Close, contentDescription = "Close", tint = WebColors.Text, modifier = Modifier.size(18.dp)) }
                }
                Spacer(Modifier.weight(1f))
                if (expired) {
                    Text(
                        "Sign out",
                        style = WebType.small.copy(fontWeight = FontWeight.Medium),
                        modifier = Modifier.clip(CircleShape).clickable { viewModel.signOut() }.padding(8.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(WebColors.BrandSoft),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Outlined.Insights, contentDescription = null, tint = WebColors.Brand, modifier = Modifier.size(28.dp)) }
            Spacer(Modifier.height(18.dp))

            AnimatedContent(targetState = stage, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "stage") { s ->
                Column {
                    when (s) {
                        "pending" -> {
                            Text("Under review", style = WebType.h3)
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Thanks. The team is checking your IB analytics. You'll get an email the moment it's decided" +
                                    if (expired) ", and the app opens again as soon as it's accepted." else ".",
                                style = WebType.lead
                            )
                            Spacer(Modifier.height(22.dp))
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(WebColors.Ink3).padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Outlined.HourglassTop, contentDescription = null, tint = WebColors.Amber, modifier = Modifier.size(22.dp))
                                Text("Usually within a few hours.", style = WebType.small.copy(color = WebColors.Text))
                            }
                        }
                        "declined" -> {
                            Text("We couldn't verify it", style = WebType.h3)
                            Spacer(Modifier.height(10.dp))
                            Text(
                                latest?.reviewNote ?: "The screenshot didn't show an account under the TWT IB link.",
                                style = WebType.lead
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "To keep your membership, join again under the TWT IB link, then send a new screenshot of your IB analytics.",
                                style = WebType.small.copy(color = WebColors.Text2)
                            )
                            Spacer(Modifier.height(24.dp))
                            WebButton(text = "Send a new screenshot", onClick = viewModel::startOver)
                        }
                        else -> {
                            Text(if (expired) "Your month has ended" else "Your month ends ${ends ?: "soon"}", style = WebType.h3)
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "As an IB member, you renew by sharing a screenshot of the IB analytics in your broker's portal. The team checks it and extends your membership for another month.",
                                style = WebType.lead
                            )
                            Spacer(Modifier.height(22.dp))
                            val picked = state.picked
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(if (picked != null) 320.dp else 170.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(WebColors.Ink3)
                                    .border(1.dp, if (picked != null) WebColors.Brand.copy(alpha = 0.5f) else WebColors.Line2, RoundedCornerShape(18.dp))
                                    .clickable(enabled = !state.isSubmitting) {
                                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (picked != null) {
                                    Image(picked.asImageBitmap(), contentDescription = "Your IB analytics screenshot", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(8.dp))
                                } else {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, tint = WebColors.Brand, modifier = Modifier.size(30.dp))
                                        Spacer(Modifier.height(8.dp))
                                        Text("Choose your IB analytics screenshot", style = WebType.small.copy(color = WebColors.Text, fontWeight = FontWeight.Medium))
                                        Text("It should show your account under the TWT IB link", style = WebType.small, textAlign = TextAlign.Center)
                                    }
                                }
                            }
                            if (picked != null) {
                                Text(
                                    "Choose a different one",
                                    style = WebType.small.copy(fontWeight = FontWeight.Medium),
                                    modifier = Modifier.padding(top = 8.dp).clickable(enabled = !state.isSubmitting) {
                                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    }.padding(vertical = 6.dp)
                                )
                            }
                            state.error?.let {
                                Spacer(Modifier.height(10.dp))
                                Text(it, style = WebType.small.copy(color = WebColors.Red))
                            }
                            Spacer(Modifier.height(20.dp))
                            WebButton(
                                text = if (state.isSubmitting) "Uploading…" else "Send for review",
                                onClick = viewModel::submit,
                                enabled = picked != null && !state.isSubmitting,
                                loading = state.isSubmitting
                            )
                            if (!expired) {
                                Spacer(Modifier.height(8.dp))
                                WebButton(text = "Later", onClick = onClose, style = WebButtonStyle.Ghost)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
            Text(
                "Your screenshot is private: only you and the TWT team can see it.",
                style = WebType.small.copy(color = WebColors.Text3, fontSize = 12.sp)
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}


/**
 * The reminder in an IB member's last three days: a card over the main tabs,
 * once per launch, that opens [IbRenewalScreen]. Hidden while a request is
 * already under review.
 */
@Composable
fun IbRenewalReminder(
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IbRenewalViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var dismissed by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val user = state.user ?: return
    if (dismissed || !user.ibRenewalDueSoon || state.latest?.status == IbRenewal.Status.PENDING) return
    val days = user.membershipExpiresAt?.let { ((it.time - System.currentTimeMillis()) / (24 * 3600 * 1000L)).coerceAtLeast(0) } ?: 0

    Row(
        modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(WebColors.Ink3)
            .border(1.dp, WebColors.Brand.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
            .clickable { dismissed = true; onOpen() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Outlined.Insights, contentDescription = null, tint = WebColors.Brand, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (days == 0L) "Your month ends today" else "Your month ends in $days day${if (days == 1L) "" else "s"}",
                style = WebType.small.copy(color = WebColors.Text, fontWeight = FontWeight.SemiBold)
            )
            Text("Share your IB analytics to renew", style = WebType.small)
        }
        Icon(
            Icons.Outlined.Close,
            contentDescription = "Dismiss",
            tint = WebColors.Text3,
            modifier = Modifier.size(20.dp).clip(CircleShape).clickable { dismissed = true }
        )
    }
}
