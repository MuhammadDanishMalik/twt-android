package com.talkswithtanha.twt.features.marketplace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.model.ExchangeRules

/**
 * The desk's rules, in full, before a member's first deal. Agreeing is a
 * tick and a button; "Not now" leaves the deal screen. [readOnly] shows the
 * same rules again later, with just a close button.
 */
@Composable
fun ExchangeRulesSheet(readOnly: Boolean = false, onAgree: () -> Unit = {}, onClose: () -> Unit) {
    var ticked by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(IosColors.Background).statusBarsPadding().navigationBarsPadding()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 18.dp)) {
                Text(if (readOnly) "Deal rules" else "Before your first deal", color = IosColors.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Every deal with the TWT desk follows these rules. They protect you and the desk if a payment goes wrong.",
                    color = IosColors.TextSecondary, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
                )
                ExchangeRules.rules.forEachIndexed { i, (title, body) ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(16.dp)).background(IosColors.SecondaryBackground).padding(14.dp)) {
                        Box(Modifier.size(26.dp).clip(CircleShape).background(IosColors.BuyGreen.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                            Text("${i + 1}", color = IosColors.BuyGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(title, color = IosColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(body, color = IosColors.TextSecondary, fontSize = 13.5.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 3.dp))
                        }
                    }
                }
            }
            Column(Modifier.padding(horizontal = 22.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!readOnly) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { ticked = !ticked }) {
                        Checkbox(ticked, { ticked = it }, colors = CheckboxDefaults.colors(checkedColor = IosColors.BuyGreen))
                        Text("I have read these rules and agree to them", color = IosColors.TextPrimary, fontSize = 14.sp)
                    }
                }
                val enabled = readOnly || ticked
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(if (enabled) IosColors.BuyGreen else Color.White.copy(alpha = 0.08f))
                        .clickable(enabled = enabled) { if (readOnly) onClose() else onAgree() }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (readOnly) "Close" else "Agree and continue", color = if (enabled) Color.Black else IosColors.TextSecondary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                if (!readOnly) {
                    Text("Not now", color = IosColors.TextSecondary, fontSize = 14.sp, modifier = Modifier.align(Alignment.CenterHorizontally).clickable(onClick = onClose).padding(6.dp))
                }
            }
        }
    }
}
