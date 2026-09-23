package com.talkswithtanha.twt.core.model

import java.util.Date

/**
 * One currency exchange between a member and Tanha.
 *
 * Money here is an integer in minor units, like everywhere else: [amountUsdCents]
 * and [lockedRatePaisa]. The rate is *locked* at the moment the deal opens —
 * that is the whole point of opening one, and recomputing it later from a live
 * rate would quietly requote somebody mid-transfer.
 */
data class Deal(
    val id: String,
    val userId: String,
    val userName: String,
    /** Short human reference, e.g. `TWT-4H2K`. What support asks for. */
    val reference: String,
    val side: DealSide,
    val amountUsdCents: Long,
    val lockedRatePaisa: Long,
    val status: DealStatus,
    /** Where the member pays. Null until Tanha's accounts are configured. */
    val sellerAccount: PaymentAccount? = null,
    /** Where the member wants their funds sent. Staff-set. */
    val receivingAccount: PaymentAccount? = null,
    val paymentReference: String? = null,
    val events: List<DealEvent> = emptyList(),
    val createdAt: Date = Date()
) {
    /** What the member pays, in the currency they pay in. */
    val payTotalMinor: Long
        get() = when (side) {
            DealSide.BUY -> amountUsdCents * lockedRatePaisa / 100
            DealSide.SELL -> amountUsdCents
        }

    val receiveTotalMinor: Long
        get() = when (side) {
            DealSide.BUY -> amountUsdCents
            DealSide.SELL -> amountUsdCents * lockedRatePaisa / 100
        }
}

enum class DealSide(val stored: String, val title: String) {
    BUY("buy", "Buy USD"),
    SELL("sell", "Sell USD");

    val payCurrency: String get() = if (this == BUY) "PKR" else "USD"
    val receiveCurrency: String get() = if (this == BUY) "USD" else "PKR"

    companion object {
        fun from(value: String?): DealSide =
            entries.firstOrNull { it.stored == value } ?: BUY
    }
}

/**
 * Where a deal has got to.
 *
 * **A member may only ever move a deal from [AWAITING_PAYMENT] to
 * [PAYMENT_SENT].** Everything past that is Tanha approving a transfer and
 * releasing funds, and the security rules enforce it — this is the single most
 * important rule in the file, because without it somebody marks their own deal
 * complete and is sent money for nothing.
 */
enum class DealStatus(val stored: String, val title: String) {
    AWAITING_PAYMENT("awaitingPayment", "Send payment"),
    PAYMENT_SENT("paymentSent", "Awaiting approval"),
    PAYMENT_APPROVED("paymentApproved", "Payment approved"),
    RELEASING("releasing", "Releasing funds"),
    COMPLETED("completed", "Completed"),
    CANCELLED("cancelled", "Cancelled"),
    DISPUTED("disputed", "Under review");

    /** Position on the happy path, or null for the two ways out of it. */
    val stepIndex: Int?
        get() = when (this) {
            AWAITING_PAYMENT -> 0
            PAYMENT_SENT -> 1
            PAYMENT_APPROVED -> 2
            RELEASING -> 3
            COMPLETED -> 4
            CANCELLED, DISPUTED -> null
        }

    val isTerminal: Boolean get() = this == COMPLETED || this == CANCELLED
    val isActive: Boolean get() = !isTerminal && this != DISPUTED

    /** The only transition this app is allowed to make. */
    val memberCanReportPayment: Boolean get() = this == AWAITING_PAYMENT

    fun detail(side: DealSide): String = when (this) {
        AWAITING_PAYMENT -> "Send the exact amount to the account below, then confirm."
        PAYMENT_SENT -> "Tanha is verifying your transfer. This usually takes a few minutes."
        PAYMENT_APPROVED -> "Payment confirmed. Tanha will ask where to send your ${side.receiveCurrency}."
        RELEASING -> "Tanha is sending your ${side.receiveCurrency} now."
        COMPLETED -> "Funds delivered. This deal is closed."
        CANCELLED -> "This deal was cancelled. No funds were moved."
        DISPUTED -> "This deal is under review. Tanha will be in touch."
    }

    companion object {
        val happyPath = listOf("Payment", "Approval", "Account", "Release", "Done")

        fun from(value: String?): DealStatus =
            entries.firstOrNull { it.stored == value } ?: AWAITING_PAYMENT
    }
}

/** An account someone pays into. */
data class PaymentAccount(
    val method: String,
    val accountTitle: String,
    val accountNumber: String
) {
    /** All but the last four, for anywhere the full number is not needed. */
    val masked: String
        get() = if (accountNumber.length <= 4) accountNumber
        else "•".repeat(accountNumber.length - 4) + accountNumber.takeLast(4)
}

/** One line in a deal's history. Appended, never edited. */
data class DealEvent(
    val id: String,
    val status: DealStatus,
    val note: String,
    val timestamp: Date
)
