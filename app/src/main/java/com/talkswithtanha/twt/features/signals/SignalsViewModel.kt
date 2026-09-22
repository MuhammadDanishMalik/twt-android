package com.talkswithtanha.twt.features.signals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.talkswithtanha.twt.core.data.SignalRepository
import com.talkswithtanha.twt.core.data.Snapshot
import com.talkswithtanha.twt.core.model.FollowOutcome
import com.talkswithtanha.twt.core.model.Signal
import com.talkswithtanha.twt.core.model.SignalFollow
import com.talkswithtanha.twt.core.model.TradingStats
import com.talkswithtanha.twt.core.signals.FollowedSignalTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SignalsUiState(
    val signals: List<Signal> = emptyList(),
    val followedSignalIds: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class SignalsViewModel @Inject constructor(
    signalRepository: SignalRepository,
    private val tracker: FollowedSignalTracker
) : ViewModel() {

    val state: StateFlow<SignalsUiState> = combine(
        signalRepository.observeSignals(),
        tracker.follows
    ) { snapshot, follows ->
        when (snapshot) {
            is Snapshot.Data -> SignalsUiState(
                signals = snapshot.value,
                followedSignalIds = follows.filter { it.isActive }.map { it.signalId }.toSet(),
                isLoading = false
            )
            is Snapshot.Failed -> SignalsUiState(
                isLoading = false,
                // Named rather than swallowed. The overwhelmingly likely cause
                // is the composite index for `isPublished` + `timestamp` not
                // being deployed, and that only starts failing once real signals
                // exist -- so it passes every test until launch day.
                error = "Signals could not be loaded. " +
                    (snapshot.error.localizedMessage ?: "Check your connection.")
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SignalsUiState())

    val stats: StateFlow<TradingStats> = tracker.stats

    fun follow(signal: Signal) = viewModelScope.launch { tracker.follow(signal) }

    fun unfollow(signalId: String) = viewModelScope.launch { tracker.unfollow(signalId) }
}

data class SignalDetailUiState(
    val signal: Signal? = null,
    val follow: SignalFollow? = null,
    val isLoading: Boolean = true,
    val error: String? = null
) {
    val isFollowing: Boolean get() = follow?.isActive == true

    /**
     * Whether to offer the "record what you made" sheet.
     *
     * Offered once the trade is over, or once the member has dropped it —
     * either way there is a result to write down, and until then there is not.
     */
    val canRecordOutcome: Boolean
        get() = follow != null &&
            follow.outcome == FollowOutcome.OPEN &&
            (signal?.status?.isOngoing == false || !follow.isActive)
}

@HiltViewModel
class SignalDetailViewModel @Inject constructor(
    private val signalRepository: SignalRepository,
    private val tracker: FollowedSignalTracker,
    savedStateHandle: androidx.lifecycle.SavedStateHandle
) : ViewModel() {

    private val signalId: String =
        checkNotNull(savedStateHandle[com.talkswithtanha.twt.core.navigation.AppRoute.SignalDetail.ARG])

    val state: StateFlow<SignalDetailUiState> = combine(
        signalRepository.observeSignal(signalId),
        tracker.follows.map { follows -> follows.firstOrNull { it.signalId == signalId } }
    ) { snapshot, follow ->
        when (snapshot) {
            is Snapshot.Data -> SignalDetailUiState(
                signal = snapshot.value,
                follow = follow,
                isLoading = false,
                error = if (snapshot.value == null) "This signal is no longer available." else null
            )
            is Snapshot.Failed -> SignalDetailUiState(
                follow = follow,
                isLoading = false,
                error = snapshot.error.localizedMessage ?: "This signal could not be loaded."
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SignalDetailUiState())

    fun toggleFollow() {
        val current = state.value
        val signal = current.signal ?: return
        viewModelScope.launch {
            if (current.isFollowing) tracker.unfollow(signal.id)
            else tracker.follow(signal)
        }
    }

    fun recordOutcome(
        outcome: FollowOutcome,
        pips: Double?,
        amountMinor: Long?,
        currency: String?,
        note: String?
    ) = viewModelScope.launch {
        runCatching {
            tracker.recordOutcome(
                signalId = signalId,
                outcome = outcome,
                pips = pips,
                amountMinor = amountMinor,
                currency = currency,
                note = note
            )
        }
    }
}


/**
 * The member's journal. Reads the same tracker the alerting does, so the list
 * and the notifications can never disagree about what is being followed.
 */
@HiltViewModel
class MySignalsViewModel @Inject constructor(
    private val tracker: FollowedSignalTracker
) : ViewModel() {
    val follows: StateFlow<List<SignalFollow>> = tracker.follows
    val stats: StateFlow<TradingStats> = tracker.stats
    val isLoading: StateFlow<Boolean> = tracker.isLoading

    fun record(
        signalId: String,
        outcome: FollowOutcome,
        pips: Double?,
        amountMinor: Long?,
        currency: String?,
        note: String?
    ) = viewModelScope.launch {
        runCatching {
            tracker.recordOutcome(signalId, outcome, pips, amountMinor, currency, note)
        }
    }
}
