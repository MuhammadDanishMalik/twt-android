package com.talkswithtanha.twt.core.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** A home-screen banner, managed on the admin panel's Promotional Banners page. */
data class Banner(
    val id: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String,
    val action: Action,
    /** Set only when [action] is [Action.URL]. */
    val url: String,
    val order: Int
) {
    /** Where a tap goes. Stored as the lowercase name, as the admin panel writes it. */
    enum class Action {
        NONE, ACADEMY, SIGNALS, CHAT, URL;

        companion object {
            fun from(value: String?) = entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: NONE
        }
    }
}

/**
 * The live banners, and counting taps on them.
 *
 * Filtered on `isActive` and sorted here rather than in the query, so no
 * composite index is needed — there are only ever a handful.
 */
@Singleton
class BannerRepository @Inject constructor(
    private val db: FirebaseFirestore
) {
    fun observeActive(): Flow<List<Banner>> =
        db.collection(COLLECTION)
            .whereEqualTo("isActive", true)
            .snapshotFlow()
            .map { snapshot ->
                when (snapshot) {
                    is Snapshot.Failed -> emptyList()
                    is Snapshot.Data -> snapshot.value.documents.mapNotNull { d ->
                        val image = d.getString("imageUrl")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        Banner(
                            id = d.id,
                            title = d.getString("title").orEmpty(),
                            subtitle = d.getString("subtitle").orEmpty(),
                            imageUrl = image,
                            action = Banner.Action.from(d.getString("action")),
                            url = d.getString("url").orEmpty(),
                            order = d.getLong("order")?.toInt() ?: 0
                        )
                    }.sortedBy { it.order }
                }
            }

    /** +1 to the banner's tap count — the one write members may make to it. */
    suspend fun recordTap(bannerId: String) {
        runCatching {
            db.collection(COLLECTION).document(bannerId)
                .update("clicks", FieldValue.increment(1))
                .await()
        }
    }

    private companion object {
        const val COLLECTION = "banners"
    }
}
