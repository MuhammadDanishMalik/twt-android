package com.talkswithtanha.twt.core.badges

import android.content.Context
import com.talkswithtanha.twt.core.data.ChatRepository
import com.talkswithtanha.twt.core.data.FeedRepository
import com.talkswithtanha.twt.core.data.SignalRepository
import com.talkswithtanha.twt.core.data.Snapshot
import com.talkswithtanha.twt.core.firebase.FirestorePaths
import com.talkswithtanha.twt.core.model.ChatMessage
import com.talkswithtanha.twt.core.session.SessionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/** A room as the Chats list shows it: who said what last, and how much is new. */
data class RoomPreview(
    val lastText: String? = null,
    val lastSender: String? = null,
    val lastAt: Date? = null,
    val lastIsMine: Boolean = false,
    val unread: Int = 0,
    /** "Tanha is recording…" while it is happening, in place of the last message. */
    val activity: String? = null
)

/**
 * The counts on the badges: new messages in each room, new posts in the feed
 * and new signals since the member last looked.
 *
 * "Last looked" is kept on the phone, one time per room or tab, rather than in
 * Firestore. A badge is a nudge on this device, and keeping it here needs no
 * new rules and costs no writes on every open. Counting is over the latest
 * page of each room, so a busy room reads "30+" rather than an exact figure.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class Badges @Inject constructor(
    @param:ApplicationContext context: Context,
    session: SessionRepository,
    chats: ChatRepository,
    feed: FeedRepository,
    signals: SignalRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val prefs = context.getSharedPreferences("twt-badges", Context.MODE_PRIVATE)

    /** Bumped whenever a "last looked" time changes, so the counts recompute. */
    private val marks = MutableStateFlow(0)

    private fun lastRead(key: String): Long = prefs.getLong(key, 0L)

    /**
     * The first time a count is asked for on this phone there is no "last
     * looked" yet. Everything already there is history, not news: start the
     * clock now rather than lighting up every badge on a fresh install.
     */
    private fun lastReadOrStart(key: String): Long {
        val at = lastRead(key)
        if (at != 0L) return at
        val now = System.currentTimeMillis()
        prefs.edit().putLong(key, now).apply()
        return now
    }

    private fun mark(key: String) {
        prefs.edit().putLong(key, System.currentTimeMillis()).apply()
        marks.value++
    }

    // ── Chats ─────────────────────────────────────────────────────────────

    /** The rooms this member is in: everybody's, and their own with Tanha. */
    private val roomIds = session.currentUser.map { u ->
        if (u == null) emptyList()
        else listOf(FirestorePaths.ChatRoom.COMMUNITY, FirestorePaths.ChatRoom.support(u.id))
    }

    val rooms: StateFlow<Map<String, RoomPreview>> = combine(session.currentUser, roomIds) { u, ids -> u to ids }
        .flatMapLatest { (u, ids) ->
            if (u == null || ids.isEmpty()) return@flatMapLatest flowOf(emptyMap())
            val flows = ids.map { id ->
                // Only the private thread says who is typing: in the community
                // somebody always is, and the row would never show a message.
                val activity = if (id.startsWith(FirestorePaths.ChatRoom.SUPPORT_PREFIX)) {
                    chats.observeActivity(id, u.id).map { it.line() }
                } else {
                    flowOf(null)
                }
                combine(chats.observeMessages(id, u.id, PAGE.toLong()), marks, activity) { snap, _, line ->
                    id to preview(id, (snap as? Snapshot.Data)?.value.orEmpty()).copy(activity = line)
                }
            }
            combine(flows) { it.toMap() }
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    private fun preview(roomId: String, messages: List<ChatMessage>): RoomPreview {
        val last = messages.lastOrNull() ?: return RoomPreview()
        val since = lastReadOrStart(roomKey(roomId))
        return RoomPreview(
            lastText = when {
                last.audioUrl != null -> "🎤 Voice note"
                PHOTO_LINK.matches(last.text.trim()) -> "📷 Photo"
                last.noteAttachment != null && last.text.isBlank() -> "Shared a lesson note"
                else -> last.text
            },
            lastSender = last.senderName,
            lastAt = last.timestamp,
            lastIsMine = last.isCurrentUser,
            unread = messages.count { !it.isCurrentUser && it.timestamp.time > since }
        )
    }

    val chatsUnread: StateFlow<Int> = rooms.map { it.values.sumOf { r -> r.unread } }
        .stateIn(scope, SharingStarted.Eagerly, 0)

    /** The member has the room open: everything in it is read. */
    fun markRoomRead(roomId: String) = mark(roomKey(roomId))

    // ── Feed ──────────────────────────────────────────────────────────────

    val feedNew: StateFlow<Int> = combine(feed.observeFeed(PAGE), marks) { snap, _ ->
        val since = lastReadOrStart(FEED)
        (snap as? Snapshot.Data)?.value.orEmpty().count { it.createdAt.time > since }
    }.stateIn(scope, SharingStarted.Eagerly, 0)

    fun markFeedSeen() = mark(FEED)

    // ── Signals ───────────────────────────────────────────────────────────

    val signalsNew: StateFlow<Int> = session.currentUser
        .flatMapLatest { u ->
            if (u == null) flowOf(0)
            else combine(signals.observeSignals(PAGE.toLong()), marks) { snap, _ ->
                val since = lastReadOrStart(SIGNALS)
                (snap as? Snapshot.Data)?.value.orEmpty().count { it.timestamp.time > since }
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, 0)

    fun markSignalsSeen() = mark(SIGNALS)

    private fun roomKey(roomId: String) = "room.$roomId"

    private companion object {
        /** A message that is nothing but a link to a picture: a photo sent in the chat. */
        val PHOTO_LINK = Regex("""https?://\S+?\.(?:png|jpe?g|gif|webp)(?:\?\S*)?""", RegexOption.IGNORE_CASE)
        const val PAGE = 30
        const val FEED = "feed"
        const val SIGNALS = "signals"
    }
}

/** "30+" once a count reaches the page size. */
fun badgeText(count: Int): String = if (count >= 30) "30+" else count.toString()
