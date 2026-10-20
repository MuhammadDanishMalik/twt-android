package com.talkswithtanha.twt.core.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.talkswithtanha.twt.core.firebase.FirestorePaths.Collection
import com.talkswithtanha.twt.core.firebase.FirestorePaths.FeedCommentField as C
import com.talkswithtanha.twt.core.firebase.FirestorePaths.FeedField as F
import com.talkswithtanha.twt.core.firebase.FirestorePaths.FeedReactionField as R
import com.talkswithtanha.twt.core.model.FeedComment
import com.talkswithtanha.twt.core.model.FeedPost
import com.talkswithtanha.twt.core.model.Reaction
import com.talkswithtanha.twt.core.model.Signal
import com.talkswithtanha.twt.core.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The feed: Tanha's posts, everybody's reactions and replies.
 *
 * ### Counters, and why the client keeps them
 *
 * A post carries its own `reactionCount` and `commentCount`. Counting the
 * subcollections instead would mean reading every reaction on every post to
 * draw one number on a card, which is the kind of query that is free with
 * eleven members and ruinous with eleven hundred.
 *
 * The rules let a member move those two counters by one and touch nothing else,
 * so the worst anybody can do to a total is what they could do by reacting and
 * un-reacting anyway. The words, the images and the author are out of reach.
 */
/** A voice note that has already been uploaded, on its way into a post. */
data class VoiceNote(val url: String, val title: String, val seconds: Int)

interface FeedRepository {
    fun observeFeed(limit: Int = 50): Flow<Snapshot<List<FeedPost>>>
    fun observePost(postId: String): Flow<Snapshot<FeedPost?>>

    /** Top-level replies, oldest first — the order a conversation happened in. */
    fun observeComments(postId: String): Flow<Snapshot<List<FeedComment>>>

    /** What this member has reacted with, if anything. */
    fun observeMyReaction(postId: String, userId: String): Flow<Reaction?>

    /** Whether this member has saved the post to read again. */
    fun observeMySave(postId: String, userId: String): Flow<Boolean>

    suspend fun setSaved(postId: String, userId: String, saved: Boolean)

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

    /**
     * Puts a freshly posted trade in the feed.
     *
     * Called straight after a signal is written, so the call appears where
     * members already are rather than only in the Signals tab. The text is
     * deliberately thin — the row draws the ticket, and repeating the levels in
     * a sentence above it is the sort of duplication that goes stale the moment
     * the trade moves.
     */
    suspend fun shareSignal(signal: Signal, author: User): String

    /**
     * Holds a post at the top of the feed, or lets it fall back into the day.
     *
     * For the one post that has to outlive the scroll — the week's plan, a
     * warning before an interest-rate decision. The ordering is applied when
     * the feed is read, so this is the whole feature.
     */
    suspend fun setPinned(postId: String, isPinned: Boolean)
    suspend fun deletePost(postId: String)

    /**
     * Sets, changes or clears a reaction. Passing the kind they already have
     * clears it, which is what tapping the same heart twice means.
     */
    suspend fun react(postId: String, userId: String, kind: Reaction?, previous: Reaction?)

    suspend fun comment(postId: String, text: String, parentId: String?, author: User): String
    suspend fun deleteComment(postId: String, commentId: String)
}

