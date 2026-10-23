package com.talkswithtanha.twt.core.model

import java.util.Date

/**
 * Where a deal stands, in plain words — the same model as `dealProgress()` on
 * the website and the Swift copy on iOS, so every app says the same thing at
 * the same moment.
 *
 * The deadlines come from the server (the panel's deal watcher), never from a
 * phone clock: [Deal.payBy] for the member, [Deal.deskBy] for the desk.
 */
enum class Turn { YOU, DESK, REVIEW, DONE, CLOSED }

data class DealProgress(
    /** Index into [STEPS] of the step in progress; 4 when everything is done. */
    val step: Int,
    /** Under review: an amber step after [step]. */
    val appeal: Boolean,
    val turn: Turn,
    val headline: String,
    val next: String,
    val deadline: Date?,
    /** What happens at zero, around the time left. */
    val clockText: (String) -> String,
    /** A few words for the floating pill. */
    val short: String
) {
    companion object {
        val STEPS = listOf("Deal opened", "Payment", "Release", "Completed")
        const val PAY_MINUTES = 30
        const val DESK_MINUTES = 30
    }
}

private fun stepFor(status: DealStatus): Int = when (status) {
    DealStatus.PAYMENT_APPROVED, DealStatus.RELEASING -> 2
    DealStatus.COMPLETED -> 4
    else -> 1
}

fun Deal.progress(): DealProgress {
    val sends = payLabel()
    val gets = receiveLabel()
    val via = sellerAccount?.method?.takeIf { it.isNotBlank() } ?: "account"
    val none: (String) -> String = { it }
    return when (status) {
        DealStatus.AWAITING_PAYMENT -> DealProgress(
            1, false, Turn.YOU,
            "Pay $sends to the desk's $via, then tap I have paid",
            "Once the desk confirms it arrived, you add where to receive $gets.",
            payBy,
            { "Cancels automatically if not paid in $it" },
            "Pay in"
        )
        DealStatus.PAYMENT_SENT -> DealProgress(
            1, false, Turn.DESK,
            "Waiting for the TWT desk to check its account for your $sends",
            "Once confirmed, you add where to receive $gets.",
            deskBy.takeIf { deskByFor == DealStatus.PAYMENT_SENT },
            { "The desk confirms within ${DealProgress.DESK_MINUTES} min · $it left" },
            "Desk checking"
        )
        DealStatus.PAYMENT_APPROVED -> DealProgress(
            2, false, Turn.YOU,
            "Your payment arrived. Add the account to receive $gets",
            "The desk sends it as soon as you do.",
            null, none, "Add your account"
        )
        DealStatus.RELEASING -> DealProgress(
            2, false, Turn.DESK,
            "The TWT desk is sending $gets to your ${receivingAccount?.method ?: "account"}",
            "You get a confirmation here and by email the moment it is sent.",
            deskBy.takeIf { deskByFor == DealStatus.RELEASING },
            { "The desk sends within ${DealProgress.DESK_MINUTES} min · $it left" },
            "Desk sending"
        )
        DealStatus.COMPLETED -> DealProgress(
            4, false, Turn.DONE, "$gets sent. This deal is complete", "Nothing more to do.", null, none, "Completed"
        )
        DealStatus.CANCELLED -> DealProgress(
            0, false, Turn.CLOSED,
            if (cancelReason == "timeout") "Expired, not paid in time, so the deal closed itself"
            else "This deal was cancelled",
            if (cancelReason == "timeout") "Nothing moved. If you already paid, message the desk with your transaction id."
            else "Nothing moved. You can open a new deal at any time.",
            null, none, "Cancelled"
        )
        DealStatus.DISPUTED -> {
            val before = events.lastOrNull { it.status != DealStatus.DISPUTED }?.status ?: DealStatus.AWAITING_PAYMENT
            DealProgress(
                stepFor(before), true, Turn.REVIEW,
                "TWT support is reviewing this deal. Nothing moves until it is decided",
                "You will hear from us here and by email.",
                null, none, "Under review"
            )
        }
    }
}

/** mm:ss, or h:mm:ss past the hour. Never negative. */
fun clockText(millis: Long): String {
    val total = maxOf(0L, (millis + 999) / 1000)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
