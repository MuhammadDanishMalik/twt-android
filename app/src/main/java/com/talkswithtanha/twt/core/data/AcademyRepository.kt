package com.talkswithtanha.twt.core.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.talkswithtanha.twt.core.firebase.FirestorePaths.AcademyVideoField as F
import com.talkswithtanha.twt.core.firebase.FirestorePaths.Collection
import com.talkswithtanha.twt.core.model.AcademyVideo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

interface AcademyRepository {
    fun observeVideos(): Flow<Snapshot<List<AcademyVideo>>>
}

@Singleton
class FirebaseAcademyRepository @Inject constructor(
    private val db: FirebaseFirestore
) : AcademyRepository {

    override fun observeVideos(): Flow<Snapshot<List<AcademyVideo>>> =
        db.collection(Collection.ACADEMY_VIDEOS)
            // Drafts stay out of the app, same rule as signals: a lesson being
            // written should not appear half-finished in someone's course list.
            //
            // Filters on one field and orders by another, so this needs the
            // composite index declared in `firestore.indexes.json`. The emulator
            // does not enforce composite indexes, so it passes locally and fails
            // live without it.
            .whereEqualTo(F.IS_PUBLISHED, true)
            .orderBy(F.CREATED_AT, Query.Direction.DESCENDING)
            .snapshotFlow()
            .map { snapshot ->
                when (snapshot) {
                    is Snapshot.Failed -> snapshot
                    is Snapshot.Data -> Snapshot.Data(
                        snapshot.value.documents.mapNotNull { document ->
                            // Without a video id there is nothing to play, so
                            // the row is worthless — better absent than a card
                            // that does nothing when tapped.
                            val videoId = document.get(F.VIDEO_ID).asNonBlankString()
                                ?: return@mapNotNull null
                            val title = document.get(F.TITLE).asNonBlankString()
                                ?: return@mapNotNull null

                            AcademyVideo(
                                id = document.id,
                                title = title,
                                description = document.get(F.DESCRIPTION).asNonBlankString(),
                                youtubeId = videoId,
                                category = document.get(F.CATEGORY).asNonBlankString(),
                                level = document.get(F.LEVEL).asNonBlankString(),
                                duration = document.get(F.DURATION).asLongOrNull()?.toInt() ?: 0,
                                xpReward = document.get(F.XP_REWARD).asLongOrNull()?.toInt() ?: 0,
                                createdAt = document.getTimestamp(F.CREATED_AT)?.toDate() ?: Date()
                            )
                        }
                    )
                }
            }
}
