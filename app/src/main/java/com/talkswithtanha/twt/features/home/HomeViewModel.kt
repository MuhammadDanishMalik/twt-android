package com.talkswithtanha.twt.features.home

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.talkswithtanha.twt.core.data.ExchangeRateRepository
import com.talkswithtanha.twt.core.data.SignalRepository
import com.talkswithtanha.twt.core.data.Snapshot
import com.talkswithtanha.twt.core.model.ExchangeRate
import com.talkswithtanha.twt.core.model.Signal
import com.talkswithtanha.twt.core.model.TradingStats
import com.talkswithtanha.twt.core.model.User
import com.talkswithtanha.twt.core.session.SessionRepository
import com.talkswithtanha.twt.core.signals.FollowedSignalTracker
import com.talkswithtanha.twt.core.storage.appPreferences
import com.talkswithtanha.twt.features.home.components.HomeShortcutsLayout
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val signals: List<Signal> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    session: SessionRepository,
    signalRepository: SignalRepository,
    exchangeRates: ExchangeRateRepository,
    private val tracker: FollowedSignalTracker,
    private val banners: com.talkswithtanha.twt.core.data.BannerRepository,
    supportConfig: com.talkswithtanha.twt.core.data.SupportConfigRepository,
    adminRepository: com.talkswithtanha.twt.core.data.AdminRepository,
    desk: com.talkswithtanha.twt.core.data.DeskRepository
) : ViewModel() {

    /**
     * The exchange desk's live counts, for staff who hold the marketplace —
     * the dealer above all. Null for everybody else, who never sees the card.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val deskSummary: StateFlow<DeskSummary?> = session.currentUser
        .flatMapLatest { u ->
            if (u == null || !u.can(com.talkswithtanha.twt.core.model.StaffArea.MARKETPLACE) || !com.talkswithtanha.twt.BuildConfig.MARKETPLACE_ENABLED) {
                kotlinx.coroutines.flow.flowOf(null)
            } else {
                kotlinx.coroutines.flow.combine(adminRepository.observeAllDeals(), desk.observeThreads()) { deals, threads ->
                    val list = (deals as? com.talkswithtanha.twt.core.data.Snapshot.Data)?.value.orEmpty()
                    DeskSummary(
                        requests = list.count { it.status == com.talkswithtanha.twt.core.model.DealStatus.AWAITING_APPROVAL },
                        needsYou = list.count {
                            it.status == com.talkswithtanha.twt.core.model.DealStatus.PAYMENT_SENT ||
                                it.status == com.talkswithtanha.twt.core.model.DealStatus.RELEASING
                        },
                        unreadChats = (threads as? com.talkswithtanha.twt.core.data.Snapshot.Data)?.value.orEmpty().count { it.unseen },
                        deskOnly = u.role == com.talkswithtanha.twt.core.model.UserRole.ADMIN &&
                            u.staffAccess == listOf(com.talkswithtanha.twt.core.model.StaffArea.MARKETPLACE)
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The WhatsApp number and the rest, as the panel sets them, for "tap to renew". */
    val support: StateFlow<com.talkswithtanha.twt.core.model.SupportConfig> = supportConfig.observeConfig()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.talkswithtanha.twt.core.model.SupportConfig())

    /** Active promotional banners, in the order the admin panel set. */
    val activeBanners: StateFlow<List<com.talkswithtanha.twt.core.data.Banner>> = banners.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun bannerTapped(id: String) = viewModelScope.launch { banners.recordTap(id) }

    val user: StateFlow<User?> = session.currentUser

    val stats: StateFlow<TradingStats> = tracker.stats

    /**
     * Ongoing trades lead — a closed signal is history, an open one is a
     * decision.
     */
    val state: StateFlow<HomeUiState> = signalRepository.observeSignals()
        .map { snapshot ->
            when (snapshot) {
                is Snapshot.Data -> HomeUiState(
                    signals = snapshot.value.sortedByDescending { it.status.isOngoing },
                    isLoading = false
                )
                is Snapshot.Failed -> HomeUiState(isLoading = false)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    val followedSignalIds: StateFlow<Set<String>> = tracker.follows
        .map { follows -> follows.filter { it.isActive }.map { it.signalId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val rate: StateFlow<ExchangeRate?> = exchangeRates.observeRate()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The member's choice of home layout, from Settings → Appearance. */
    val shortcutsLayout: StateFlow<HomeShortcutsLayout> = context.appPreferences.data
        .map { HomeShortcutsLayout.from(it[LAYOUT_KEY]) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeShortcutsLayout.LIST)

    fun follow(signal: Signal) = viewModelScope.launch { tracker.follow(signal) }

    fun unfollow(signalId: String) = viewModelScope.launch { tracker.unfollow(signalId) }

    companion object {
        val LAYOUT_KEY = stringPreferencesKey(HomeShortcutsLayout.STORAGE_KEY)

        suspend fun setLayout(context: Context, layout: HomeShortcutsLayout) {
            context.appPreferences.edit { it[LAYOUT_KEY] = layout.name }
        }
    }
}

/** What the desk card on Home says: how much is waiting on the dealer. */
data class DeskSummary(val requests: Int, val needsYou: Int, val unreadChats: Int, val deskOnly: Boolean)