@Singleton
class FirebaseFeedRepository @Inject constructor(
    private val db: FirebaseFirestore
) : FeedRepository {

    private val posts get() = db.collection(Collection.FEED_POSTS)
    private fun comments(postId: String) = posts.document(postId).collection(Collection.COMMENTS)
    private fun reactions(postId: String) = posts.document(postId).collection(Collection.REACTIONS)
    private fun saves(postId: String) = posts.document(postId).collection(Collection.SAVES)

    // ── Reads ────────────────────────────────────────────────────────────

    override fun observeFeed(limit: Int): Flow<Snapshot<List<FeedPost>>> =
        posts
            .orderBy(F.CREATED_AT, Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .snapshotFlow()
            .map { snapshot ->
                when (snapshot) {
                    is Snapshot.Failed -> snapshot
                    is Snapshot.Data -> Snapshot.Data(
                        snapshot.value.documents
                            .mapNotNull { map(it) }
                            // Pinned first, then newest. Sorted here rather than
                            // in the query: Firestore would need a composite
                            // index for the pair, and the page is fifty rows.
                            .sortedWith(compareByDescending<FeedPost> { it.isPinned }.thenByDescending { it.createdAt })
                    )
                }
            }

    override fun observePost(postId: String): Flow<Snapshot<FeedPost?>> =
        posts.document(postId).snapshotFlow().map { snapshot ->
            when (snapshot) {
                is Snapshot.Failed -> snapshot
                is Snapshot.Data -> Snapshot.Data(snapshot.value?.let { map(it) })
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

    // ── Writes ───────────────────────────────────────────────────────────

    override fun observeMySave(postId: String, userId: String): Flow<Boolean> =
        saves(postId).document(userId).snapshotFlow().map { snapshot ->
            when (snapshot) {
                is Snapshot.Failed -> false
                is Snapshot.Data -> snapshot.value?.exists() == true
            }
        }

    override suspend fun setSaved(postId: String, userId: String, saved: Boolean) {
        val document = saves(postId).document(userId)
        // No counter on the post for this one: what a member keeps is theirs,
        // and a public "47 people saved this" is a number nobody asked for.
        if (saved) {
            document.set(mapOf(F.CREATED_AT to FieldValue.serverTimestamp())).await()
        } else {
            document.delete().await()
        }
    }

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
        val document = posts.document()
        document.set(
            mapOf(
                F.AUTHOR_ID to author.id,
                F.AUTHOR_NAME to author.fullName,
                F.AUTHOR_PHOTO to author.profilePhoto,
                F.TEXT to text.trim(),
                F.IMAGES to images.filter { it.isNotBlank() },
                F.VIDEO_URL to videoUrl?.trim()?.ifEmpty { null },
                F.PAIR to pair?.trim()?.uppercase()?.ifEmpty { null },
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
        posts.document(postId).update(
            mapOf(
                F.IS_PINNED to isPinned,
                F.UPDATED_AT to FieldValue.serverTimestamp()
            )
        ).await()
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
            document.set(
                mapOf(R.KIND to kind.stored, R.CREATED_AT to FieldValue.serverTimestamp())
            ).await()
        }

        // The totals, in one update. `reactionCount` only moves when somebody
        // arrives or leaves — swapping a heart for a fire is not a second
        // reaction, which is the whole reason the previous kind is passed in.
        val changes = mutableMapOf<String, Any>(F.UPDATED_AT to FieldValue.serverTimestamp())
        if (previous == null && kind != null) changes[F.REACTION_COUNT] = FieldValue.increment(1)
        if (previous != null && kind == null) changes[F.REACTION_COUNT] = FieldValue.increment(-1)
        previous?.let { changes["${F.REACTION_COUNTS}.${it.stored}"] = FieldValue.increment(-1) }
        kind?.let { changes["${F.REACTION_COUNTS}.${it.stored}"] = FieldValue.increment(1) }

        posts.document(postId).update(changes).await()
    }

    override suspend fun comment(postId: String, text: String, parentId: String?, author: User): String {
        val document = comments(postId).document()
        document.set(
            mapOf(
                C.AUTHOR_ID to author.id,
                C.AUTHOR_NAME to author.fullName,
                C.AUTHOR_PHOTO to author.profilePhoto,
                C.TEXT to text.trim(),
                C.CREATED_AT to FieldValue.serverTimestamp(),
                C.PARENT_ID to parentId,
                C.REPLY_COUNT to 0
            )
        ).await()

        posts.document(postId).update(
            mapOf(
                F.COMMENT_COUNT to FieldValue.increment(1),
                F.UPDATED_AT to FieldValue.serverTimestamp()
            )
        ).await()

        // A reply to a reply bumps its parent's count, so the thread can say
        // "3 replies" without counting them.
        parentId?.let {
            comments(postId).document(it).update(C.REPLY_COUNT, FieldValue.increment(1)).await()
        }

        return document.id
    }

    override suspend fun deleteComment(postId: String, commentId: String) {
        comments(postId).document(commentId).delete().await()
        posts.document(postId).update(
            mapOf(
                F.COMMENT_COUNT to FieldValue.increment(-1),
                F.UPDATED_AT to FieldValue.serverTimestamp()
            )
        ).await()
    }

    // ── Mapping ──────────────────────────────────────────────────────────

    private fun map(document: DocumentSnapshot): FeedPost? {
        if (!document.exists()) return null
        val text = document.getString(F.TEXT) ?: ""
        val images = (document.get(F.IMAGES) as? List<*>).orEmpty().filterIsInstance<String>()
        if (text.isBlank() && images.isEmpty() && document.getString(F.VIDEO_URL).isNullOrBlank()) return null

        @Suppress("UNCHECKED_CAST")
        val counts = (document.get(F.REACTION_COUNTS) as? Map<String, Any?>).orEmpty()
            .mapNotNull { (key, value) ->
                val number = (value as? Number)?.toInt() ?: return@mapNotNull null
                key to number
            }
            .toMap()

        return FeedPost(
            id = document.id,
            authorId = document.getString(F.AUTHOR_ID).orEmpty(),
            authorName = document.getString(F.AUTHOR_NAME) ?: "Tanha",
            authorPhoto = document.getString(F.AUTHOR_PHOTO),
            text = text,
            images = images,
            videoUrl = document.getString(F.VIDEO_URL),
            pair = document.getString(F.PAIR),
            signalId = document.getString(F.SIGNAL_ID),
            // A post written a moment ago has no server timestamp yet; dating it
            // now keeps it at the top of the feed instead of at the bottom.
            createdAt = document.getDate(F.CREATED_AT) ?: Date(),
            editedAt = document.getDate(F.EDITED_AT),
            reactionCount = (document.getLong(F.REACTION_COUNT) ?: 0L).toInt(),
            reactionCounts = counts,
            commentCount = (document.getLong(F.COMMENT_COUNT) ?: 0L).toInt(),
            audioUrl = document.getString(F.AUDIO_URL),
            audioTitle = document.getString(F.AUDIO_TITLE),
            audioDurationSec = (document.getLong(F.AUDIO_DURATION) ?: 0L).toInt(),
            isPinned = document.getBoolean(F.IS_PINNED) ?: false
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
            replyCount = (document.getLong(C.REPLY_COUNT) ?: 0L).toInt()
        )
    }
}
