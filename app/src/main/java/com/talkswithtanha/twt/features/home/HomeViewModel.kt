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
    private val tracker: FollowedSignalTracker
) : ViewModel() {

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
