package com.talkswithtanha.twt.features.marketplace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.talkswithtanha.twt.core.data.DealRepository
import com.talkswithtanha.twt.core.data.ExchangeRateRepository
import com.talkswithtanha.twt.core.data.MarketplaceConfigRepository
import com.talkswithtanha.twt.core.data.SupportConfigRepository
import com.talkswithtanha.twt.core.data.Snapshot
import com.talkswithtanha.twt.core.firebase.FirestorePaths
import com.talkswithtanha.twt.core.model.Deal
import com.talkswithtanha.twt.core.model.ExchangeRate
import com.talkswithtanha.twt.core.model.MarketplaceSeller
import com.talkswithtanha.twt.core.model.SupportConfig
import com.talkswithtanha.twt.core.session.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MarketplaceViewModel @Inject constructor(
    exchangeRates: ExchangeRateRepository,
    supportConfig: SupportConfigRepository,
    marketplaceConfig: MarketplaceConfigRepository,
    deals: DealRepository,
    session: SessionRepository
) : ViewModel() {

    val rate: StateFlow<ExchangeRate?> = exchangeRates.observeRate()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val support: StateFlow<SupportConfig> = supportConfig.observeConfig()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SupportConfig())

    val seller: StateFlow<MarketplaceSeller> = marketplaceConfig.observeSeller()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MarketplaceSeller())

    /**
     * The member's private thread with the seller.
     *
     * The same `support_{uid}` room the Chat tab opens — there is one
     * conversation with Tanha, and reaching it from the rate screen must not
     * start a second one.
     */
    val sellerRoomId: StateFlow<String?> = session.currentUser
        .map { user -> user?.id?.let(FirestorePaths.ChatRoom::support) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * The one deal still in flight, if there is one.
     *
     * Newest first from the query, so the first active deal is the one the
     * member is actually living through. Showing it on this screen is what
     * stops somebody opening a second deal because they could not find the
     * first.
     */
    val activeDeal: StateFlow<Deal?> = session.currentUser
        .flatMapLatest { user ->
            val uid = user?.id ?: return@flatMapLatest flowOf(null)
            deals.observeDeals(uid).map { snapshot ->
                (snapshot as? Snapshot.Data)?.value?.firstOrNull { it.status.isActive }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
