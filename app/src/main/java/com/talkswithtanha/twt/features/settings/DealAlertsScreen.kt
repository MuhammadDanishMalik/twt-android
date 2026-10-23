package com.talkswithtanha.twt.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.talkswithtanha.twt.core.data.DealAlert
import com.talkswithtanha.twt.core.data.NotificationPrefs
import com.talkswithtanha.twt.core.data.PrefsRepository
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.designsystem.WebFonts
import com.talkswithtanha.twt.core.session.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DealAlertsViewModel @Inject constructor(
    private val session: SessionRepository,
    private val prefs: PrefsRepository
) : ViewModel() {

    val live: StateFlow<NotificationPrefs?> = session.currentUser
        .flatMapLatest { u -> if (u == null) flowOf(null) else prefs.observe(u.id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _note = MutableStateFlow<String?>(null)
    val note: StateFlow<String?> = _note.asStateFlow()

    fun save(next: NotificationPrefs, onDone: () -> Unit) {
        val uid = session.currentUser.value?.id ?: return
        if (_saving.value) return
        _saving.value = true
        _note.value = null
        viewModelScope.launch {
            try {
                prefs.save(uid, next)
                onDone()
            } catch (e: Exception) {
                _note.value = "That did not save. Check your connection and try again."
            } finally {
                _saving.value = false
            }
        }
    }
}

/**
 * The anti-phishing code and which deal alerts arrive where — the same
 * document the website's settings write. Appeals and security mail are
 * always on; the server ignores these for them.
 */
@Composable
fun DealAlertsScreen(onClose: () -> Unit, viewModel: DealAlertsViewModel = hiltViewModel()) {
    val live by viewModel.live.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val note by viewModel.note.collectAsStateWithLifecycle()
    var edit by remember { mutableStateOf<NotificationPrefs?>(null) }
    val prefs = edit ?: live ?: NotificationPrefs()
    val codeBad = prefs.antiPhishingCode.isNotEmpty() && !NotificationPrefs.validCode(prefs.antiPhishingCode)

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(IosColors.Background).statusBarsPadding().imePadding()) {
            SheetHeader("Exchange alerts", onClose)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SettingsSectionHeader("Anti-phishing code")
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF1C1C1E)).padding(horizontal = 14.dp, vertical = 16.dp)) {
                    if (prefs.antiPhishingCode.isEmpty()) Text("A word only you know, e.g. Falcon42", color = Color.White.copy(alpha = 0.3f), fontSize = 16.sp)
                    BasicTextField(
                        value = prefs.antiPhishingCode,
                        onValueChange = { v -> edit = prefs.copy(antiPhishingCode = v.filter(Char::isLetterOrDigit).take(20)) },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontFamily = WebFonts.Mono),
                        cursorBrush = SolidColor(IosColors.Accent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text(
                    if (codeBad) "Use 4 to 20 letters or numbers." else "It appears at the top of every email from TWT. An email without it is not from us.",
                    color = if (codeBad) IosColors.SellRed else IosColors.TextSecondary,
                    fontSize = 12.5.sp
                )

                SettingsSectionHeader("Deal alerts")
                SettingsCard {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Spacer(Modifier.weight(1f))
                        Text("EMAIL", color = IosColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(58.dp))
                        Text("PUSH", color = IosColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(58.dp))
                    }
                    DealAlert.entries.forEachIndexed { i, alert ->
                        if (i > 0) SettingsDivider()
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(alert.label, color = Color.White, fontSize = 15.sp)
                                Text(alert.hint, color = IosColors.TextSecondary, fontSize = 12.sp)
                            }
                            Toggle(prefs.emailOn(alert)) { on -> edit = prefs.copy(email = prefs.email + (alert.key to on)) }
                            Toggle(prefs.pushOn(alert)) { on -> edit = prefs.copy(push = prefs.push + (alert.key to on)) }
                        }
                    }
                }
                Text(
                    "Always on: reviews and appeals on your deals, and security emails such as a password change.",
                    color = IosColors.TextSecondary,
                    fontSize = 12.5.sp
                )
                SettingsCard {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Sounds and the two-minute buzz on deals", color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Switch(prefs.sound, { edit = prefs.copy(sound = it) }, colors = SwitchDefaults.colors(checkedTrackColor = IosColors.BuyGreen))
                    }
                }
                note?.let { Text(it, color = IosColors.SellRed, fontSize = 13.sp) }
            }
            Box(Modifier.padding(16.dp)) {
                SheetPrimaryButton(
                    text = if (saving) "Saving…" else "Save",
                    enabled = edit != null && !codeBad && !saving,
                    onClick = { viewModel.save(prefs) { edit = null } }
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Toggle(on: Boolean, onChange: (Boolean) -> Unit) {
    Box(Modifier.width(58.dp), contentAlignment = Alignment.CenterStart) {
        Switch(on, onChange, colors = SwitchDefaults.colors(checkedTrackColor = IosColors.BuyGreen))
    }
}
