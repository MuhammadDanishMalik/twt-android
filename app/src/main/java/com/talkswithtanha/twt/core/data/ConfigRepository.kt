package com.talkswithtanha.twt.core.data

import android.net.Uri
import com.google.firebase.firestore.FirebaseFirestore
import com.talkswithtanha.twt.core.firebase.FirestorePaths.Collection
import com.talkswithtanha.twt.core.firebase.FirestorePaths.Document
import com.talkswithtanha.twt.core.firebase.FirestorePaths.ExchangeRateField as E
import com.talkswithtanha.twt.core.firebase.FirestorePaths.SupportConfigField as S
import com.talkswithtanha.twt.core.model.ExchangeRate
import com.talkswithtanha.twt.core.model.SupportConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

interface ExchangeRateRepository {
    /**
     * Live rate. Fires immediately, then whenever Tanha changes it in the admin
     * panel — so a member watching the card sees the new number appear.
     */
    fun observeRate(): Flow<ExchangeRate?>
}

/**
 * Reads the single `config/exchangeRate` document.
 *
 * This replaces the client-side random walk the iOS app started with, which
 * drifted the rate every four seconds. That was always a placeholder, and it
 * also directly contradicted what the client asked for: he sets the rate
 * himself, and it must not move under a member who is deciding whether to trade
 * at it.
 */
@Singleton
class FirebaseExchangeRateRepository @Inject constructor(
    private val db: FirebaseFirestore
) : ExchangeRateRepository {

    override fun observeRate(): Flow<ExchangeRate?> =
        db.collection(Collection.CONFIG)
            .document(Document.EXCHANGE_RATE)
            .snapshotFlow()
            .map { snapshot ->
                val document = (snapshot as? Snapshot.Data)?.value ?: return@map null
                if (!document.exists()) return@map null

                val buyPaisa = document.get(E.PKR_PER_USD_PAISA).asLongOrNull()
                    ?.takeIf { it > 0 } ?: return@map null

                ExchangeRate(
                    buyPaisa = buyPaisa,
                    // The sell side is derived from the spread Tanha configures.
                    // Until the admin panel writes one, buy and sell are equal
                    // rather than invented — showing a made-up spread would
                    // misquote what he will actually pay.
                    sellPaisa = document.get(E.SELL_PKR_PER_USD_PAISA).asLongOrNull() ?: buyPaisa,
                    previousBuyPaisa = document.get(E.PREVIOUS_PKR_PER_USD_PAISA).asLongOrNull()
                        ?: buyPaisa,
                    updatedAt = document.getTimestamp(E.UPDATED_AT)?.toDate() ?: Date(),
                    history = (document.get(E.HISTORY) as? List<*>)
                        .orEmpty()
                        .mapNotNull { it.asLongOrNull() },
                    minAmountPaisa = document.get(E.MIN_AMOUNT_PAISA).asLongOrNull(),
                    maxAmountPaisa = document.get(E.MAX_AMOUNT_PAISA).asLongOrNull(),
                    isAcceptingDeals = document.getBoolean(E.IS_ACCEPTING_DEALS) ?: true
                )
            }
}

interface SupportConfigRepository {
    fun observeConfig(): Flow<SupportConfig>
}

/**
 * One listener on `config/support`.
 *
 * This exists so the WhatsApp number is not compiled into the binary as the only
 * copy. A support number changes — a SIM is swapped, a second staff member takes
 * the queue — and a number that can only be corrected by shipping an update is a
 * number that stays wrong for however long review takes.
 *
 * The rules open `config` only to signed-in clients, so a signed-out device
 * simply keeps the fallbacks in [SupportConfig] rather than erroring.
 */
@Singleton
class FirebaseSupportConfigRepository @Inject constructor(
    private val db: FirebaseFirestore
) : SupportConfigRepository {

    override fun observeConfig(): Flow<SupportConfig> =
        db.collection(Collection.CONFIG)
            .document(Document.SUPPORT)
            .snapshotFlow()
            .map { snapshot ->
                val document = (snapshot as? Snapshot.Data)?.value
                if (document == null || !document.exists()) return@map SupportConfig()

                SupportConfig(
                    // Same normalisation the admin panel applies, repeated here
                    // because a number hand-edited in the Firebase console never
                    // went through it.
                    whatsAppNumber = document.getString(S.WHATSAPP_NUMBER)
                        ?.filter { it.isDigit() }
                        ?.takeIf { it.isNotEmpty() }
                        ?: SupportConfig.DEFAULT_WHATSAPP,
                    supportEmail = document.getString(S.SUPPORT_EMAIL)
                        ?.takeIf { it.contains('@') }
                        ?: SupportConfig.DEFAULT_EMAIL,
                    accessPageUrl = httpsOrNull(document.getString(S.ACCESS_PAGE_URL))
                        ?: SupportConfig.DEFAULT_ACCESS_PAGE,
                    termsUrl = httpsOrNull(document.getString(S.TERMS_URL))
                        ?: SupportConfig.DEFAULT_TERMS,
                    privacyUrl = httpsOrNull(document.getString(S.PRIVACY_URL))
                        ?: SupportConfig.DEFAULT_PRIVACY
                )
            }

    /**
     * `https` only.
     *
     * Everything here is handed to an ACTION_VIEW intent on a member's tap. A
     * remote document that can put any scheme in front of that is a remote
     * document that can fire off a `tel:` dialler, or a third-party app's deep
     * link, from a button labelled "Terms of use".
     */
    private fun httpsOrNull(value: String?): String? {
        val text = value?.takeIf { it.isNotBlank() } ?: return null
        return text.takeIf { Uri.parse(it).scheme?.lowercase() == "https" }
    }
}
