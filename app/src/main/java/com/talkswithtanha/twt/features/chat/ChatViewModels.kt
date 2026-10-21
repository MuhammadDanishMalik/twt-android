package com.talkswithtanha.twt.features.chat

import com.talkswithtanha.twt.core.moderation.ReportReason
import com.talkswithtanha.twt.core.moderation.ReportRepository
import com.talkswithtanha.twt.core.moderation.BlockedUsersStore
import com.talkswithtanha.twt.core.notes.VideoNotesStore
import com.talkswithtanha.twt.core.notes.VideoNote
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.WorkspacePremium
import com.talkswithtanha.twt.core.data.ChatRepository
import com.talkswithtanha.twt.core.data.Snapshot
import com.talkswithtanha.twt.core.firebase.FirestorePaths
import com.talkswithtanha.twt.core.model.ChatMessage
import com.talkswithtanha.twt.core.model.User
import com.talkswithtanha.twt.core.navigation.AppRoute
import com.talkswithtanha.twt.core.session.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One row in the chat list. */
data class ChatRoomSummary(
    val roomId: String,
    val title: String,
    val subtitle: String,
    val requiresAccess: Boolean,
    val icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    val initials: String? = null,
    /** Tanha's own thread wears the crown, the way it does on iOS. */
    val crowned: Boolean = false
)

