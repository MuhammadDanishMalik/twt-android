package com.talkswithtanha.twt.features.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.talkswithtanha.twt.core.data.AdminBanner
import com.talkswithtanha.twt.core.data.AdminRepository
import com.talkswithtanha.twt.core.data.SignalDraft
import com.talkswithtanha.twt.core.data.SignalRepository
import com.talkswithtanha.twt.core.data.Snapshot
import com.talkswithtanha.twt.core.data.SupportTicketRepository
import com.talkswithtanha.twt.core.model.Deal
import com.talkswithtanha.twt.core.model.DealStatus
import com.talkswithtanha.twt.core.model.Signal
import com.talkswithtanha.twt.core.model.SignalStatus
import com.talkswithtanha.twt.core.model.StaffArea
import com.talkswithtanha.twt.core.model.User
import com.talkswithtanha.twt.core.session.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Running TWT from a phone.
 *
 * Not a port of the admin panel, and deliberately so. The panel is where the
 * books are kept — payments, access codes, users, the audit log. This is the
 * half that cannot wait for a laptop: a trade needs calling now, a target just
 * hit, somebody is waiting on an answer, a banner is advertising something that
 * finished yesterday.
 *
 * Each screen is gated on the area the account holds, and that gating is
 * cosmetic: `can()` in firestore.rules is what actually refuses a dealer who
 * tries to post a signal.
 */
