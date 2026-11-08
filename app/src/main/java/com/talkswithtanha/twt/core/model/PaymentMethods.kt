package com.talkswithtanha.twt.core.model

/**
 * Where money can be sent in a deal, for the desk to pick from when it says
 * where a member should pay: Pakistani wallets and banks for rupees, USDT
 * networks and dollar wallets for dollars. Searched by name or short name.
 */
object PaymentMethods {

    enum class Kind { WALLET, BANK, CRYPTO }

    data class Method(val name: String, val kind: Kind, val aliases: List<String> = emptyList()) {
        fun matches(query: String): Boolean {
            val q = query.trim().lowercase()
            if (q.isEmpty()) return true
            return name.lowercase().contains(q) || aliases.any { it.lowercase().contains(q) }
        }

        /** What the number field is called for this method. */
        val numberLabel: String
            get() = when {
                kind == Kind.CRYPTO && name.startsWith("USDT") -> "Wallet address"
                kind == Kind.CRYPTO -> "Pay ID or email"
                kind == Kind.WALLET -> "Mobile number"
                else -> "Account number or IBAN"
            }
    }

    private val wallets = listOf(
        Method("Easypaisa", Kind.WALLET, listOf("ep", "telenor")),
        Method("JazzCash", Kind.WALLET, listOf("jazz", "jc", "mobilink")),
        Method("SadaPay", Kind.WALLET, listOf("sada")),
        Method("NayaPay", Kind.WALLET, listOf("naya")),
        Method("UPaisa", Kind.WALLET, listOf("ufone", "u paisa")),
        Method("Raast ID", Kind.WALLET, listOf("raast")),
    )

    private val banks = listOf(
        Method("HBL", Kind.BANK, listOf("habib bank limited")),
        Method("UBL", Kind.BANK, listOf("united bank")),
        Method("MCB Bank", Kind.BANK, listOf("mcb", "muslim commercial")),
        Method("Allied Bank", Kind.BANK, listOf("abl")),
        Method("Meezan Bank", Kind.BANK, listOf("meezan")),
        Method("Bank Alfalah", Kind.BANK, listOf("alfalah", "bafl")),
        Method("Bank Al Habib", Kind.BANK, listOf("al habib", "bahl")),
        Method("Faysal Bank", Kind.BANK, listOf("faysal", "fbl")),
        Method("Askari Bank", Kind.BANK, listOf("askari")),
        Method("National Bank of Pakistan", Kind.BANK, listOf("nbp", "national bank")),
        Method("Standard Chartered", Kind.BANK, listOf("scb", "standard")),
        Method("JS Bank", Kind.BANK, listOf("js")),
        Method("Habib Metropolitan Bank", Kind.BANK, listOf("habib metro", "hmb")),
        Method("Soneri Bank", Kind.BANK, listOf("soneri")),
        Method("Bank of Punjab", Kind.BANK, listOf("bop", "punjab")),
        Method("Dubai Islamic Bank", Kind.BANK, listOf("dib")),
        Method("BankIslami", Kind.BANK, listOf("bank islami")),
        Method("MCB Islamic Bank", Kind.BANK, listOf("mcb islamic")),
        Method("Al Baraka Bank", Kind.BANK, listOf("albaraka")),
        Method("Silkbank", Kind.BANK, listOf("silk")),
        Method("Summit Bank", Kind.BANK, listOf("summit")),
        Method("Bank of Khyber", Kind.BANK, listOf("bok", "khyber")),
        Method("Sindh Bank", Kind.BANK, listOf("sindh")),
        Method("First Women Bank", Kind.BANK, listOf("fwbl")),
        Method("Mobilink Microfinance Bank", Kind.BANK, listOf("mmbl")),
        Method("Telenor Microfinance Bank", Kind.BANK, listOf("tmb")),
        Method("Khushhali Microfinance Bank", Kind.BANK, listOf("khushhali")),
        Method("U Microfinance Bank", Kind.BANK, listOf("u bank")),
    )

    private val crypto = listOf(
        Method("USDT (TRC20)", Kind.CRYPTO, listOf("tron", "trc", "usdt")),
        Method("USDT (BEP20)", Kind.CRYPTO, listOf("bsc", "bep", "binance smart chain", "usdt")),
        Method("USDT (ERC20)", Kind.CRYPTO, listOf("ethereum", "erc", "usdt")),
        Method("USDT (Solana)", Kind.CRYPTO, listOf("sol", "usdt")),
        Method("USDT (Polygon)", Kind.CRYPTO, listOf("matic", "usdt")),
        Method("USDT (TON)", Kind.CRYPTO, listOf("ton", "telegram", "usdt")),
        Method("Binance Pay", Kind.CRYPTO, listOf("binance", "pay id")),
        Method("Bybit Pay", Kind.CRYPTO, listOf("bybit")),
        Method("OKX Pay", Kind.CRYPTO, listOf("okx")),
        Method("Payoneer", Kind.CRYPTO, listOf("payoneer")),
        Method("Wise", Kind.CRYPTO, listOf("transferwise")),
        Method("Skrill", Kind.CRYPTO, listOf("skrill")),
    )

    /** For a member paying rupees: wallets first, then banks. */
    val rupees: List<Method> = wallets + banks

    /** For a member paying dollars: USDT networks and dollar wallets. */
    val dollars: List<Method> = crypto

    fun forCurrency(currency: String): List<Method> = if (currency == "PKR") rupees else dollars

    fun find(name: String): Method? = (rupees + dollars).firstOrNull { it.name.equals(name, ignoreCase = true) }
}
