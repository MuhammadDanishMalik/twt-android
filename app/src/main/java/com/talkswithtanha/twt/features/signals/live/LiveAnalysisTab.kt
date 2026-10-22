package com.talkswithtanha.twt.features.signals.live

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.talkswithtanha.twt.core.data.LiveAnalysis
import com.talkswithtanha.twt.core.data.LiveAnalysisRepository
import com.talkswithtanha.twt.core.data.LiveApi
import com.talkswithtanha.twt.core.data.LiveDrawing
import com.talkswithtanha.twt.core.data.Snapshot
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.designsystem.WebFonts
import com.talkswithtanha.twt.core.designsystem.bouncyClick
import com.talkswithtanha.twt.core.designsystem.brand.PairFlagBadge
import com.talkswithtanha.twt.core.designsystem.components.RollingNumber
import com.talkswithtanha.twt.core.designsystem.components.SegmentedControl
import com.talkswithtanha.twt.core.designsystem.staggeredAppear
import com.talkswithtanha.twt.core.model.Signal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Locale
import javax.inject.Inject

data class LiveUiState(
    val analyses: List<LiveAnalysis> = emptyList(),
    val loaded: Boolean = false
)

@HiltViewModel
class LiveAnalysisViewModel @Inject constructor(
    repository: LiveAnalysisRepository
) : ViewModel() {
    val state: StateFlow<LiveUiState> = repository.observeLive()
        .map { snap ->
            when (snap) {
                is Snapshot.Data -> LiveUiState(snap.value, loaded = true)
                // Refused or offline: the chart still works, only the levels are missing.
                is Snapshot.Failed -> LiveUiState(emptyList(), loaded = true)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LiveUiState())
}

/** A trade's entry, stop and targets, as lines for the chart. */
fun Signal.chartLines(): List<ChartLine> {
    fun n(v: String) = v.filter { it.isDigit() || it == '.' }.toDoubleOrNull()?.takeIf { it > 0 }
    return buildList {
        n(entryPrice)?.let { add(ChartLine(it, "Entry", Color(0xFF2563EB))) }
        n(stopLoss)?.let { add(ChartLine(it, "SL", Color(0xFFDC2626))) }
        takeProfits.forEach { tp -> n(tp.price)?.let { add(ChartLine(it, tp.label + if (tp.isHit) " ✓" else "", Color(0xFF16A34A))) } }
    }
}

private val KIND_LABEL = mapOf(
    LiveDrawing.Kind.LEVEL to "Level",
    LiveDrawing.Kind.ZONE to "Zone",
    LiveDrawing.Kind.TREND to "Trend line",
    LiveDrawing.Kind.NOTE to "Note",
    LiveDrawing.Kind.VIDEO to "Video note",
)

/**
 * Signals → Live: Tanha's levels on the live chart. He draws them in the
 * panel; they arrive here as he draws.
 */
@Composable
fun LiveAnalysisTab(
    signals: List<Signal>,
    viewModel: LiveAnalysisViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }
    var tfKey by rememberSaveable { mutableStateOf<String?>(null) }
    var shownTrade by rememberSaveable { mutableStateOf<String?>(null) }
    var quote by remember { mutableStateOf<LiveApi.Quote?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }

    val key = chosen ?: state.analyses.firstOrNull()?.key ?: "XAUUSD"
    val instrument = LiveApi.find(key) ?: LiveApi.INSTRUMENTS.first()
    val analysis = state.analyses.firstOrNull { it.key == instrument.key }
    val tf = tfKey?.let { LiveApi.Timeframe.of(it) } ?: analysis?.timeframe ?: LiveApi.Timeframe.M15
    val liveKeys = state.analyses.map { it.key }.toSet()
    var query by rememberSaveable { mutableStateOf("") }
    // Live markets first while browsing; best match first while searching.
    val markets = if (query.isBlank()) LiveApi.INSTRUMENTS.sortedByDescending { it.key in liveKeys } else LiveApi.search(query)

    val openTrades = signals.filter { !it.isAnalysis && it.status.isOngoing && LiveApi.find(it.pair)?.key == instrument.key }
    val trade = openTrades.firstOrNull { it.id == shownTrade }
    val drawings = analysis?.drawings.orEmpty()

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            Modifier
                .padding(horizontal = 16.dp)
                .padding(top = 6.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(IosColors.CardSurface)
                .border(1.dp, IosColors.Rim, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = IosColors.TextSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text("Search: XAU, EURUSD, gold, Dow, oil…", color = Color.White.copy(alpha = 0.3f), fontSize = 14.sp, fontFamily = WebFonts.Body)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp, fontFamily = WebFonts.Body),
                    cursorBrush = SolidColor(IosColors.BuyGreen),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, capitalization = KeyboardCapitalization.Characters),
                    keyboardActions = KeyboardActions(onSearch = {
                        LiveApi.search(query).firstOrNull()?.let { chosen = it.key; tfKey = null; shownTrade = null; quote = null }
                        query = ""
                        focus.clearFocus()
                    }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (query.isNotEmpty()) {
                Text(
                    "${markets.size}",
                    color = IosColors.TextSecondary,
                    fontSize = 12.sp,
                    fontFamily = WebFonts.Body,
                    modifier = Modifier.padding(start = 8.dp)
                )
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Clear search",
                    tint = IosColors.TextSecondary,
                    modifier = Modifier.padding(start = 8.dp).size(18.dp).bouncyClick { query = "" }
                )
            }
        }

        if (query.isNotEmpty() && markets.isEmpty()) {
            Text("No market matches \"$query\".", color = IosColors.TextSecondary, fontSize = 13.sp, fontFamily = WebFonts.Body, modifier = Modifier.padding(horizontal = 16.dp))
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 6.dp)
        ) {
            items(markets, key = { it.key }) { m ->
                val on = m.key == instrument.key
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (on) Color(0x1A33C759) else IosColors.CardSurface)
                        .border(1.dp, if (on) IosColors.BuyGreen.copy(alpha = 0.6f) else IosColors.Rim, RoundedCornerShape(50))
                        .bouncyClick {
                            chosen = m.key
                            query = ""
                            focus.clearFocus()
                            tfKey = null
                            shownTrade = null
                            quote = null
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    PairFlagBadge(m.pair, size = 16.dp)
                    Text(m.pair, color = if (on) Color.White else IosColors.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = WebFonts.Body)
                    if (m.key in liveKeys) LiveDot()
                }
            }
        }

        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PairFlagBadge(instrument.pair, size = 26.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(instrument.pair, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, fontFamily = WebFonts.Display)
                    Text(
                        when {
                            analysis != null -> "Tanha's analysis · updated ${ago(analysis.updatedAt?.time, now)}"
                            state.loaded -> "No levels from Tanha on this market right now"
                            else -> instrument.label
                        },
                        color = IosColors.TextSecondary,
                        fontSize = 12.sp,
                        fontFamily = WebFonts.Body
                    )
                }
                RollingNumber(
                    text = quote?.let { String.format(Locale.US, "%.${instrument.decimals}f", it.price) } ?: "—",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    style = androidx.compose.ui.text.TextStyle(fontFamily = WebFonts.Mono)
                )
            }

            SegmentedControl(
                options = LiveApi.Timeframe.entries.map { it.label },
                selectedIndex = LiveApi.Timeframe.entries.indexOf(tf),
                onSelect = { tfKey = LiveApi.Timeframe.entries[it].key }
            )

            LiveCandleChart(
                instrument = instrument,
                timeframe = tf,
                height = 380.dp,
                drawings = drawings,
                lines = trade?.chartLines().orEmpty(),
                onQuote = { quote = it }
            )

            if (analysis?.note?.isNotBlank() == true) {
                InfoCard("Tanha's read", Modifier.staggeredAppear(0)) {
                    Text(analysis.note, color = Color.White.copy(alpha = 0.88f), fontSize = 14.sp, lineHeight = 21.sp, fontFamily = WebFonts.Body)
                }
            }

            if (drawings.isNotEmpty()) {
                InfoCard("On the chart", Modifier.staggeredAppear(1)) {
                    drawings.sortedByDescending { it.p1 }.forEach { d ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(drawColor(d.color)))
                            Spacer(Modifier.width(10.dp))
                            Text(d.label.ifBlank { KIND_LABEL[d.kind].orEmpty() }, color = Color.White, fontSize = 14.sp, fontFamily = WebFonts.Body, modifier = Modifier.weight(1f))
                            val fmt = "%.${instrument.decimals}f"
                            Text(
                                if (d.kind == LiveDrawing.Kind.ZONE) {
                                    val a = minOf(d.p1, d.p2 ?: d.p1)
                                    val b = maxOf(d.p1, d.p2 ?: d.p1)
                                    "${String.format(Locale.US, fmt, a)} – ${String.format(Locale.US, fmt, b)}"
                                } else String.format(Locale.US, fmt, d.p1),
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = WebFonts.Mono
                            )
                        }
                    }
                }
            }

            if (openTrades.isNotEmpty()) {
                InfoCard("Open trades on ${instrument.pair}", Modifier.staggeredAppear(2)) {
                    openTrades.forEach { t ->
                        val on = t.id == shownTrade
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (on) Color(0x1A33C759) else Color.Transparent)
                                .border(1.dp, if (on) IosColors.BuyGreen.copy(alpha = 0.5f) else IosColors.Rim, RoundedCornerShape(12.dp))
                                .bouncyClick { shownTrade = if (on) null else t.id }
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(t.type.name, color = if (t.type.name == "BUY") IosColors.BuyGreen else IosColors.SellRed, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = WebFonts.Body)
                            Spacer(Modifier.width(8.dp))
                            Text("${t.timeframe} · ${t.status.stored}", color = Color.White, fontSize = 13.sp, fontFamily = WebFonts.Body, modifier = Modifier.weight(1f))
                            Text(if (on) "Hide levels" else "Show on chart", color = IosColors.TextSecondary, fontSize = 12.sp, fontFamily = WebFonts.Body)
                        }
                    }
                }
            }

            Text(
                "For analysis, not for execution. Prices are from free public feeds and can differ slightly from your broker's.",
                color = Color.White.copy(alpha = 0.3f),
                fontSize = 11.sp,
                fontFamily = WebFonts.Body
            )
        }
    }
}

@Composable
private fun InfoCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(IosColors.CardSurface)
            .border(1.dp, IosColors.Rim, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, color = IosColors.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = WebFonts.Body)
        content()
    }
}

/** A red dot that breathes: this market has Tanha's levels on it now. */
@Composable
fun LiveDot(modifier: Modifier = Modifier) {
    val pulse by rememberInfiniteTransition(label = "liveDot").animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "liveDotScale"
    )
    Box(
        modifier
            .size(7.dp)
            .scale(pulse)
            .clip(CircleShape)
            .background(IosColors.SellRed)
    )
}

private fun ago(at: Long?, now: Long): String {
    if (at == null) return ""
    val m = ((now - at) / 60_000).coerceAtLeast(0)
    return when {
        m < 1 -> "just now"
        m < 60 -> "${m}m ago"
        m < 1440 -> "${m / 60}h ago"
        else -> "${m / 1440}d ago"
    }
}
