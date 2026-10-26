package com.talkswithtanha.twt.core.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.talkswithtanha.twt.R

/**
 * The real mark for a payment method — JazzCash, Easypaisa, USDT, or a bank —
 * wherever one is named. Matched on the method's name, so "USDT (TRC20)",
 * "Bank Transfer" or a bank's own name all find their icon; anything else gets
 * the bank. The same four files ship in the website and the panel.
 */
@DrawableRes
fun paymentMethodIcon(method: String?): Int {
    val m = method.orEmpty().lowercase()
    return when {
        "jazz" in m -> R.drawable.pay_jazzcash
        "easy" in m -> R.drawable.pay_easypaisa
        "usdt" in m || "tether" in m || "trc" in m || "crypto" in m -> R.drawable.pay_usdt
        else -> R.drawable.pay_bank
    }
}

@Composable
fun PaymentMethodIcon(method: String?, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(paymentMethodIcon(method)),
        contentDescription = method,
        modifier = modifier.size(size)
    )
}
