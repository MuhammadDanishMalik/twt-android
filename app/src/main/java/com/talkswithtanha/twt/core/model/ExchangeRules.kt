package com.talkswithtanha.twt.core.model

/**
 * The desk's terms, shown to a member before their first deal and agreed to
 * once. Word for word what the desk sends members; the website shows the same
 * five (twt-web `lib/exchange-rules.ts`).
 */
object ExchangeRules {
    val rules: List<Pair<String, String>> = listOf(
        "Pay from your own account" to
            "Always send payment from your own account. Third-party payments are not accepted. Before sending, ask the desk for the account, and pay only into the account the desk gives you — never without confirming it first.",
        "Use the same payment method" to
            "Try to use the same method on both ends — Easypaisa to Easypaisa, JazzCash to JazzCash, HBL to HBL, UBL to UBL and so on. If a payment has a problem, one payment system solves it far faster than two banks.",
        "ID when asked" to
            "If we ask for your CNIC, or your CNIC with a selfie, as the sender you must provide it.",
        "Disputed payments" to
            "If a payment is disputed, directly or as part of a chain, the sender is responsible for that payment and will pay it in full.",
        "Help resolve disputes" to
            "If a payment is disputed, the sender will cooperate fully in opening the dispute, do all the paperwork, and visit the bank as often as needed."
    )
}
