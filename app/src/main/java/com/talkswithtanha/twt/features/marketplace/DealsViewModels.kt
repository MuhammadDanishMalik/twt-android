package com.talkswithtanha.twt.features.marketplace

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.talkswithtanha.twt.core.data.DealRepository
import com.talkswithtanha.twt.core.data.ExchangeRateRepository
import com.talkswithtanha.twt.core.data.MarketplaceConfigRepository
import com.talkswithtanha.twt.core.data.Snapshot
import com.talkswithtanha.twt.core.model.Deal
import com.talkswithtanha.twt.core.model.DealSide
import com.talkswithtanha.twt.core.model.ExchangeRate
import com.talkswithtanha.twt.core.model.PaymentAccount
import com.talkswithtanha.twt.core.navigation.AppRoute
import com.talkswithtanha.twt.core.session.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToLong
import kotlin.random.Random

/** The member's deals, newest first. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DealsViewModel @Inject constructor(
    session: SessionRepository,
    dealRepository: DealRepository
) : ViewModel() {

    val deals: StateFlow<List<Deal>> = session.currentUser
        .flatMapLatest { user ->
            if (user == null) flowOf(Snapshot.Data(emptyList()))
            else dealRepository.observeDeals(user.id)
        }
        .map { (it as? Snapshot.Data)?.value.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

data class NewDealUiState(
    val side: DealSide = DealSide.BUY,
    val amount: String = "",
    /** Index into the configured accounts, or -1 until one is chosen. */
    val accountIndex: Int = 0,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val createdDealId: String? = null
)

@HiltViewModel
class NewDealViewModel @Inject constructor(
    private val session: SessionRepository,
    private val dealRepository: DealRepository,
    exchangeRates: ExchangeRateRepository,
    marketplaceConfig: MarketplaceConfigRepository
) : ViewModel() {

    private val _state = MutableStateFlow(NewDealUiState())
    val state: StateFlow<NewDealUiState> = _state.asStateFlow()

    val rate: StateFlow<ExchangeRate?> = exchangeRates.observeRate()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val accounts: StateFlow<List<PaymentAccount>> = marketplaceConfig.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onSideChange(side: DealSide) = _state.update { it.copy(side = side, error = null) }

    fun onAccountChange(index: Int) = _state.update { it.copy(accountIndex = index, error = null) }

    /** Quick amounts, in whole dollars. */
    fun onQuickAmount(dollars: Int) =
        _state.update { it.copy(amount = dollars.toString(), error = null) }

    fun onAmountChange(value: String) = _state.update {
        it.copy(amount = value.filter { c -> c.isDigit() || c == '.' }, error = null)
    }

    /** Cents, so the amount never rides on a float past this point. */
    val amountCents: Long?
        get() = _state.value.amount.toDoubleOrNull()
            ?.takeIf { it > 0 }
            ?.let { (it * 100).roundToLong() }

    fun open() {
        val user = session.currentUser.value ?: return
        val cents = amountCents ?: return
        val currentRate = rate.value ?: run {
            _state.update {
                it.copy(error = "The rate is not published right now. Message Tanha and he will quote you.")
            }
            return
        }
        if (_state.value.isSubmitting) return

        _state.update { it.copy(isSubmitting = true, error = null) }

        viewModelScope.launch {
            try {
                val id = dealRepository.createDeal(
                    Deal(
                        id = "",
                        userId = user.id,
                        userName = user.fullName,
                        reference = newReference(),
                        side = _state.value.side,
                        amountUsdCents = cents,
                        // Locked here, at the rate on screen. Re-reading it
                        // later would requote somebody mid-transfer.
                        //
                        // Per side, not always the buy rate. A sell locked at
                        // the buy rate pays the member the wrong side of the
                        // spread — on a $100 sell that is 34,100 rupees where
                        // 23,000 was quoted, out of Tanha's pocket.
                        lockedRatePaisa = currentRate.paisaFor(_state.value.side),
                        status = com.talkswithtanha.twt.core.model.DealStatus.AWAITING_PAYMENT,
                        // The account the member actually chose, rather than
                        // whichever happened to be configured first.
                        sellerAccount = accounts.value.getOrNull(_state.value.accountIndex)
                            ?: accounts.value.firstOrNull()
                    )
                )
                _state.update { it.copy(isSubmitting = false, createdDealId = id) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        error = "Could not open the deal. Check your connection and try again."
                    )
                }
            }
        }
    }

    /** `TWT-8K2P`. Short enough to read down a phone. */
    private fun newReference(): String {
        val alphabet = com.talkswithtanha.twt.core.model.AccessCode.ALPHABET
        val body = (1..4).map { alphabet[Random.nextInt(alphabet.length)] }.joinToString("")
        return "TWT-$body"
    }
}

data class DealDetailUiState(
    val deal: Deal? = null,
    val reportingPayment: Boolean = false,
    val error: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DealDetailViewModel @Inject constructor(
    private val dealRepository: DealRepository,
    session: SessionRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val dealId: String = checkNotNull(savedStateHandle[AppRoute.DealDetail.ARG])

    private val _error = MutableStateFlow<String?>(null)
    private val _reporting = MutableStateFlow(false)

    val state: StateFlow<DealDetailUiState> = kotlinx.coroutines.flow.combine(
        session.currentUser.flatMapLatest { user ->
            if (user == null) flowOf(emptyList())
            else dealRepository.observeDeals(user.id).map { (it as? Snapshot.Data)?.value.orEmpty() }
        },
        _reporting,
        _error
    ) { deals, reporting, error ->
        DealDetailUiState(
            deal = deals.firstOrNull { it.id == dealId },
            reportingPayment = reporting,
            error = error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DealDetailUiState())

    /**
     * The member declaring they have paid.
     *
     * The only transition this app can make: the rules refuse anything else
     * from a client, and approving a payment or releasing funds are Tanha's.
     */
    fun reportPayment(reference: String) {
        if (_reporting.value) return
        _reporting.value = true
        viewModelScope.launch {
            try {
                dealRepository.markPaymentSent(dealId, reference.trim())
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Could not report the payment. Try again, or tell Tanha in your chat."
            } finally {
                _reporting.value = false
            }
        }
    }
}

private fun <T> MutableStateFlow<T>.update(block: (T) -> T) {
    value = block(value)
}
