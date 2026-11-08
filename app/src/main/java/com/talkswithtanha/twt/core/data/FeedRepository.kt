package com.talkswithtanha.twt.core.data

import android.content.Context
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.talkswithtanha.twt.core.firebase.FirestorePaths.Collection
import com.talkswithtanha.twt.core.firebase.FirestorePaths.FeedCommentField as C
import com.talkswithtanha.twt.core.firebase.FirestorePaths.FeedField as F
import com.talkswithtanha.twt.core.firebase.FirestorePaths.FeedReactionField as R
import com.talkswithtanha.twt.core.model.FeedComment
import com.talkswithtanha.twt.core.model.FeedFilter
import com.talkswithtanha.twt.core.model.FeedPost
import com.talkswithtanha.twt.core.model.FeedState
import com.talkswithtanha.twt.core.model.FilterType
import com.talkswithtanha.twt.core.model.LinkCard
import com.talkswithtanha.twt.core.model.PostType
import com.talkswithtanha.twt.core.model.Reaction
import com.talkswithtanha.twt.core.model.Signal
import com.talkswithtanha.twt.core.model.SignalUpdate
import com.talkswithtanha.twt.core.model.User
import com.talkswithtanha.twt.core.model.VideoChapter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The feed: Tanha's posts, members' reactions and questions, and each
 * member's own state (saves, last visit, filter).
 *
 * ### Counters, and why the client keeps them
 *
 * A post carries its own `reactionCount`, `commentCount`, `viewCount` and
 * `saveCount`. Counting the subcollections instead would mean reading every
 * reaction on every post to draw one number on a card. The rules let a member
 * move those counters by one and touch nothing else.
 *
 * The website reads and writes the same documents (twt-web/lib/feed-repo.ts
 * and feed-state.ts), so a post saved on the phone is saved on the website.
 */
/** A voice note that has already been uploaded, on its way into a post. */
data class VoiceNote(val url: String, val title: String, val seconds: Int)

interface FeedRepository {
    fun observeFeed(limit: Int = 30): Flow<Snapshot<List<FeedPost>>>
    fun observePost(postId: String): Flow<Snapshot<FeedPost?>>

    /** Pinned posts, wherever they sit in time. */
    fun observePinned(): Flow<List<FeedPost>>

    /** Setups the desk updated in the last two days — TP hit, stop moved, closed. */
    fun observeRecentlyUpdated(): Flow<List<FeedPost>>

    /** A trade's timeline, newest first. */
    fun observeSignalUpdates(signalId: String): Flow<List<SignalUpdate>>

    /** Questions and answers, oldest first — the order a conversation happened in. */
    fun observeComments(postId: String): Flow<Snapshot<List<FeedComment>>>

    /** What this member has reacted with, if anything. */
    fun observeMyReaction(postId: String, userId: String): Flow<Reaction?>

    fun observeFeedState(userId: String): Flow<FeedState?>
    suspend fun saveFeedState(userId: String, state: FeedState, changed: Set<String>)

    /** Counts a view once per post on this phone. */
    fun recordView(postId: String)

    /** Keeps the post's save count in step when a member saves or unsaves it. */
    suspend fun countSave(postId: String, delta: Int)

    suspend fun post(
        text: String,
        images: List<String>,
        videoUrl: String?,
        pair: String?,
        author: User,
        signalId: String? = null,
        isPinned: Boolean = false,
        audio: VoiceNote? = null
    ): String

    /** Puts a freshly posted trade in the feed. */
    suspend fun shareSignal(signal: Signal, author: User): String

    suspend fun setPinned(postId: String, isPinned: Boolean)
    suspend fun deletePost(postId: String)

    /** Sets, changes or clears a reaction. Passing the kind they already have clears it. */
    suspend fun react(postId: String, userId: String, kind: Reaction?, previous: Reaction?)

    suspend fun comment(postId: String, text: String, parentId: String?, author: User, asAuthor: Boolean = false): String
    suspend fun deleteComment(postId: String, commentId: String)
}

