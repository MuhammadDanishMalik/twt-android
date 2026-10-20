package com.talkswithtanha.twt.features.feed

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.talkswithtanha.twt.core.data.FeedRepository
import com.talkswithtanha.twt.core.data.SignalRepository
import com.talkswithtanha.twt.core.data.Snapshot
import com.talkswithtanha.twt.core.model.FeedComment
import com.talkswithtanha.twt.core.data.VoiceNote
import com.talkswithtanha.twt.core.images.CloudinaryUploader
import com.talkswithtanha.twt.core.images.UploadResult
import com.talkswithtanha.twt.core.model.FeedPost
import com.talkswithtanha.twt.core.model.Reaction
import com.talkswithtanha.twt.core.model.Signal
import com.talkswithtanha.twt.core.model.StaffArea
import com.talkswithtanha.twt.core.model.User
import com.talkswithtanha.twt.core.navigation.AppRoute
import com.talkswithtanha.twt.core.session.SessionRepository
import com.talkswithtanha.twt.core.signals.FollowedSignalTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class FeedUiState(
    val posts: List<FeedPost> = emptyList(),
    val myReactions: Map<String, Reaction> = emptyMap(),
    val mySaves: Set<String> = emptySet(),
    /** The trades behind the signal posts, by id. */
    val signals: Map<String, Signal> = emptyMap(),
    val followedSignalIds: Set<String> = emptySet(),
    val user: User? = null,
    val isLoading: Boolean = true,
    val error: String? = null
) {
    /** Posting is the `appContent` area — the same hands that run the banners. */
    val canPost: Boolean get() = user?.can(StaffArea.APP_CONTENT) == true
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FeedViewModel @Inject constructor(
    private val repository: FeedRepository,
    private val signalRepository: SignalRepository,
    private val tracker: FollowedSignalTracker,
    private val uploader: CloudinaryUploader,
    private val session: SessionRepository
) : ViewModel() {

    private val feed = repository.observeFeed()

    /**
     * What this member has reacted with, per post.
     *
     * One listener per post would be fifty listeners on a scrolling screen. This
     * is a single read of the member's own reaction documents, refreshed when
     * the feed changes or they react — enough for a heart that fills in, and a
     * great deal cheaper.
     */
    private val reactionRefresh = MutableStateFlow(0)

    private val myReactions: StateFlow<Map<String, Reaction>> =
        combine(
            feed.map { (it as? Snapshot.Data)?.value.orEmpty().map { post -> post.id } },
            session.currentUser,
            reactionRefresh
        ) { ids, user, _ ->
            // Null while signed out, so the listeners below are never opened for
            // an account the rules would refuse.
            user?.let { ids to it }
        }
            .flatMapLatest { pair ->
                val ids = pair?.first.orEmpty()
                val user = pair?.second
                if (user == null || ids.isEmpty()) {
                    flowOf(emptyMap())
                } else {
                    combine(
                        ids.map { id -> repository.observeMyReaction(id, user.id).map { id to it } }
                    ) { pairs ->
                        pairs.mapNotNull { (id, reaction) -> reaction?.let { id to it } }.toMap()
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val mySaves: StateFlow<Set<String>> =
        combine(
            feed.map { (it as? Snapshot.Data)?.value.orEmpty().map { post -> post.id } },
            session.currentUser
        ) { ids, user -> user?.let { ids to it } }
            .flatMapLatest { pair ->
                val ids = pair?.first.orEmpty()
                val user = pair?.second
                if (user == null || ids.isEmpty()) {
                    flowOf(emptySet())
                } else {
                    combine(
                        ids.map { id -> repository.observeMySave(id, user.id).map { id to it } }
                    ) { pairs -> pairs.filter { it.second }.map { it.first }.toSet() }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /**
     * The trades the feed is showing.
     *
     * Read from the signals feed the app already has open rather than fetched
     * one document at a time: a signal post is a *view* of a trade, and a card
     * showing an entry that moved an hour ago is worse than no card.
     */
    private val signals: StateFlow<List<Signal>> =
        signalRepository.observeSignals()
            .map { (it as? Snapshot.Data)?.value.orEmpty() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val state: StateFlow<FeedUiState> =
        combine(
            feed,
            myReactions,
            mySaves,
            combine(signals, tracker.follows) { list, follows -> list to follows },
            session.currentUser
        ) { snapshot, reactions, saves, signalsAndFollows, user ->
            val (signalList, follows) = signalsAndFollows
            FeedUiState(
                posts = (snapshot as? Snapshot.Data)?.value.orEmpty(),
                myReactions = reactions,
                mySaves = saves,
                signals = signalList.associateBy { it.id },
                followedSignalIds = follows.filter { it.isActive }.map { it.signalId }.toSet(),
                user = user,
                isLoading = snapshot !is Snapshot.Data,
                error = (snapshot as? Snapshot.Failed)?.let {
                    "The feed could not be loaded. ${it.error.localizedMessage ?: "Check your connection."}"
                }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedUiState())

    fun react(post: FeedPost, kind: Reaction) = viewModelScope.launch {
        val user = session.currentUser.value ?: return@launch
        val previous = state.value.myReactions[post.id]
        // Tapping the one they already have takes it back, the way a heart does.
        val next = if (previous == kind) null else kind
        runCatching { repository.react(post.id, user.id, next, previous) }
        reactionRefresh.value += 1
    }

    fun post(
        text: String,
        images: List<String>,
        videoUrl: String?,
        pair: String?,
        signalId: String?,
        isPinned: Boolean,
        audio: VoiceNote?,
        onDone: () -> Unit
    ) = viewModelScope.launch {
        val user = session.currentUser.value ?: return@launch
        runCatching { repository.post(text, images, videoUrl, pair, user, signalId, isPinned, audio) }
            .onSuccess { onDone() }
    }

    /**
     * Sends a finished recording to Cloudinary.
     *
     * Done when the recording stops rather than when Post is tapped, so the
     * wait happens while Tanha is typing the title — and so a rejected upload
     * is found at the point it can still be re-recorded, not after the words
     * have been written.
     *
     * The local file is deleted either way: it has served its purpose, and a
     * failed one is not worth keeping in a cache the system may clear anyway.
     */
    fun uploadVoice(
        file: File,
        onDone: (url: String?, error: String?) -> Unit
    ) = viewModelScope.launch {
        when (val result = uploader.uploadVoiceNote(file)) {
            is UploadResult.Success -> {
                file.delete()
                onDone(result.url, null)
            }
            is UploadResult.Failed -> {
                file.delete()
                onDone(null, result.message)
            }
        }
    }

    fun togglePin(post: FeedPost) = viewModelScope.launch {
        runCatching { repository.setPinned(post.id, !post.isPinned) }
    }

    fun follow(signal: Signal) = viewModelScope.launch { tracker.follow(signal) }

    fun unfollow(signalId: String) = viewModelScope.launch { tracker.unfollow(signalId) }

    fun toggleSave(post: FeedPost) = viewModelScope.launch {
        val user = session.currentUser.value ?: return@launch
        runCatching { repository.setSaved(post.id, user.id, post.id !in state.value.mySaves) }
    }

    fun delete(post: FeedPost) = viewModelScope.launch {
        runCatching { repository.deletePost(post.id) }
    }
}

// ─── One post, with its replies ─────────────────────────────────────────────

data class PostUiState(
    val post: FeedPost? = null,
    val signal: Signal? = null,
    val isFollowingSignal: Boolean = false,
    val comments: List<FeedComment> = emptyList(),
    val myReaction: Reaction? = null,
    val isSaved: Boolean = false,
    val user: User? = null,
    val isSending: Boolean = false
) {
    /** Top-level replies, in the order they were written. */
    val topLevel: List<FeedComment> get() = comments.filter { it.parentId == null }

    fun repliesTo(commentId: String): List<FeedComment> =
        comments.filter { it.parentId == commentId }

    val canModerate: Boolean get() = user?.can(StaffArea.APP_CONTENT) == true
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PostDetailViewModel @Inject constructor(
    private val repository: FeedRepository,
    private val signalRepository: SignalRepository,
    private val tracker: FollowedSignalTracker,
    private val session: SessionRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val postId: String = savedStateHandle[AppRoute.FeedPost.ARG] ?: ""

    private val sending = MutableStateFlow(false)

    private val myReaction = session.currentUser.flatMapLatest { user ->
        if (user == null) flowOf(null) else repository.observeMyReaction(postId, user.id)
    }

    private val saved = session.currentUser.flatMapLatest { user ->
        if (user == null) flowOf(false) else repository.observeMySave(postId, user.id)
    }

    private val postFlow = repository.observePost(postId).map { (it as? Snapshot.Data)?.value }

    private val signalFlow = postFlow
        .map { it?.signalId }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else signalRepository.observeSignal(id).map { (it as? Snapshot.Data)?.value }
        }

    val state: StateFlow<PostUiState> = combine(
        combine(postFlow, signalFlow, tracker.follows) { post, signal, follows ->
            Triple(post, signal, follows.any { it.signalId == signal?.id && it.isActive })
        },
        repository.observeComments(postId).map { (it as? Snapshot.Data)?.value.orEmpty() },
        combine(myReaction, saved) { reaction, isSaved -> reaction to isSaved },
        session.currentUser,
        sending
    ) { postSignalFollow, comments, reactionAndSave, user, isSending ->
        val (post, signal, isFollowing) = postSignalFollow
        PostUiState(
            post = post,
            signal = signal,
            isFollowingSignal = isFollowing,
            comments = comments,
            myReaction = reactionAndSave.first,
            isSaved = reactionAndSave.second,
            user = user,
            isSending = isSending
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PostUiState())

    fun togglePin() = viewModelScope.launch {
        val post = state.value.post ?: return@launch
        runCatching { repository.setPinned(post.id, !post.isPinned) }
    }

    fun follow(signal: Signal) = viewModelScope.launch { tracker.follow(signal) }

    fun unfollow(signalId: String) = viewModelScope.launch { tracker.unfollow(signalId) }

    fun toggleSave() = viewModelScope.launch {
        val user = session.currentUser.value ?: return@launch
        runCatching { repository.setSaved(postId, user.id, !state.value.isSaved) }
    }

    fun react(kind: Reaction) = viewModelScope.launch {
        val user = session.currentUser.value ?: return@launch
        val previous = state.value.myReaction
        runCatching { repository.react(postId, user.id, if (previous == kind) null else kind, previous) }
    }

    fun reply(text: String, parentId: String?) = viewModelScope.launch {
        val user = session.currentUser.value ?: return@launch
        if (text.isBlank()) return@launch
        sending.value = true
        runCatching { repository.comment(postId, text, parentId, user) }
        sending.value = false
    }

    fun deleteComment(comment: FeedComment) = viewModelScope.launch {
        runCatching { repository.deleteComment(postId, comment.id) }
    }
}
