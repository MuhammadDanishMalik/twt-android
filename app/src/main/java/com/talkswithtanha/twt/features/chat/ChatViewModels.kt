package com.talkswithtanha.twt.features.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val requiresAccess: Boolean
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
                        subtitle = "Everyone in TWT",
                        requiresAccess = false
                    )
                )
                add(
                    ChatRoomSummary(
                        roomId = FirestorePaths.ChatRoom.PREMIUM,
                        title = "Premium room",
                        subtitle = "Code holders only",
                        requiresAccess = true
                    )
                )
                user?.let {
                    add(
                        ChatRoomSummary(
                            roomId = FirestorePaths.ChatRoom.support(it.id),
                            title = "Talks with Tanha",
                            subtitle = "Your private thread",
                            requiresAccess = false
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
    val isLoading: Boolean = true,
    val error: String? = null,
    val canPost: Boolean = false
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatRoomViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val session: SessionRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val roomId: String = checkNotNull(savedStateHandle[AppRoute.ChatRoom.ARG])

    val isSupportRoom: Boolean = roomId.startsWith(FirestorePaths.ChatRoom.SUPPORT_PREFIX)

    private val _draft = MutableStateFlow("")
    private val _replyTo = MutableStateFlow<ChatMessage?>(null)
    private val _error = MutableStateFlow<String?>(null)

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
        session.currentUser
    ) { snapshot, draft, replyTo, error, user ->
        ChatRoomUiState(
            messages = (snapshot as? Snapshot.Data)?.value.orEmpty(),
            draft = draft,
            replyTo = replyTo,
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
            canPost = user != null && (user.canPostInCommunity || isSupportRoom)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatRoomUiState())

    fun onDraftChange(value: String) { _draft.value = value }

    fun setReplyTo(message: ChatMessage?) { _replyTo.value = message }

    fun send() {
        val user = session.currentUser.value ?: return
        val text = _draft.value.trim()
        if (text.isEmpty()) return

        // Cleared immediately rather than on success. Firestore's offline queue
        // will deliver this, and leaving the text in the box makes a member on a
        // slow connection send it twice.
        _draft.value = ""
        val reply = _replyTo.value
        _replyTo.value = null

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
                        lastMessage = text
                    )
                }
                chatRepository.send(
                    roomId = roomId,
                    text = text,
                    senderId = user.id,
                    senderName = user.fullName,
                    replyTo = reply?.let {
                        ChatMessage.ReplyReference(it.senderName, it.text)
                    }
                )
            }.onFailure {
                _error.value = "That message did not send."
                _draft.value = text
            }
        }
    }

    fun toggleReaction(message: ChatMessage, emoji: String) = viewModelScope.launch {
        val alreadyReacted = (message.reactions[emoji] ?: 0) > 0
        runCatching {
            chatRepository.toggleReaction(roomId, message.id, emoji, !alreadyReacted)
        }
    }

    fun dismissError() { _error.value = null }
}