@HiltViewModel
class ChatListViewModel @Inject constructor(
    session: SessionRepository
) : ViewModel() {

    val user: StateFlow<User?> = session.currentUser

    val rooms: StateFlow<List<ChatRoomSummary>> = session.currentUser
        .map { user ->
            buildList {
                add(
                    ChatRoomSummary(
                        roomId = FirestorePaths.ChatRoom.COMMUNITY,
                        title = "Community",
                        subtitle = "Everyone on TWT",
                        requiresAccess = false,
                        icon = Icons.Filled.Groups
                    )
                )
                add(
                    ChatRoomSummary(
                        roomId = FirestorePaths.ChatRoom.PREMIUM,
                        title = "Premium Community",
                        subtitle = "Signals talk and Tanha's desk",
                        requiresAccess = true,
                        icon = Icons.Filled.WorkspacePremium
                    )
                )
                user?.let {
                    add(
                        ChatRoomSummary(
                            roomId = FirestorePaths.ChatRoom.support(it.id),
                            title = "Talks with Tanha",
                            subtitle = "Ask about your access, signals or your account",
                            requiresAccess = false,
                            initials = "TT",
                            crowned = true
                        )
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

data class ChatRoomUiState(
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = "",
    val replyTo: ChatMessage? = null,
    /** A lesson note waiting to go out with the next message. */
    val pendingNote: ChatMessage.NoteAttachment? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val canPost: Boolean = false,
    /** Formatted buy rate, for the marketplace quick replies. */
    val exchangeRate: String? = null
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
private const val SEEN_WINDOW = 50

@HiltViewModel
class ChatRoomViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val session: SessionRepository,
    exchangeRates: com.talkswithtanha.twt.core.data.ExchangeRateRepository,
    notesStore: VideoNotesStore,
    private val blockedUsers: BlockedUsersStore,
    private val reports: ReportRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    /** The member's saved lesson notes, for the paperclip's share sheet. */
    val savedNotes: StateFlow<List<VideoNote>> = notesStore.notes

    private val rate = exchangeRates.observeRate()
        .map { it?.let { r -> com.talkswithtanha.twt.core.model.ExchangeRate.formatPaisa(r.buyPaisa) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val roomId: String = checkNotNull(savedStateHandle[AppRoute.ChatRoom.ARG])

    /** Marketplace presentation of the same thread. */
    val isSellerChat: Boolean =
        savedStateHandle.get<String>(AppRoute.ChatRoom.SELLER_ARG)?.toBoolean() == true

    val isSupportRoom: Boolean = roomId.startsWith(FirestorePaths.ChatRoom.SUPPORT_PREFIX)

    val roomTitle: String = when (roomId) {
        FirestorePaths.ChatRoom.PREMIUM -> "Premium Community"
        FirestorePaths.ChatRoom.COMMUNITY -> "Community"
        else -> "Talks with Tanha"
    }

    private val _draft = MutableStateFlow("")
    private val _replyTo = MutableStateFlow<ChatMessage?>(null)
    private val _pendingNote = MutableStateFlow<ChatMessage.NoteAttachment?>(null)
    private val _error = MutableStateFlow<String?>(null)
    private val _editing = MutableStateFlow<ChatMessage?>(null)
    /** The member's own message being edited, if any; its text is in the draft. */
    val editing: StateFlow<ChatMessage?> = _editing.asStateFlow()

    /** Who else is in the room and who is typing. Not shown in support threads. */
    val activity: StateFlow<com.talkswithtanha.twt.core.data.RoomActivity> = session.currentUser
        .flatMapLatest { user ->
            if (user == null || isSupportRoom) flowOf(com.talkswithtanha.twt.core.data.RoomActivity())
            else chatRepository.observeActivity(roomId, user.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.talkswithtanha.twt.core.data.RoomActivity())

    private var presenceJob: kotlinx.coroutines.Job? = null
    private var typingClearJob: kotlinx.coroutines.Job? = null
    private var lastTypingWrite = 0L

    private val messages: StateFlow<Snapshot<List<ChatMessage>>?> = session.currentUser
        .flatMapLatest { user ->
            if (user == null) flowOf(null)
            else chatRepository.observeMessages(roomId, user.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val state: StateFlow<ChatRoomUiState> = kotlinx.coroutines.flow.combine(
        messages,
        _draft,
        _replyTo,
        _error,
        session.currentUser,
        rate,
        _pendingNote,
        blockedUsers.blocked
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val snapshot = values[0] as Snapshot<List<ChatMessage>>?
        val draft = values[1] as String
        val replyTo = values[2] as ChatMessage?
        val error = values[3] as String?
        val user = values[4] as com.talkswithtanha.twt.core.model.User?
        val liveRate = values[5] as String?
        val pendingNote = values[6] as ChatMessage.NoteAttachment?
        @Suppress("UNCHECKED_CAST")
        val blocked = values[7] as Map<String, String>
        ChatRoomUiState(
            // A blocked member's messages are simply not shown to the blocker.
            messages = (snapshot as? Snapshot.Data)?.value.orEmpty()
                .filterNot { !it.isCurrentUser && it.senderId in blocked },
            draft = draft,
            replyTo = replyTo,
            pendingNote = pendingNote,
            isLoading = snapshot == null,
            error = error ?: (snapshot as? Snapshot.Failed)?.let {
                // The premium room is gated on *reading*. A member whose code
                // lapsed sees this rather than an empty room, which would look
                // like nobody had posted.
                if (roomId == FirestorePaths.ChatRoom.PREMIUM) {
                    "The premium room needs a live access code."
                } else {
                    "This room could not be loaded."
                }
            },
            // Blocking silences somebody without locking them out: they keep
            // reading, they cannot post. A member's own support thread stays
            // open even without a live code -- asking why your code will not
            // work must not require a working code.
            canPost = user != null && (user.canPostInCommunity || isSupportRoom),
            exchangeRate = liveRate
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatRoomUiState())

    // Messages this session has already recorded a view on, so a snapshot that
    // arrives again does not write again.
    private val markedSeen = mutableSetOf<String>()

    /**
     * Records that this member has seen the newest messages from other people.
     * Called by the screen while it is actually showing them — not from a
     * background collector, which would keep marking messages "seen" while the
     * room sat behind another screen. Only the latest few, so a first visit to
     * a long room is not a burst of writes.
     */
    fun markVisibleSeen() {
        val uid = session.currentUser.value?.id ?: return
        state.value.messages
            .takeLast(SEEN_WINDOW)
            .filter { it.senderId != uid && uid !in it.seenBy && !it.isPending && markedSeen.add(it.id) }
            .forEach { message ->
                viewModelScope.launch { runCatching { chatRepository.markSeen(roomId, message.id, uid) } }
            }
    }

    fun onDraftChange(value: String) {
        _draft.value = value
        reportTyping(value.isNotBlank())
    }

    /**
     * "Ali is typing…" for the others in the room. Written at most every 3 s
     * while keys are going, and cleared 4 s after the last one.
     */
    private fun reportTyping(active: Boolean) {
        if (isSupportRoom) return
        val user = session.currentUser.value ?: return
        typingClearJob?.cancel()
        if (!active) {
            if (lastTypingWrite != 0L) {
                lastTypingWrite = 0L
                viewModelScope.launch { runCatching { chatRepository.setTyping(roomId, user.id, user.fullName, false) } }
            }
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastTypingWrite > 3_000) {
            lastTypingWrite = now
            viewModelScope.launch { runCatching { chatRepository.setTyping(roomId, user.id, user.fullName, true) } }
        }
        typingClearJob = viewModelScope.launch {
            kotlinx.coroutines.delay(4_000)
            reportTyping(false)
        }
    }

    /**
     * Marks this member in the room while it is on screen, refreshed every 30 s
     * so the admin's "online now" stays honest; cleared when they leave.
     */
    fun onRoomVisible(visible: Boolean) {
        if (isSupportRoom) return
        val user = session.currentUser.value ?: return
        presenceJob?.cancel()
        if (visible) {
            presenceJob = viewModelScope.launch {
                while (true) {
                    runCatching { chatRepository.setPresence(roomId, user.id, user.fullName, true) }
                    kotlinx.coroutines.delay(30_000)
                }
            }
        } else {
            reportTyping(false)
            viewModelScope.launch { runCatching { chatRepository.setPresence(roomId, user.id, user.fullName, false) } }
        }
    }

    /** Starts editing the member's own message: its text goes into the box. */
    fun startEdit(message: ChatMessage) {
        _replyTo.value = null
        _editing.value = message
        _draft.value = message.text
    }

    /** Drops a reply or an edit in progress. */
    fun cancelCompose() {
        if (_editing.value != null) _draft.value = ""
        _editing.value = null
        _replyTo.value = null
    }

    fun delete(message: ChatMessage) = viewModelScope.launch {
        runCatching { chatRepository.delete(roomId, message.id) }
            .onFailure { _error.value = "That message could not be deleted." }
    }

    /** A quick reply fills the box rather than sending: the member nearly
     *  always wants to add an amount or a sentence of their own. */
    fun useQuickReply(text: String) { _draft.value = text }

    fun setReplyTo(message: ChatMessage?) {
        if (_editing.value != null) cancelCompose()
        _replyTo.value = message
    }

    /** Attaches a saved note to the next message — a copy, since notes live
     *  only on this phone. Null clears it. */
    fun setPendingNote(note: VideoNote?) {
        _pendingNote.value = note?.let {
            ChatMessage.NoteAttachment(
                noteId = it.id,
                videoId = it.videoId,
                videoTitle = it.videoTitle,
                timestamp = it.timestamp,
                noteText = it.text
            )
        }
    }

    fun send() {
        val user = session.currentUser.value ?: return
        val text = _draft.value.trim()
        reportTyping(false)

        // Saving an edit rather than sending something new.
        _editing.value?.let { original ->
            if (text.isEmpty()) return
            _editing.value = null
            _draft.value = ""
            if (text == original.text) return
            viewModelScope.launch {
                runCatching { chatRepository.edit(roomId, original.id, text) }
                    .onFailure {
                        _error.value = "That edit did not save."
                        _editing.value = original
                        _draft.value = text
                    }
            }
            return
        }
        val note = _pendingNote.value
        // A shared note can go out on its own, as on iOS.
        if (text.isEmpty() && note == null) return

        // Cleared immediately rather than on success. Firestore's offline queue
        // will deliver this, and leaving the text in the box makes a member on a
        // slow connection send it twice.
        _draft.value = ""
        val reply = _replyTo.value
        _replyTo.value = null
        _pendingNote.value = null

        viewModelScope.launch {
            runCatching {
                // The parent room document first, so a brand new support thread
                // shows up in Tanha's queue rather than existing only as an
                // orphaned subcollection.
                if (isSupportRoom) {
                    chatRepository.ensureSupportRoom(
                        uid = user.id,
                        userName = user.fullName,
                        userEmail = user.email,
                        lastMessage = text.ifEmpty { "Shared a lesson note" }
                    )
                }
                chatRepository.send(
                    roomId = roomId,
                    text = text,
                    senderId = user.id,
                    senderName = user.fullName,
                    replyTo = reply?.let {
                        ChatMessage.ReplyReference(it.senderName, it.text)
                    },
                    note = note
                )
            }.onFailure {
                _error.value = "That message did not send."
                _draft.value = text
                _pendingNote.value = note
            }
        }
    }

    /** Files a report for the team to review. [onDone] gets whether it went through. */
    fun report(message: ChatMessage, reason: ReportReason, onDone: (Boolean) -> Unit) {
        val user = session.currentUser.value ?: return onDone(false)
        viewModelScope.launch {
            val sent = runCatching {
                reports.reportMessage(
                    reporterId = user.id,
                    roomId = roomId,
                    messageId = message.id,
                    reportedUserId = message.senderId,
                    reportedUserName = message.senderName,
                    messageText = message.text.ifEmpty { message.noteAttachment?.noteText.orEmpty() },
                    reason = reason
                )
            }.isSuccess
            onDone(sent)
        }
    }

    /** Hides everything [message]'s author posts, on this phone. */
    fun block(message: ChatMessage) = blockedUsers.block(message.senderId, message.senderName)

    fun toggleReaction(message: ChatMessage, emoji: String) = viewModelScope.launch {
        val uid = session.currentUser.value?.id ?: return@launch
        runCatching { chatRepository.react(roomId, message.id, uid, emoji) }
    }

    fun dismissError() { _error.value = null }
}

/**
 * The Contact Support sheet's send button.
 *
 * Writes into the member's own `support_{uid}` thread, so the reply arrives in
 * the Chat tab rather than in an email they will not check.
 */
@HiltViewModel
class ContactSupportViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val session: SessionRepository
) : ViewModel() {

    fun send(message: String) {
        val user = session.currentUser.value ?: return
        if (message.isBlank()) return
        viewModelScope.launch {
            runCatching {
                chatRepository.ensureSupportRoom(
                    uid = user.id,
                    userName = user.fullName,
                    userEmail = user.email,
                    lastMessage = message
                )
                chatRepository.send(
                    roomId = FirestorePaths.ChatRoom.support(user.id),
                    text = message,
                    senderId = user.id,
                    senderName = user.fullName
                )
            }
        }
    }
}
