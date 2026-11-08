package com.talkswithtanha.twt.features.admin

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.designsystem.components.PaymentMethodIcon
import com.talkswithtanha.twt.core.model.Deal
import com.talkswithtanha.twt.core.model.PaymentAccount
import com.talkswithtanha.twt.core.model.PaymentMethods

/**
 * Accepting a deal: the desk says where this member pays, every time.
 *
 * The dealer does not keep one fixed account — which one he can take money
 * on changes deal to deal — so accepting asks for it: one of his saved
 * accounts in a tap, or a new one, its bank or wallet found by search. What
 * he picks is what the member is shown to pay into.
 */
@Composable
internal fun AcceptDealSheet(
    deal: Deal,
    saved: List<PaymentAccount>,
    /** Already accepted: pointing the member at a different account. */
    changing: Boolean = false,
    onDismiss: () -> Unit,
    onAccept: (account: PaymentAccount, saveForNextTime: Boolean) -> Unit
) {
    val currency = deal.side.payCurrency
    val catalog = PaymentMethods.forCurrency(currency)
    val usable = saved.filter { acc ->
        val m = PaymentMethods.find(acc.method)
        // Unknown names (typed in the panel) are offered for either side.
        m == null || m in catalog
    }
    var picked by remember {
        mutableStateOf(usable.firstOrNull { it == deal.sellerAccount } ?: usable.firstOrNull())
    }
    var addingNew by remember { mutableStateOf(usable.isEmpty()) }
    var query by remember { mutableStateOf("") }
    var method by remember { mutableStateOf<PaymentMethods.Method?>(null) }
    var title by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    var save by remember { mutableStateOf(true) }

    val newAccount = method?.let { m ->
        PaymentAccount(m.name, title.trim(), number.trim()).takeIf { title.isNotBlank() && number.trim().length >= 4 }
    }
    val chosen = if (addingNew) newAccount else picked

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(
            Modifier.fillMaxSize().background(IosColors.Background).statusBarsPadding().navigationBarsPadding().imePadding()
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (changing) "Change account · ${deal.reference}" else "Accept ${deal.reference}", color = IosColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${deal.userName.ifBlank { "The member" }} pays ${deal.payLabel()}. Where should it go?",
                        color = IosColors.TextSecondary, fontSize = 13.5.sp
                    )
                }
                Text("Close", color = IosColors.TextSecondary, fontSize = 15.sp, modifier = Modifier.clickable(onClick = onDismiss).padding(8.dp))
            }

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                deal.sellerAccount?.method?.takeIf { it.isNotBlank() }?.let { m ->
                    Text(
                        "${deal.userName.ifBlank { "The member" }} picked $m. Same method on both ends settles fastest.",
                        color = IosColors.BuyGreen, fontSize = 13.sp
                    )
                }
                if (usable.isNotEmpty()) {
                    Text("Your accounts · saved and recently used", color = IosColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    usable.forEach { acc ->
                        val on = !addingNew && picked == acc
                        AccountRow(acc, on) { picked = acc; addingNew = false }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .border(1.dp, if (addingNew) IosColors.BuyGreen else Color.White.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
                        .clickable { addingNew = true }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("+", color = IosColors.BuyGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(12.dp))
                    Text("Use another account", color = IosColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }

                if (addingNew) {
                    if (method == null) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text(if (currency == "PKR") "Search bank or wallet" else "Search USDT network or wallet") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        val results = catalog.filter { it.matches(query) }
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            items(results, key = { it.name }) { m ->
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { method = m }.padding(horizontal = 8.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    PaymentMethodIcon(m.name, size = 26.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Text(m.name, color = IosColors.TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                                    Text(
                                        when (m.kind) { PaymentMethods.Kind.WALLET -> "Wallet"; PaymentMethods.Kind.BANK -> "Bank"; PaymentMethods.Kind.CRYPTO -> "Crypto" },
                                        color = IosColors.TextSecondary, fontSize = 12.sp
                                    )
                                }
                            }
                            if (results.isEmpty()) item {
                                Text("Nothing called \"$query\".", color = IosColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(8.dp))
                            }
                        }
                    } else {
                        val m = method!!
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(IosColors.SecondaryBackground).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PaymentMethodIcon(m.name, size = 26.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(m.name, color = IosColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text("Change", color = IosColors.BuyGreen, fontSize = 13.sp, modifier = Modifier.clickable { method = null })
                        }
                        OutlinedTextField(title, { title = it }, label = { Text(if (m.kind == PaymentMethods.Kind.CRYPTO) "Name on the account" else "Account title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(
                            number, { number = it },
                            label = { Text(m.numberLabel) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = if (m.kind == PaymentMethods.Kind.WALLET) KeyboardType.Phone else KeyboardType.Text),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { save = !save }) {
                            Checkbox(save, { save = it }, colors = CheckboxDefaults.colors(checkedColor = IosColors.BuyGreen))
                            Text("Save to my accounts for next time", color = IosColors.TextPrimary, fontSize = 14.sp)
                        }
                    }
                }
                Spacer(Modifier.size(8.dp))
            }

            Column(Modifier.padding(20.dp)) {
                chosen?.let {
                    Text(
                        "The member will be shown: ${it.method} · ${it.accountTitle} · ${it.accountNumber}",
                        color = IosColors.TextSecondary, fontSize = 12.5.sp, modifier = Modifier.padding(bottom = 10.dp)
                    )
                }
                PrimaryAction(if (changing) "Share this account instead" else "Accept and share this account", enabled = chosen != null) {
                    chosen?.let { onAccept(it, addingNew && save) }
                }
            }
        }
    }
}

@Composable
private fun AccountRow(acc: PaymentAccount, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(IosColors.SecondaryBackground)
            .border(1.5.dp, if (on) IosColors.BuyGreen else Color.Transparent, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PaymentMethodIcon(acc.method, size = 28.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(acc.method, color = IosColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("${acc.accountTitle} · ${acc.accountNumber}", color = IosColors.TextSecondary, fontSize = 12.5.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
        }
        Box(
            Modifier.size(22.dp).clip(CircleShape)
                .border(2.dp, if (on) IosColors.BuyGreen else Color.White.copy(alpha = 0.3f), CircleShape)
                .background(if (on) IosColors.BuyGreen else Color.Transparent)
        )
    }
}