data class AdminHomeUiState(
    val user: User? = null,
    val waitingTickets: Int = 0,
    val liveSignals: Int = 0
) {
    fun can(area: StaffArea): Boolean = user?.can(area) == true

    /**
     * What is *not* here, and why. A team member with one area should be told
     * the rest of the panel exists and is simply not theirs, rather than left
     * wondering whether the app is broken.
     */
    val footnote: String
        get() {
            val areas = user?.staffAccess.orEmpty()
            return if (user?.role?.stored == "owner" || areas.isEmpty()) {
                "Access codes, payments and member accounts stay in the admin panel on the web — they are worth sitting down for."
            } else {
                "You have ${areas.joinToString(", ") { it.title }}. Anything else is set by Tanha from the admin panel."
            }
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AdminHomeViewModel @Inject constructor(
    session: SessionRepository,
    signalRepository: SignalRepository,
    ticketRepository: SupportTicketRepository
) : ViewModel() {

    private val queue = session.currentUser
        .flatMapLatest { user ->
            if (user?.can(StaffArea.TICKETS) == true) ticketRepository.observeQueue() else flowOf(null)
        }
        .map { snapshot ->
            (snapshot as? Snapshot.Data)?.value.orEmpty()
                .count { it.needsReply && !it.status.isFinished }
        }

    val state: StateFlow<AdminHomeUiState> = combine(
        session.currentUser,
        queue,
        signalRepository.observeSignals().map { (it as? Snapshot.Data)?.value.orEmpty() }
    ) { user, waiting, signals ->
        AdminHomeUiState(
            user = user,
            waitingTickets = waiting,
            liveSignals = signals.count { it.status.isOngoing }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AdminHomeUiState())
}

// ─── Posting and minding signals ────────────────────────────────────────────

data class AdminSignalsUiState(
    val signals: List<Signal> = emptyList(),
    val workingId: String? = null,
    val error: String? = null
) {
    val ongoing: List<Signal> get() = signals.filter { it.status.isOngoing }
    val finished: List<Signal> get() = signals.filterNot { it.status.isOngoing }.take(10)
}

@HiltViewModel
class AdminSignalsViewModel @Inject constructor(
    private val repository: AdminRepository,
    signalRepository: SignalRepository
) : ViewModel() {

    private val working = MutableStateFlow<String?>(null)
    private val failure = MutableStateFlow<String?>(null)

    val state: StateFlow<AdminSignalsUiState> = combine(
        signalRepository.observeSignals(),
        working,
        failure
    ) { snapshot, workingId, error ->
        AdminSignalsUiState(
            signals = (snapshot as? Snapshot.Data)?.value.orEmpty(),
            workingId = workingId,
            error = error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AdminSignalsUiState())

    private val draftState = MutableStateFlow(SignalDraft())
    val draft: StateFlow<SignalDraft> = draftState.asStateFlow()

    fun edit(transform: (SignalDraft) -> SignalDraft) {
        draftState.value = transform(draftState.value)
    }

    fun post(onDone: () -> Unit) = viewModelScope.launch {
        val draft = draftState.value
        if (!draft.isComplete) return@launch
        working.value = "post"
        failure.value = null
        runCatching { repository.createSignal(draft) }
            .onSuccess {
                draftState.value = SignalDraft()
                onDone()
            }
            .onFailure { failure.value = "That could not be posted. ${it.localizedMessage ?: ""}".trim() }
        working.value = null
    }

    /**
     * Ticking a target writes `isHit`, which is what the milestone track and the
     * follower alerts read — and then moves the status with it, the way the
     * panel does, so followers are told "TP2 HIT" rather than being left on
     * ACTIVE with two ticks nobody can see.
     */
    fun toggleTarget(signal: Signal, index: Int) = viewModelScope.launch {
        val target = signal.takeProfits.getOrNull(index) ?: return@launch
        working.value = signal.id
        failure.value = null
        runCatching {
            repository.setTakeProfitHit(signal.id, index, !target.isHit)
            val hits = signal.takeProfits.mapIndexed { i, tp ->
                if (i == index) !target.isHit else tp.isHit
            }.count { it }
            repository.setSignalStatus(
                signal.id,
                when {
                    hits >= 2 -> SignalStatus.TP2_HIT
                    hits == 1 -> SignalStatus.TP1_HIT
                    else -> SignalStatus.ACTIVE
                }
            )
        }.onFailure { failure.value = "That did not save. ${it.localizedMessage ?: ""}".trim() }
        working.value = null
    }

    fun setStatus(signal: Signal, status: SignalStatus) = viewModelScope.launch {
        working.value = signal.id
        failure.value = null
        runCatching { repository.setSignalStatus(signal.id, status) }
            .onFailure { failure.value = "That did not save. ${it.localizedMessage ?: ""}".trim() }
        working.value = null
    }

    fun delete(signal: Signal) = viewModelScope.launch {
        working.value = signal.id
        runCatching { repository.deleteSignal(signal.id) }
            .onFailure { failure.value = "That could not be deleted. ${it.localizedMessage ?: ""}".trim() }
        working.value = null
    }
}

// ─── The exchange desk, from the other side of the counter ──────────────────

/** The desk's queues, as the panel's Deals page splits them. */
enum class DeskTab(val title: String) { REQUESTS("New requests"), NEEDS("Needs you"), WAITING("Waiting on member"), CHATS("Chats"), HISTORY("History") }

data class AdminDealsUiState(
    val deals: List<Deal> = emptyList(),
    val threads: List<com.talkswithtanha.twt.core.data.DeskThread> = emptyList(),
    val rate: com.talkswithtanha.twt.core.model.ExchangeRate? = null,
    val workingId: String? = null,
    val error: String? = null,
    val notice: String? = null
) {
    fun of(tab: DeskTab): List<Deal> = deals.filter {
        when (tab) {
            DeskTab.REQUESTS -> it.status == DealStatus.AWAITING_APPROVAL
            DeskTab.NEEDS -> it.status == DealStatus.PAYMENT_SENT || it.status == DealStatus.RELEASING || it.status == DealStatus.DISPUTED
            DeskTab.WAITING -> it.status == DealStatus.AWAITING_PAYMENT || it.status == DealStatus.PAYMENT_APPROVED
            DeskTab.HISTORY -> it.status == DealStatus.COMPLETED || it.status == DealStatus.CANCELLED
            DeskTab.CHATS -> false
        }
    }

    fun count(tab: DeskTab): Int = if (tab == DeskTab.CHATS) threads.count { it.unseen } else of(tab).size

    /** Finished today, and by whom: what the owner reads to see the desk's day. */
    val doneToday: List<Pair<String, Int>>
        get() {
            val start = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0)
            }.time
            return deals.filter { it.status == DealStatus.COMPLETED && (it.releasedAt ?: it.createdAt).after(start) }
                .groupingBy { it.releasedBy ?: "Desk" }.eachCount().toList().sortedByDescending { it.second }
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AdminDealsViewModel @Inject constructor(
    repository: AdminRepository,
    private val desk: com.talkswithtanha.twt.core.data.DeskRepository,
    rates: com.talkswithtanha.twt.core.data.ExchangeRateRepository,
    private val session: SessionRepository
) : ViewModel() {

    private val working = MutableStateFlow<String?>(null)
    private val failure = MutableStateFlow<String?>(null)
    private val notice = MutableStateFlow<String?>(null)

    private val messages = combine(working, failure, notice) { w, f, n -> Triple(w, f, n) }

    val state: StateFlow<AdminDealsUiState> = combine(
        repository.observeAllDeals(),
        desk.observeThreads(),
        rates.observeRate(),
        messages
    ) { snapshot, threads, rate, (workingId, error, info) ->
        AdminDealsUiState(
            deals = (snapshot as? Snapshot.Data)?.value.orEmpty(),
            threads = (threads as? Snapshot.Data)?.value.orEmpty(),
            rate = rate,
            workingId = workingId,
            notice = info,
            error = error ?: (snapshot as? Snapshot.Failed)?.let {
                "Deals could not be loaded. ${it.error.localizedMessage ?: "Check your connection."}"
            }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AdminDealsUiState())

    /** Who is acting, for the deal's timeline: the same name the panel records. */
    private val me: String get() = session.currentUser.value?.fullName?.takeIf { it.isNotBlank() } ?: "TWT Desk"

    private fun run(id: String, done: String, block: suspend () -> Unit) = viewModelScope.launch {
        working.value = id
        failure.value = null
        notice.value = null
        runCatching { block() }
            .onSuccess { notice.value = done }
            .onFailure {
                failure.value = (it as? com.talkswithtanha.twt.core.data.DeskRefusal)?.message
                    ?: "That did not save. Check your connection and try again."
            }
        working.value = null
    }

    fun accept(deal: Deal) = run(deal.id, "${deal.reference} accepted. The member can pay now.") { desk.accept(deal.id, me) }
    fun decline(deal: Deal, reason: String) = run(deal.id, "${deal.reference} declined.") { desk.decline(deal.id, reason) }
    fun cancel(deal: Deal, reason: String) = run(deal.id, "${deal.reference} cancelled.") { desk.cancel(deal.id, reason) }
    fun confirmPayment(deal: Deal) = run(deal.id, "${deal.reference}: payment confirmed.") { desk.confirmPayment(deal.id, me) }
    fun release(deal: Deal, reference: String) = run(deal.id, "${deal.reference} completed. The member was told.") { desk.release(deal.id, reference, me) }
    fun dispute(deal: Deal, reason: String) = run(deal.id, "${deal.reference} moved to review.") { desk.dispute(deal.id, reason) }
    fun setTakingDeals(on: Boolean) = run("desk", if (on) "Desk open for new deals." else "Desk closed for new deals.") { desk.setTakingDeals(on, me) }
    fun setFund(amount: Long) = run("desk", "Today's fund set to ${"%,d".format(amount)} USDT.") { desk.setDailyFund(amount, me) }
    fun dismissNotice() { notice.value = null; failure.value = null }
}

// ─── Banners ────────────────────────────────────────────────────────────────

data class AdminBannersUiState(
    val banners: List<AdminBanner> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class AdminBannersViewModel @Inject constructor(
    private val repository: AdminRepository
) : ViewModel() {

    private val failure = MutableStateFlow<String?>(null)

    val state: StateFlow<AdminBannersUiState> = combine(
        repository.observeBanners(),
        failure
    ) { snapshot, error ->
        AdminBannersUiState(
            banners = (snapshot as? Snapshot.Data)?.value.orEmpty(),
            error = error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AdminBannersUiState())

    fun setActive(banner: AdminBanner, isActive: Boolean) = viewModelScope.launch {
        runCatching { repository.setBannerActive(banner.id, isActive) }
            .onFailure { failure.value = "That could not be changed. ${it.localizedMessage ?: ""}".trim() }
    }

    fun save(banner: AdminBanner) = viewModelScope.launch {
        runCatching { repository.saveBanner(banner) }
            .onFailure { failure.value = "That could not be saved. ${it.localizedMessage ?: ""}".trim() }
    }

    fun delete(banner: AdminBanner) = viewModelScope.launch {
        runCatching { repository.deleteBanner(banner.id) }
            .onFailure { failure.value = "That could not be deleted. ${it.localizedMessage ?: ""}".trim() }
    }
}