@Singleton
class FirebaseFeedRepository @Inject constructor(
    private val db: FirebaseFirestore,
    @ApplicationContext private val context: Context
) : FeedRepository {

    private val posts get() = db.collection(Collection.FEED_POSTS)
    private fun comments(postId: String) = posts.document(postId).collection(Collection.COMMENTS)
    private fun reactions(postId: String) = posts.document(postId).collection(Collection.REACTIONS)
    private fun feedState(userId: String) = db.collection("users").document(userId).collection("feedState").document("main")

    // ── Reads ────────────────────────────────────────────────────────────

    override fun observeFeed(limit: Int): Flow<Snapshot<List<FeedPost>>> =
        posts
            .orderBy(F.CREATED_AT, Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .snapshotFlow()
            .map { snapshot ->
                when (snapshot) {
                    is Snapshot.Failed -> snapshot
                    is Snapshot.Data -> Snapshot.Data(snapshot.value.documents.mapNotNull { map(it) })
                }
            }

    override fun observePost(postId: String): Flow<Snapshot<FeedPost?>> =
        posts.document(postId).snapshotFlow().map { snapshot ->
            when (snapshot) {
                is Snapshot.Failed -> snapshot
                is Snapshot.Data -> Snapshot.Data(snapshot.value?.let { map(it) })
            }
        }

    override fun observePinned(): Flow<List<FeedPost>> =
        posts.whereEqualTo(F.IS_PINNED, true).limit(5).snapshotFlow().map { snapshot ->
            (snapshot as? Snapshot.Data)?.value?.documents.orEmpty().mapNotNull { map(it) }.sortedByDescending { it.createdAt }
        }

    override fun observeRecentlyUpdated(): Flow<List<FeedPost>> =
        posts.orderBy(F.BUMPED_AT, Query.Direction.DESCENDING).limit(4).snapshotFlow().map { snapshot ->
            val cutoff = System.currentTimeMillis() - 48 * 3_600_000L
            (snapshot as? Snapshot.Data)?.value?.documents.orEmpty()
                // Bumped after it was posted, not merely stamped when it was
                // made: the panel sets bumpedAt on a new post too, and every
                // fresh post was being labelled "UPDATED".
                .filter {
                    val bumped = it.getDate(F.BUMPED_AT)?.time ?: 0L
                    val created = it.getDate(F.CREATED_AT)?.time ?: 0L
                    bumped > cutoff && bumped - created > 2 * 60_000L
                }
                .mapNotNull { map(it) }
        }

    override fun observeSignalUpdates(signalId: String): Flow<List<SignalUpdate>> =
        db.collection(Collection.SIGNALS).document(signalId).collection("updates")
            .orderBy("at", Query.Direction.DESCENDING)
            .limit(20)
            .snapshotFlow()
            .map { snapshot ->
                (snapshot as? Snapshot.Data)?.value?.documents.orEmpty().map { d ->
                    SignalUpdate(
                        id = d.id,
                        type = d.getString("type") ?: "note",
                        note = d.getString("note").orEmpty(),
                        price = d.getString("price"),
                        at = d.getDate("at") ?: Date(),
                        audioUrl = d.getString("audioUrl"),
                        audioDurationSec = (d.getLong("audioDurationSec") ?: 0L).toInt(),
                        transcript = d.getString("transcript")
                    )
                }
            }

    override fun observeComments(postId: String): Flow<Snapshot<List<FeedComment>>> =
        comments(postId)
            .orderBy(C.CREATED_AT)
            .limit(200)
            .snapshotFlow()
            .map { snapshot ->
                when (snapshot) {
                    is Snapshot.Failed -> snapshot
                    is Snapshot.Data -> Snapshot.Data(snapshot.value.documents.mapNotNull { comment(it) })
                }
            }

    override fun observeMyReaction(postId: String, userId: String): Flow<Reaction?> =
        reactions(postId).document(userId).snapshotFlow().map { snapshot ->
            when (snapshot) {
                is Snapshot.Failed -> null
                is Snapshot.Data -> Reaction.from(snapshot.value?.getString(R.KIND))
            }
        }

    // ── The member's own state ───────────────────────────────────────────

    override fun observeFeedState(userId: String): Flow<FeedState?> =
        feedState(userId).snapshotFlow().map { snapshot ->
            val d = (snapshot as? Snapshot.Data)?.value ?: return@map null
            if (!d.exists()) return@map FeedState()
            @Suppress("UNCHECKED_CAST")
            val saved = (d.get("saved") as? Map<String, Any?>).orEmpty().mapValues { (_, v) ->
                (v as? List<*>).orEmpty().filterIsInstance<String>()
            }
            @Suppress("UNCHECKED_CAST")
            val filter = (d.get("filter") as? Map<String, Any?>).orEmpty()
            val collections = (d.get("collections") as? List<*>).orEmpty().filterIsInstance<String>()
            FeedState(
                saved = saved,
                lastSeenAt = d.getTimestamp("lastSeenAt")?.toDate()?.time,
                filter = FeedFilter(
                    type = FilterType.from(filter["type"] as? String),
                    pair = filter["pair"] as? String,
                    unreadOnly = filter["unreadOnly"] == true
                ),
                collections = collections.ifEmpty { FeedState.DEFAULT_COLLECTIONS }
            )
        }

    override suspend fun saveFeedState(userId: String, state: FeedState, changed: Set<String>) {
        val payload = mutableMapOf<String, Any?>()
        if ("saved" in changed) payload["saved"] = state.saved
        if ("filter" in changed) {
            payload["filter"] = mapOf("type" to state.filter.type.stored, "pair" to state.filter.pair, "unreadOnly" to state.filter.unreadOnly)
        }
        if ("collections" in changed) payload["collections"] = state.collections
        if ("lastSeenAt" in changed) payload["lastSeenAt"] = state.lastSeenAt?.let { Timestamp(Date(it)) }
        if (payload.isEmpty()) return
        feedState(userId).set(payload, SetOptions.merge()).await()
    }

    override fun recordView(postId: String) {
        val prefs = context.getSharedPreferences("twt-feed", Context.MODE_PRIVATE)
        val seen = prefs.getStringSet("viewed", emptySet()).orEmpty()
        if (postId in seen) return
        prefs.edit().putStringSet("viewed", (seen + postId).toList().takeLast(500).toSet()).apply()
        posts.document(postId).update(F.VIEW_COUNT, FieldValue.increment(1))
    }

    override suspend fun countSave(postId: String, delta: Int) {
        runCatching { posts.document(postId).update(F.SAVE_COUNT, FieldValue.increment(delta.toLong())).await() }
    }

    // ── Writes ───────────────────────────────────────────────────────────

    override suspend fun shareSignal(signal: Signal, author: User): String =
        post(
            text = signal.notes?.takeIf { it.isNotBlank() } ?: "New ${signal.type.stored.lowercase()} on ${signal.pair}.",
            images = emptyList(),
            videoUrl = null,
            pair = signal.pair,
            author = author,
            signalId = signal.id
        )

    override suspend fun post(
        text: String,
        images: List<String>,
        videoUrl: String?,
        pair: String?,
        author: User,
        signalId: String?,
        isPinned: Boolean,
        audio: VoiceNote?
    ): String {
        val cleanPair = pair?.trim()?.uppercase()?.ifEmpty { null }
        val cleanVideo = videoUrl?.trim()?.ifEmpty { null }
        val cleanImages = images.filter { it.isNotBlank() }
        // The type is read from what the post carries, as the website's composer does.
        val type = when {
            signalId != null -> PostType.SETUP
            cleanVideo != null -> PostType.VIDEO
            audio != null && cleanImages.isEmpty() -> PostType.VOICE
            cleanImages.isNotEmpty() -> PostType.CHART
            else -> PostType.NOTE
        }
        val document = posts.document()
        document.set(
            mapOf(
                F.AUTHOR_ID to author.id,
                F.AUTHOR_NAME to author.fullName,
                F.AUTHOR_PHOTO to author.profilePhoto,
                F.TEXT to text.trim(),
                F.IMAGES to cleanImages,
                F.VIDEO_URL to cleanVideo,
                F.PAIR to cleanPair,
                F.PAIRS to listOfNotNull(cleanPair),
                F.TYPE to type.stored,
                F.COMMENTS_ENABLED to true,
                F.SIGNAL_ID to signalId,
                F.AUDIO_URL to audio?.url,
                F.AUDIO_TITLE to audio?.title,
                F.AUDIO_DURATION to (audio?.seconds ?: 0),
                F.CREATED_AT to FieldValue.serverTimestamp(),
                F.UPDATED_AT to FieldValue.serverTimestamp(),
                F.REACTION_COUNT to 0,
                F.REACTION_COUNTS to emptyMap<String, Int>(),
                F.COMMENT_COUNT to 0,
                F.IS_PINNED to isPinned
            )
        ).await()
        return document.id
    }

    override suspend fun setPinned(postId: String, isPinned: Boolean) {
        posts.document(postId).update(mapOf(F.IS_PINNED to isPinned, F.UPDATED_AT to FieldValue.serverTimestamp())).await()
    }

    override suspend fun deletePost(postId: String) {
        // The replies and reactions underneath are left behind. Firestore has no
        // recursive delete from a client, and a post nobody can reach is not
        // worth three hundred writes to tidy.
        posts.document(postId).delete().await()
    }

    override suspend fun react(postId: String, userId: String, kind: Reaction?, previous: Reaction?) {
        if (kind == previous) return

        val document = reactions(postId).document(userId)
        if (kind == null) {
            document.delete().await()
        } else {
            document.set(mapOf(R.KIND to kind.stored, R.CREATED_AT to FieldValue.serverTimestamp())).await()
        }

        // The totals, in one update. `reactionCount` only moves when somebody
        // arrives or leaves — swapping one kind for another is not a second
        // reaction, which is why the previous kind is passed in.
        val changes = mutableMapOf<String, Any>(F.UPDATED_AT to FieldValue.serverTimestamp())
        if (previous == null && kind != null) changes[F.REACTION_COUNT] = FieldValue.increment(1)
        if (previous != null && kind == null) changes[F.REACTION_COUNT] = FieldValue.increment(-1)
        previous?.let { changes["${F.REACTION_COUNTS}.${it.stored}"] = FieldValue.increment(-1) }
        kind?.let { changes["${F.REACTION_COUNTS}.${it.stored}"] = FieldValue.increment(1) }

        // Best effort: the member's own reaction is the thing that must land.
        runCatching { posts.document(postId).update(changes).await() }
    }

    override suspend fun comment(postId: String, text: String, parentId: String?, author: User, asAuthor: Boolean): String {
        val document = comments(postId).document()
        document.set(
            buildMap {
                put(C.AUTHOR_ID, author.id)
                put(C.AUTHOR_NAME, author.fullName)
                put(C.AUTHOR_PHOTO, author.profilePhoto)
                put(C.TEXT, text.trim())
                put(C.CREATED_AT, FieldValue.serverTimestamp())
                put(C.PARENT_ID, parentId)
                put(C.REPLY_COUNT, 0)
                // Only staff may set this; the rules refuse it from anyone else.
                if (asAuthor) put("isAuthorReply", true)
            }
        ).await()

        runCatching {
            posts.document(postId).update(mapOf(F.COMMENT_COUNT to FieldValue.increment(1), F.UPDATED_AT to FieldValue.serverTimestamp())).await()
        }
        parentId?.let { runCatching { comments(postId).document(it).update(C.REPLY_COUNT, FieldValue.increment(1)).await() } }
        return document.id
    }

    override suspend fun deleteComment(postId: String, commentId: String) {
        comments(postId).document(commentId).delete().await()
        runCatching {
            posts.document(postId).update(mapOf(F.COMMENT_COUNT to FieldValue.increment(-1), F.UPDATED_AT to FieldValue.serverTimestamp())).await()
        }
    }

    // ── Mapping ──────────────────────────────────────────────────────────

    private fun map(document: DocumentSnapshot): FeedPost? {
        if (!document.exists()) return null
        val text = document.getString(F.TEXT) ?: ""
        val bodyMd = document.getString(F.BODY_MD)
        val images = (document.get(F.IMAGES) as? List<*>).orEmpty().filterIsInstance<String>().filter { it.isNotBlank() }
        val videoUrl = document.getString(F.VIDEO_URL)?.takeIf { it.isNotBlank() }
        val audioUrl = document.getString(F.AUDIO_URL)?.takeIf { it.isNotBlank() }
        val signalId = document.getString(F.SIGNAL_ID)
        if (text.isBlank() && bodyMd.isNullOrBlank() && images.isEmpty() && videoUrl == null && audioUrl == null && signalId == null) return null

        @Suppress("UNCHECKED_CAST")
        val counts = (document.get(F.REACTION_COUNTS) as? Map<String, Any?>).orEmpty()
            .mapNotNull { (key, value) -> (value as? Number)?.toInt()?.let { key to it } }
            .toMap()

        val pair = document.getString(F.PAIR)
        val pairs = (document.get(F.PAIRS) as? List<*>).orEmpty().filterIsInstance<String>().filter { it.isNotBlank() }
            .ifEmpty { listOfNotNull(pair) }

        // A post's own type when the composer set one, else read from what it carries.
        val type = PostType.from(document.getString(F.TYPE)) ?: when {
            signalId != null -> PostType.SETUP
            audioUrl != null -> PostType.VOICE
            videoUrl != null -> PostType.VIDEO
            images.isNotEmpty() -> PostType.CHART
            else -> PostType.NOTE
        }

        @Suppress("UNCHECKED_CAST")
        val link = (document.get(F.LINK) as? Map<String, Any?>)?.let { m ->
            val url = m["url"] as? String ?: return@let null
            LinkCard(url, m["site"] as? String ?: "", m["title"] as? String ?: url, m["description"] as? String, m["image"] as? String)
        }
        val chapters = (document.get(F.CHAPTERS) as? List<*>).orEmpty().mapNotNull { c ->
            val m = c as? Map<*, *> ?: return@mapNotNull null
            val at = (m["at"] as? Number)?.toInt() ?: return@mapNotNull null
            VideoChapter(at, m["label"] as? String ?: "")
        }

        return FeedPost(
            id = document.id,
            authorId = document.getString(F.AUTHOR_ID).orEmpty(),
            authorName = document.getString(F.AUTHOR_NAME) ?: "Trade With Tanha",
            authorPhoto = document.getString(F.AUTHOR_PHOTO),
            text = text,
            images = images,
            videoUrl = videoUrl,
            pair = pair ?: pairs.firstOrNull(),
            signalId = signalId,
            // A post written a moment ago has no server timestamp yet; dating it
            // now keeps it at the top of the feed instead of at the bottom.
            createdAt = document.getDate(F.CREATED_AT) ?: Date(),
            editedAt = document.getDate(F.EDITED_AT),
            reactionCount = (document.getLong(F.REACTION_COUNT) ?: 0L).toInt(),
            reactionCounts = counts,
            commentCount = (document.getLong(F.COMMENT_COUNT) ?: 0L).toInt(),
            audioUrl = audioUrl,
            audioTitle = document.getString(F.AUDIO_TITLE),
            audioDurationSec = (document.getLong(F.AUDIO_DURATION) ?: 0L).toInt(),
            isPinned = document.getBoolean(F.IS_PINNED) ?: false,
            type = type,
            bodyMd = bodyMd,
            pairs = pairs,
            commentsEnabled = document.getBoolean(F.COMMENTS_ENABLED) != false,
            imageAlts = (document.get(F.IMAGE_ALTS) as? List<*>).orEmpty().filterIsInstance<String>(),
            transcript = document.getString(F.TRANSCRIPT),
            publishAt = document.getDate(F.PUBLISH_AT),
            chapters = chapters,
            videoStart = (document.getLong(F.VIDEO_START) ?: 0L).toInt(),
            link = link,
            viewCount = (document.getLong(F.VIEW_COUNT) ?: 0L).toInt(),
            saveCount = (document.getLong(F.SAVE_COUNT) ?: 0L).toInt()
        )
    }

    private fun comment(document: DocumentSnapshot): FeedComment? {
        val text = document.getString(C.TEXT) ?: return null
        return FeedComment(
            id = document.id,
            authorId = document.getString(C.AUTHOR_ID).orEmpty(),
            authorName = document.getString(C.AUTHOR_NAME) ?: "Member",
            authorPhoto = document.getString(C.AUTHOR_PHOTO),
            text = text,
            createdAt = document.getDate(C.CREATED_AT) ?: Date(),
            parentId = document.getString(C.PARENT_ID),
            replyCount = (document.getLong(C.REPLY_COUNT) ?: 0L).toInt(),
            isAuthorReply = document.getBoolean("isAuthorReply") == true,
            audioUrl = document.getString("audioUrl")?.takeIf { it.isNotBlank() },
            audioDurationSec = (document.getLong("audioDurationSec") ?: 0L).toInt()
        )
    }
}
