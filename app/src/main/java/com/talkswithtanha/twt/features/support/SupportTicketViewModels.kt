package com.talkswithtanha.twt.features.support

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.talkswithtanha.twt.core.data.Snapshot
import com.talkswithtanha.twt.core.data.SupportTicketRepository
import com.talkswithtanha.twt.core.model.StaffArea
import com.talkswithtanha.twt.core.model.SupportTicket
import com.talkswithtanha.twt.core.model.TicketMessage
import com.talkswithtanha.twt.core.model.TicketStatus
import com.talkswithtanha.twt.core.model.User
import com.talkswithtanha.twt.core.navigation.AppRoute
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
 * The member's tickets, and — for an account holding the `tickets` area — the
 * whole queue.
 *
 * Two listeners rather than one, because the security rules treat them
 * differently: the first is filtered to the signed-in member, which anybody may
 * run, and the second is the unfiltered collection, which only staff may read.
 * Starting the staff one for a member produces a permission error on every
 * launch, so it is only collected when [canWorkQueue] is true.
 */
data class TicketsUiState(
    val mine: List<SupportTicket> = emptyList(),
    val queue: List<SupportTicket> = emptyList(),
    val canWorkQueue: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null
) {
    /** Tickets the team has replied to that the member has not opened since. */
    val unreadForMember: Int get() = mine.count { it.unreadForMember }

    /** Members waiting on an answer — what staff see on the Chat tab. */
    val queueNeedingReply: Int get() = queue.count { it.needsReply && !it.status.isFinished }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SupportTicketsViewModel @Inject constructor(
    private val repository: SupportTicketRepository,
    private val session: SessionRepository
) : ViewModel() {

    private val mine: StateFlow<Snapshot<List<SupportTicket>>?> =
        session.currentUser
            .flatMapLatest { user ->
                if (user == null) flowOf(null) else repository.observeMyTickets(user.id)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val queue: StateFlow<Snapshot<List<SupportTicket>>?> =
        session.currentUser
            .flatMapLatest { user ->
                if (user?.can(StaffArea.TICKETS) == true) repository.observeQueue() else flowOf(null)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val state: StateFlow<TicketsUiState> =
        combine(mine, queue, session.currentUser) { mineSnapshot, queueSnapshot, user ->
            TicketsUiState(
                mine = (mineSnapshot as? Snapshot.Data)?.value.orEmpty(),
                queue = (queueSnapshot as? Snapshot.Data)?.value.orEmpty(),
                canWorkQueue = user?.can(StaffArea.TICKETS) == true,
                isLoading = mineSnapshot == null,
                // Named rather than swallowed: the overwhelmingly likely cause is
                // the composite index for memberId + updatedAt not being
                // deployed, which only fails once real tickets exist.
                error = (mineSnapshot as? Snapshot.Failed)?.let {
                    "Your tickets could not be loaded. ${it.error.localizedMessage ?: "Check your connection."}"
                }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TicketsUiState())

    /** Hands the new ticket's id back so the caller can open it. */
    fun create(
        subject: String,
        category: String,
        detail: String,
        onCreated: (String) -> Unit,
        onFailed: (String) -> Unit
    ) = viewModelScope.launch {
        val user = session.currentUser.value ?: return@launch
        runCatching {
            repository.create(
                subject = subject.trim(),
                category = category,
                firstMessage = detail.trim(),
                memberId = user.id,
                memberName = user.fullName,
                memberEmail = user.email
            )
        }
            .onSuccess(onCreated)
            .onFailure { onFailed("That could not be raised. Check your connection and try again.") }
    }
}

// ─── One ticket ─────────────────────────────────────────────────────────────

data class TicketThreadUiState(
    val ticket: SupportTicket? = null,
    val messages: List<TicketMessage> = emptyList(),
    val currentUser: User? = null,
    val isSending: Boolean = false,
    val error: String? = null
) {
    /**
     * Staff looking at somebody else's ticket act as the team; staff looking at
     * their own act as themselves, or the thread replies to itself.
     */
    val repliesAsStaff: Boolean
        get() = currentUser?.can(StaffArea.TICKETS) == true && ticket?.memberId != currentUser.id
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TicketThreadViewModel @Inject constructor(
    private val repository: SupportTicketRepository,
    private val session: SessionRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val ticketId: String = savedStateHandle[AppRoute.SupportTicket.ARG] ?: ""

    private val messages: StateFlow<List<TicketMessage>> =
        repository.observeMessages(ticketId)
            .map { (it as? Snapshot.Data)?.value.orEmpty() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val tickets: StateFlow<List<SupportTicket>> =
        session.currentUser
            .flatMapLatest { user ->
                when {
                    user == null -> flowOf(emptyList())
                    // Staff open tickets that are not their own, so the queue is
                    // what has to be searched for them.
                    user.can(StaffArea.TICKETS) -> repository.observeQueue()
                        .map { (it as? Snapshot.Data)?.value.orEmpty() }
                    else -> repository.observeMyTickets(user.id)
                        .map { (it as? Snapshot.Data)?.value.orEmpty() }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val sending = MutableStateFlow(false)
    private val failure = MutableStateFlow<String?>(null)

    val state: StateFlow<TicketThreadUiState> =
        combine(tickets, messages, session.currentUser, sending, failure) { all, msgs, user, isSending, error ->
            TicketThreadUiState(
                ticket = all.firstOrNull { it.id == ticketId },
                messages = msgs,
                currentUser = user,
                isSending = isSending,
                error = error
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TicketThreadUiState())

    fun markRead() = viewModelScope.launch {
        val ticket = state.value.ticket ?: return@launch
        if (!ticket.unreadForMember || state.value.repliesAsStaff) return@launch
        runCatching { repository.markRead(ticket.id) }
    }

    fun send(text: String) = viewModelScope.launch {
        val current = state.value
        val ticket = current.ticket ?: return@launch
        val user = current.currentUser ?: return@launch
        if (text.isBlank()) return@launch

        sending.value = true
        failure.value = null

        // A member answering a resolved ticket reopens it — that is what they
        // mean by replying, and making them raise a second ticket for the same
        // problem is how support systems earn their reputation.
        val status = when {
            current.repliesAsStaff -> TicketStatus.WAITING_ON_MEMBER
            ticket.status.isFinished -> TicketStatus.OPEN
            else -> ticket.status
        }

        runCatching {
            repository.send(
                ticketId = ticket.id,
                text = text.trim(),
                senderId = user.id,
                senderName = user.fullName,
                isStaff = current.repliesAsStaff,
                status = status
            )
        }.onFailure { failure.value = "That did not send. Check your connection and try again." }

        sending.value = false
    }

    fun setStatus(status: TicketStatus) = viewModelScope.launch {
        val current = state.value
        val ticket = current.ticket ?: return@launch
        runCatching { repository.setStatus(ticket.id, status, current.repliesAsStaff) }
            .onFailure { failure.value = "That could not be changed. Try again." }
    }
}
