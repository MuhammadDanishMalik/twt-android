package com.talkswithtanha.twt.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.talkswithtanha.twt.core.model.Deal
import com.talkswithtanha.twt.core.model.DealSide
import com.talkswithtanha.twt.core.model.DealStatus
import com.talkswithtanha.twt.core.model.PaymentAccount
import com.talkswithtanha.twt.core.notifications.DealTracker
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.Date

/**
 * Debug builds only: draws a sample deal's card without signing in.
 *
 *   adb shell am broadcast -n com.orixto.twt/com.talkswithtanha.twt.debug.DealCardPreview --es status paymentSent
 */
class DealCardPreview : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun dealTracker(): DealTracker
    }

    override fun onReceive(context: Context, intent: Intent) {
        val status = DealStatus.entries.firstOrNull { it.stored == intent.getStringExtra("status") } ?: DealStatus.PAYMENT_SENT
        val deal = Deal(
            id = "preview",
            userId = "preview",
            userName = "Preview",
            reference = "TWT-DEMO",
            side = DealSide.BUY,
            amountUsdCents = 10_000,
            lockedRatePaisa = 28_250,
            status = status,
            sellerAccount = PaymentAccount("JazzCash", "Trade with Tanha", "03001234567"),
            payBy = Date(System.currentTimeMillis() + 15 * 60_000),
            deskBy = Date(System.currentTimeMillis() + 10 * 60_000),
            deskByFor = status
        )
        EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java).dealTracker().show(deal)
    }
}
