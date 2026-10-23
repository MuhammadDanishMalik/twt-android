package com.talkswithtanha.twt.features.marketplace

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.talkswithtanha.twt.core.data.DealRepository
import com.talkswithtanha.twt.core.data.PrefsRepository
import com.talkswithtanha.twt.core.data.Snapshot
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.designsystem.WebFonts
import com.talkswithtanha.twt.core.model.Deal
import com.talkswithtanha.twt.core.model.DealStatus
import com.talkswithtanha.twt.core.model.Turn
import com.talkswithtanha.twt.core.model.clockText
import com.talkswithtanha.twt.core.model.progress
import com.talkswithtanha.twt.core.session.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One change worth saying out loud. */
data class DealNews(val dealId: String, val title: String, val body: String, val at: Long)

/**
 * The member's deals, wherever they are in the app: a banner and a soft tone
 * when one changes, and the open deals for the floating pill.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DealPulseViewModel @Inject constructor(
    session: SessionRepository,
    deals: DealRepository,
    prefs: PrefsRepository
) : ViewModel() {

    private val mine: StateFlow<List<Deal>> = session.currentUser
        .flatMapLatest { u -> if (u == null) flowOf(emptyList()) else deals.observeDeals(u.id).map { (it as? Snapshot.Data)?.value.orEmpty() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val sound: StateFlow<Boolean> = session.currentUser
        .flatMapLatest { u -> if (u == null) flowOf(true) else prefs.observe(u.id).map { it.sound } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val open: StateFlow<List<Deal>> = mine
        .map { list -> list.filter { !it.status.isTerminal } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _news = MutableStateFlow<DealNews?>(null)
    val news: StateFlow<DealNews?> = _news.asStateFlow()

    /** Null until the first snapshot: what was already there is history, not news. */
    private var seen: MutableMap<String, DealStatus>? = null

    init {
        viewModelScope.launch {
            mine.collect { list ->
                val known = seen
                if (known == null) {
                    if (list.isNotEmpty() || session.currentUser.value != null) {
                        seen = list.associate { it.id to it.status }.toMutableMap()
                    }
                    return@collect
                }
                for (deal in list) {
                    val before = known[deal.id]
                    known[deal.id] = deal.status
                    if (before == null || before == deal.status) continue
                    _news.value = DealNews(deal.id, "Deal ${deal.reference}", words(deal), System.currentTimeMillis())
                    if (sound.value) chime()
                }
            }
        }
    }

    fun dismiss() {
        _news.value = null
    }

    private fun words(deal: Deal): String = when (deal.status) {
        DealStatus.PAYMENT_SENT -> "Payment reported. The desk is checking it."
        DealStatus.PAYMENT_APPROVED -> "Your payment arrived. Add where to receive your funds."
        DealStatus.RELEASING -> "The desk is sending your funds."
        DealStatus.COMPLETED -> "Funds sent. Deal complete."
        DealStatus.CANCELLED -> "Deal cancelled. Nothing moved."
        DealStatus.DISPUTED -> "TWT support is reviewing this deal."
        DealStatus.AWAITING_PAYMENT -> "Deal opened."
    }

    /** Two quiet beeps on the notification stream. A missed tone is fine. */
    private fun chime() {
        viewModelScope.launch {
            runCatching {
                val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 30)
                tone.startTone(ToneGenerator.TONE_PROP_ACK, 160)
                delay(400)
                tone.release()
            }
        }
    }
}

/**
 * Drawn once, over every screen, by RootScreen: the banner at the top when a
 * deal changes, and the pill above the tab bar while one is open.
 */
@Composable
fun BoxScope.DealPulseOverlay(
    currentRoute: String?,
    currentDealId: String?,
    pillBottom: androidx.compose.ui.unit.Dp,
    onOpenDeal: (String) -> Unit,
    onOpenDeals: () -> Unit,
    viewModel: DealPulseViewModel = hiltViewModel()
) {
    val open by viewModel.open.collectAsStateWithLifecycle()
    val news by viewModel.news.collectAsStateWithLifecycle()

    LaunchedEffect(news?.at) {
        if (news == null) return@LaunchedEffect
        delay(5_500)
        viewModel.dismiss()
    }

    AnimatedVisibility(
        visible = news != null && news?.dealId != currentDealId,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
        modifier = Modifier.align(Alignment.TopCenter)
    ) {
        news?.let { n ->
            Row(
                Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF1C1C1E))
                    .border(1.dp, IosColors.BuyGreen.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                    .clickable {
                        viewModel.dismiss()
                        onOpenDeal(n.dealId)
                    }
                    .padding(14.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(IosColors.BuyGreen), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.SwapHoriz, null, tint = Color.Black, modifier = Modifier.size(19.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(n.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(n.body, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                }
            }
        }
    }

    // The pill: not on top of the deal it points at.
    val showPill = open.isNotEmpty() && !(open.size == 1 && open.first().id == currentDealId)
    if (showPill) {
        val sorted = open.sortedBy { d ->
            val p = d.progress()
            if (p.turn == Turn.YOU) p.deadline?.time ?: (Long.MAX_VALUE - 2) else Long.MAX_VALUE
        }
        val top = sorted.first()
        val p = top.progress()
        val now = rememberNow()
        val left = p.deadline?.time?.minus(now)
        val mine = p.turn == Turn.YOU
        val label = if (open.size > 1) "${open.size} active deals"
        else "Active deal · ${p.short}" + if (mine && left != null) " ${clockText(left)}" else ""
        val urgent = mine && left != null && left < 120_000

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = pillBottom)
                .clip(RoundedCornerShape(50))
                .background(Color(0xFF2C2C2E))
                .border(1.dp, if (urgent) IosColors.SellRed else if (mine) IosColors.BuyGreen else Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
                .clickable { if (open.size == 1) onOpenDeal(top.id) else onOpenDeals() }
                .padding(horizontal = 16.dp, vertical = 9.dp)
                .semantics(mergeDescendants = true) { contentDescription = "$label. Open" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (urgent) IosColors.SellRed else if (mine) IosColors.BuyGreen else Color.White.copy(alpha = 0.4f)))
            Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = if (mine && left != null) WebFonts.Mono else null)
        }
    }
}
