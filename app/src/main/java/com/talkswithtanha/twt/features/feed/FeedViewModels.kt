package com.talkswithtanha.twt.features.feed

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.talkswithtanha.twt.core.data.FeedRepository
import com.talkswithtanha.twt.core.data.LiveApi
import com.talkswithtanha.twt.core.data.MarketsApi
import com.talkswithtanha.twt.core.data.SignalRepository
import com.talkswithtanha.twt.core.data.Snapshot
import com.talkswithtanha.twt.core.data.VoiceNote
import com.talkswithtanha.twt.core.images.CloudinaryUploader
import com.talkswithtanha.twt.core.images.UploadResult
import com.talkswithtanha.twt.core.model.FeedComment
import com.talkswithtanha.twt.core.model.FeedFilter
import com.talkswithtanha.twt.core.model.FeedPost
import com.talkswithtanha.twt.core.model.FeedState
import com.talkswithtanha.twt.core.model.Reaction
import com.talkswithtanha.twt.core.model.Signal
import com.talkswithtanha.twt.core.model.SignalUpdate
import com.talkswithtanha.twt.core.model.StaffArea
import com.talkswithtanha.twt.core.model.User
import com.talkswithtanha.twt.core.navigation.AppRoute
import com.talkswithtanha.twt.core.session.SessionRepository
import com.talkswithtanha.twt.core.signals.FollowedSignalTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** Posts per page — the first page stays live, more load as the reader reaches the end. */
private const val PAGE = 30

