package com.talkswithtanha.twt.features.access

import androidx.compose.material.icons.outlined.MailOutline
import com.talkswithtanha.twt.core.designsystem.readableWidth
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.talkswithtanha.twt.R
import com.talkswithtanha.twt.core.designsystem.Haptics
import com.talkswithtanha.twt.core.designsystem.RiseIn
import com.talkswithtanha.twt.core.designsystem.WebBackdrop
import com.talkswithtanha.twt.core.designsystem.WebButton
import com.talkswithtanha.twt.core.designsystem.WebButtonStyle
import com.talkswithtanha.twt.core.designsystem.WebColors
import com.talkswithtanha.twt.core.designsystem.WebFonts
import com.talkswithtanha.twt.core.designsystem.WebHeadline
import com.talkswithtanha.twt.core.designsystem.WebType
import com.talkswithtanha.twt.core.model.AccessCode
import com.talkswithtanha.twt.core.designsystem.components.TwtTextField

private const val SAMPLE_CODE = "TWT-4H2K-9XQP"

/**
 * The front door: one box, one button.
 *
 * Code entry, and nothing about how to get one. Access is sold outside the
 * app, and Google Play's Payments policy forbids steering people from inside it
 * to a way of paying that is not Play Billing — which a "message us on WhatsApp
 * for a code" button is, because that conversation is where payment happens.
 * So the screen only unlocks access that already exists; the one way out is a
 * neutral support email for people whose code does not work.
 */
@Composable
fun AccessGateScreen(viewModel: AccessGateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val support by viewModel.support.collectAsStateWithLifecycle()
    val user by viewModel.user.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    fun open(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
    }

    val email = user?.email.orEmpty()
    val contactSupport = {
        Haptics.tap(haptics)
        open(support.mailtoUrl("TWT Global app help" + if (email.isNotBlank()) " ($email)" else ""))
    }

    LaunchedEffect(state.error) { if (state.error != null) Haptics.failure(haptics) }
    LaunchedEffect(state.redeemed) { if (state.redeemed) Haptics.success(haptics) }

    WebBackdrop {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .readableWidth()
                .safeDrawingPadding()
                .imePadding()
        ) {
            // At least a screen tall, so the three groups spread out; taller
            // when the keyboard is up, so everything can still be scrolled to.
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                TopBar(onSignOut = viewModel::signOut)

                Column {
                    RiseIn { LockBadge(unlocked = state.redeemed) }
                    Spacer(Modifier.height(20.dp))
                    WebHeadline(lead = "Enter your", accent = "access code.", size = 40.sp, startDelayMillis = 60)
                    Spacer(Modifier.height(10.dp))
                    RiseIn(delayMillis = 240) {
                        Text(
                            "Members receive a personal access code. Enter it once and the app unlocks.",
                            style = WebType.lead
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                    RiseIn(delayMillis = 320) {
                        Column {
                            CodeField(
                                code = state.code,
                                error = state.error,
                                enabled = !state.isRedeeming,
                                canSubmit = state.canSubmit,
                                onCodeChange = viewModel::onCodeChange,
                                onSubmit = viewModel::redeem
                            )
                            Spacer(Modifier.height(14.dp))
                            WebButton(
                                "Unlock TWT",
                                onClick = viewModel::redeem,
                                enabled = state.canSubmit,
                                loading = state.isRedeeming,
                                icon = Icons.AutoMirrored.Filled.ArrowForward
                            )
                        }
                    }
                }

                RiseIn(delayMillis = 420) {
                    Column(Modifier.padding(top = 32.dp, bottom = 16.dp)) {
                        Text(
                            "Code not working?",
                            style = WebType.small.copy(color = WebColors.Text2),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        WebButton(
                            "Contact support",
                            onClick = contactSupport,
                            style = WebButtonStyle.Ghost,
                            icon = Icons.Outlined.MailOutline,
                            height = 48.dp
                        )
                    }
                }
            }
        }
    }

}

@Composable
private fun TopBar(onSignOut: () -> Unit) {
    Row(
        Modifier.padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painterResource(R.drawable.twt_logo),
            contentDescription = "TWT",
            modifier = Modifier.size(32.dp).clip(CircleShape)
        )
        Spacer(Modifier.width(10.dp))
        Text("TWT Global", style = WebType.h3.copy(fontSize = 17.sp))
        Spacer(Modifier.weight(1f))
        Text(
            "Sign out",
            style = WebType.small.copy(fontWeight = FontWeight.Medium),
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onSignOut)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        )
    }
}

/** A padlock on a soft green disc that breathes, and opens once redeemed. */
@Composable
private fun LockBadge(unlocked: Boolean) {
    val pulse = rememberInfiniteTransition(label = "lock")
    val ring by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "ring"
    )
    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(64.dp)
                .graphicsLayer {
                    val s = 0.85f + 0.15f * ring
                    scaleX = s
                    scaleY = s
                    alpha = 0.6f - 0.4f * ring
                }
                .clip(CircleShape)
                .background(WebColors.BrandSoft)
        )
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(if (unlocked) WebColors.Brand else WebColors.BrandSoft)
                .border(1.dp, WebColors.Brand.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (unlocked) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
                contentDescription = null,
                tint = if (unlocked) WebColors.OnBrand else WebColors.Brand,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/** The code box: a Material 3 field in mono, with a tick once the shape is right. */
@Composable
private fun CodeField(
    code: String,
    error: String?,
    enabled: Boolean,
    canSubmit: Boolean,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val looksRight = AccessCode.isWellFormed(AccessCode.normalise(code))
    TwtTextField(
        value = code,
        onValueChange = onCodeChange,
        label = "Access code",
        placeholder = SAMPLE_CODE,
        enabled = enabled,
        isError = error != null,
        supportingText = error ?: "Codes never contain O, I, 0, 1 or S.",
        capitalization = KeyboardCapitalization.Characters,
        keyboardType = KeyboardType.Ascii,
        autoCorrect = false,
        imeAction = ImeAction.Done,
        keyboardActions = KeyboardActions(onDone = { if (canSubmit) onSubmit() }),
        textStyle = TextStyle(
            fontFamily = WebFonts.Mono,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            letterSpacing = 0.08.em,
            color = WebColors.Text
        ),
        trailing = if (looksRight && error == null) {
            {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Looks right",
                    tint = WebColors.Brand
                )
            }
        } else null,
        modifier = Modifier.fillMaxWidth()
    )
}
