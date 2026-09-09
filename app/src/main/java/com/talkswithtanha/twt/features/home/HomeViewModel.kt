package com.talkswithtanha.twt.features.home

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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HomeUiState(
    val latestSignals: List<Signal> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    session: SessionRepository,
    signalRepository: SignalRepository,
    exchangeRates: ExchangeRateRepository,
    tracker: FollowedSignalTracker
) : ViewModel() {

    val user: StateFlow<User?> = session.currentUser

    val stats: StateFlow<TradingStats> = tracker.stats

    val openFollows = tracker.openFollows

    val state: StateFlow<HomeUiState> = signalRepository.observeSignals(limit = 5)
        .map { snapshot ->
            when (snapshot) {
                is Snapshot.Data -> HomeUiState(snapshot.value, isLoading = false)
                is Snapshot.Failed -> HomeUiState(isLoading = false)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    val rate: StateFlow<ExchangeRate?> = exchangeRates.observeRate()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