data class FeedUiState(
    /** Every live post loaded so far, newest first. */
    val posts: List<FeedPost> = emptyList(),
    val pinned: List<FeedPost> = emptyList(),
    /** Setups the desk updated in the last two days. */
    val updated: List<FeedPost> = emptyList(),
    val myReactions: Map<String, Reaction> = emptyMap(),
    val feedState: FeedState = FeedState(),
    /** The previous visit, read once when the screen opens; "new since" is measured from it. */
    val previousVisit: Long? = null,
    /** The trades behind the setup posts, by id. */
    val signals: Map<String, Signal> = emptyMap(),
    val followedSignalIds: Set<String> = emptySet(),
    /** Live prices for the pairs of running setups, by instrument key. */
    val quotes: Map<String, LiveApi.Quote> = emptyMap(),
    val events: List<MarketsApi.Event>? = null,
    val user: User? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val endReached: Boolean = false
) {
    /** Posting is the `appContent` area — the same hands that run the banners. */
    val canPost: Boolean get() = user?.can(StaffArea.APP_CONTENT) == true

    val filter: FeedFilter get() = feedState.filter

    fun isUnread(post: FeedPost): Boolean = previousVisit != null && post.createdAt.time > previousVisit

    fun isSaved(postId: String): Boolean = postId in feedState.saved

    fun quoteFor(pair: String): LiveApi.Quote? = LiveApi.find(pair)?.let { quotes[it.key] }
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

    private val limit = MutableStateFlow(PAGE)
    private val feed = limit.flatMapLatest { repository.observeFeed(it) }

    private val pinned = repository.observePinned()
    private val updated = repository.observeRecentlyUpdated()

    /** What this member has reacted with, per post — one small listener per post on screen. */
    private val myReactions: StateFlow<Map<String, Reaction>> =
        combine(
            feed.map { (it as? Snapshot.Data)?.value.orEmpty().map { post -> post.id } }.distinctUntilChanged(),
            session.currentUser
        ) { ids, user -> user?.let { ids to it } }
            .flatMapLatest { pair ->
                val ids = pair?.first.orEmpty()
                val user = pair?.second
                if (user == null || ids.isEmpty()) {
                    flowOf(emptyMap())
                } else {
                    combine(ids.map { id -> repository.observeMyReaction(id, user.id).map { id to it } }) { pairs ->
                        pairs.mapNotNull { (id, reaction) -> reaction?.let { id to it } }.toMap()
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * The member's saves, filter and last visit. Changes land here first, so
     * the screen answers at once, and go to the server behind it.
     */
    private val localState = MutableStateFlow<FeedState?>(null)
    private val previousVisit = MutableStateFlow<Long?>(null)

    private val serverState: Flow<FeedState?> = session.currentUser.flatMapLatest { user ->
        if (user == null) flowOf(null) else repository.observeFeedState(user.id)
    }

    private val signals: StateFlow<List<Signal>> =
        signalRepository.observeSignals()
            .map { (it as? Snapshot.Data)?.value.orEmpty() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val quotes = MutableStateFlow<Map<String, LiveApi.Quote>>(emptyMap())
    private val events = MutableStateFlow<List<MarketsApi.Event>?>(null)

    init {
        // The previous visit, read once; then this visit is marked a few
        // seconds in, so the dots and the divider stay put while reading.
        viewModelScope.launch {
            val first = serverState.filterNotNull().first()
            if (localState.value == null) localState.value = first
            previousVisit.value = first.lastSeenAt
            delay(4_000)
            val user = session.currentUser.value ?: return@launch
            val next = (localState.value ?: first).copy(lastSeenAt = System.currentTimeMillis())
            localState.value = next
            runCatching { repository.saveFeedState(user.id, next, setOf("lastSeenAt")) }
        }
        viewModelScope.launch { serverState.collect { s -> if (s != null) localState.value = s.copy(lastSeenAt = localState.value?.lastSeenAt ?: s.lastSeenAt) } }
        viewModelScope.launch { events.value = runCatching { MarketsApi.calendar() }.getOrNull() ?: emptyList() }
        // Prices for the running setups, every 15 seconds while the screen is up.
        viewModelScope.launch {
            while (true) {
                val keys = signals.value.filter { !it.isAnalysis && it.status.isOngoing }.mapNotNull { LiveApi.find(it.pair)?.key }.distinct()
                if (keys.isNotEmpty()) {
                    val next = quotes.value.toMutableMap()
                    keys.forEach { key -> runCatching { LiveApi.quote(key) }.getOrNull()?.let { next[key] = it } }
                    quotes.value = next
                }
                delay(15_000)
            }
        }
    }

    val state: StateFlow<FeedUiState> =
        combine(
            combine(feed, limit) { snapshot, n -> snapshot to n },
            combine(pinned, updated) { p, u -> p to u },
            combine(myReactions, localState, previousVisit) { r, s, v -> Triple(r, s, v) },
            combine(signals, tracker.follows, quotes, events) { list, follows, q, e -> listOf(list, follows, q, e) },
            session.currentUser
        ) { feedAndLimit, pinnedAndUpdated, mine, market, user ->
            val (snapshot, n) = feedAndLimit
            val (reactions, feedState, visit) = mine
            @Suppress("UNCHECKED_CAST") val signalList = market[0] as List<Signal>
            @Suppress("UNCHECKED_CAST") val follows = market[1] as List<com.talkswithtanha.twt.core.model.SignalFollow>
            @Suppress("UNCHECKED_CAST") val q = market[2] as Map<String, LiveApi.Quote>
            @Suppress("UNCHECKED_CAST") val e = market[3] as List<MarketsApi.Event>?
            val now = System.currentTimeMillis()
            val loaded = (snapshot as? Snapshot.Data)?.value.orEmpty()
            FeedUiState(
                posts = loaded.filter { it.isLive(now) },
                pinned = pinnedAndUpdated.first.filter { it.isLive(now) },
                updated = pinnedAndUpdated.second.filter { it.isLive(now) },
                myReactions = reactions,
                feedState = feedState ?: FeedState(),
                previousVisit = visit,
                signals = signalList.associateBy { it.id },
                followedSignalIds = follows.filter { it.isActive }.map { it.signalId }.toSet(),
                quotes = q,
                events = e,
                user = user,
                isLoading = snapshot !is Snapshot.Data,
                error = (snapshot as? Snapshot.Failed)?.let { "The feed could not be loaded. Check your connection." },
                endReached = snapshot is Snapshot.Data && loaded.size < n
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedUiState())

    fun loadMore() {
        val s = state.value
        if (s.isLoading || s.endReached) return
        limit.value += PAGE
    }

    fun setFilter(filter: FeedFilter) = updateState(setOf("filter")) { it.copy(filter = filter) }

    /** Saves into [collections] (empty = saved, no collection), or unsaves with null. */
    fun save(postId: String, collections: List<String>?) {
        val was = state.value.isSaved(postId)
        updateState(setOf("saved")) {
            val saved = it.saved.toMutableMap()
            if (collections == null) saved.remove(postId) else saved[postId] = collections
            it.copy(saved = saved)
        }
        val now = collections != null
        if (was != now) viewModelScope.launch { repository.countSave(postId, if (now) 1 else -1) }
    }

    private fun updateState(changed: Set<String>, change: (FeedState) -> FeedState) {
        val next = change(localState.value ?: FeedState())
        localState.value = next
        val user = session.currentUser.value ?: return
        viewModelScope.launch { runCatching { repository.saveFeedState(user.id, next, changed) } }
    }

    fun recordView(postId: String) = repository.recordView(postId)

    fun updatesFor(signalId: String): Flow<List<SignalUpdate>> = repository.observeSignalUpdates(signalId)

    fun react(post: FeedPost, kind: Reaction) = viewModelScope.launch {
        val user = session.currentUser.value ?: return@launch
        val previous = state.value.myReactions[post.id]
        // Tapping the one they already have takes it back.
        val next = if (previous == kind) null else kind
        runCatching { repository.react(post.id, user.id, next, previous) }
        // "Took it" on a setup also turns its alerts on — that is what taking it means.
        if (next == Reaction.TOOK) {
            post.signalId?.let(state.value.signals::get)?.let { signal ->
                if (signal.id !in state.value.followedSignalIds) tracker.follow(signal)
            }
        }
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
     * Sends a finished recording to Cloudinary, when the recording stops
     * rather than when Post is tapped, so the wait happens while Tanha types
     * the title. The local file is deleted either way.
     */
    fun uploadVoice(file: File, onDone: (url: String?, error: String?) -> Unit) = viewModelScope.launch {
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

    fun togglePin(post: FeedPost) = viewModelScope.launch { runCatching { repository.setPinned(post.id, !post.isPinned) } }

    fun follow(signal: Signal) = viewModelScope.launch { tracker.follow(signal) }

    fun unfollow(signalId: String) = viewModelScope.launch { tracker.unfollow(signalId) }

    fun delete(post: FeedPost) = viewModelScope.launch { runCatching { repository.deletePost(post.id) } }
}

// ─── One post, with its questions ───────────────────────────────────────────

data class PostUiState(
    val post: FeedPost? = null,
    val signal: Signal? = null,
    val isFollowingSignal: Boolean = false,
    val comments: List<FeedComment> = emptyList(),
    val myReaction: Reaction? = null,
    val feedState: FeedState = FeedState(),
    val quote: LiveApi.Quote? = null,
    val user: User? = null,
    val isSending: Boolean = false
) {
    /** Top-level questions, in the order they were asked. */
    val topLevel: List<FeedComment> get() = comments.filter { it.parentId == null }

    fun repliesTo(commentId: String): List<FeedComment> = comments.filter { it.parentId == commentId }

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
    private val localState = MutableStateFlow<FeedState?>(null)
    private val quote = MutableStateFlow<LiveApi.Quote?>(null)

    private val myReaction = session.currentUser.flatMapLatest { user ->
        if (user == null) flowOf(null) else repository.observeMyReaction(postId, user.id)
    }

    private val postFlow = repository.observePost(postId).map { (it as? Snapshot.Data)?.value }

    private val signalFlow = postFlow
        .map { it?.signalId }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else signalRepository.observeSignal(id).map { (it as? Snapshot.Data)?.value }
        }

    init {
        viewModelScope.launch {
            session.currentUser.flatMapLatest { user -> if (user == null) flowOf(null) else repository.observeFeedState(user.id) }
                .collect { if (it != null) localState.value = it }
        }
        repository.recordView(postId)
        viewModelScope.launch {
            while (true) {
                val signal = signalFlow.first()
                val key = signal?.takeIf { it.status.isOngoing }?.let { LiveApi.find(it.pair)?.key }
                if (key != null) quote.value = runCatching { LiveApi.quote(key) }.getOrNull() ?: quote.value
                delay(15_000)
            }
        }
    }

    val state: StateFlow<PostUiState> = combine(
        combine(postFlow, signalFlow, tracker.follows) { post, signal, follows ->
            Triple(post, signal, follows.any { it.signalId == signal?.id && it.isActive })
        },
        repository.observeComments(postId).map { (it as? Snapshot.Data)?.value.orEmpty() },
        combine(myReaction, localState, quote) { reaction, s, q -> Triple(reaction, s, q) },
        session.currentUser,
        sending
    ) { postSignalFollow, comments, mine, user, isSending ->
        val (post, signal, isFollowing) = postSignalFollow
        PostUiState(
            post = post,
            signal = signal,
            isFollowingSignal = isFollowing,
            comments = comments,
            myReaction = mine.first,
            feedState = mine.second ?: FeedState(),
            quote = mine.third,
            user = user,
            isSending = isSending
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PostUiState())

    fun updatesFor(signalId: String): Flow<List<SignalUpdate>> = repository.observeSignalUpdates(signalId)

    fun togglePin() = viewModelScope.launch {
        val post = state.value.post ?: return@launch
        runCatching { repository.setPinned(post.id, !post.isPinned) }
    }

    fun follow(signal: Signal) = viewModelScope.launch { tracker.follow(signal) }

    fun unfollow(signalId: String) = viewModelScope.launch { tracker.unfollow(signalId) }

    fun save(collections: List<String>?) {
        val user = session.currentUser.value ?: return
        val current = localState.value ?: FeedState()
        val was = postId in current.saved
        val saved = current.saved.toMutableMap()
        if (collections == null) saved.remove(postId) else saved[postId] = collections
        val next = current.copy(saved = saved)
        localState.value = next
        viewModelScope.launch {
            runCatching { repository.saveFeedState(user.id, next, setOf("saved")) }
            if (was != (collections != null)) repository.countSave(postId, if (collections != null) 1 else -1)
        }
    }

    fun react(kind: Reaction) = viewModelScope.launch {
        val user = session.currentUser.value ?: return@launch
        val previous = state.value.myReaction
        val next = if (previous == kind) null else kind
        runCatching { repository.react(postId, user.id, next, previous) }
        if (next == Reaction.TOOK) state.value.signal?.let { if (!state.value.isFollowingSignal) tracker.follow(it) }
    }

    fun reply(text: String, parentId: String?) = viewModelScope.launch {
        val user = session.currentUser.value ?: return@launch
        if (text.isBlank()) return@launch
        sending.value = true
        // Staff answering is Tanha's answer, badged as his.
        runCatching { repository.comment(postId, text, parentId, user, asAuthor = state.value.canModerate) }
        sending.value = false
    }

    fun deleteComment(comment: FeedComment) = viewModelScope.launch {
        runCatching { repository.deleteComment(postId, comment.id) }
    }
}
