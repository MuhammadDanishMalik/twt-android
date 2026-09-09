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
            .orderBy(F.ORDER, Query.Direction.ASCENDING)
            .snapshotFlow()
            .map { snapshot ->
                when (snapshot) {
                    is Snapshot.Failed -> snapshot
                    is Snapshot.Data -> Snapshot.Data(
                        snapshot.value.documents.mapNotNull { document ->
                            // A lesson with no video id is a draft row in the
                            // admin panel, not something to put on a screen.
                            val youtubeId = document.get(F.YOUTUBE_ID).asNonBlankString()
                                ?: return@mapNotNull null
                            // Unpublished lessons stay out of the app, same as
                            // unpublished signals. Filtered here rather than in
                            // the query so this needs no composite index --
                            // the collection is small enough that it does not
                            // matter, and one fewer index is one fewer thing
                            // that has to be deployed before the app works.
                            if (document.getBoolean(F.IS_PUBLISHED) == false) return@mapNotNull null

                            AcademyVideo(
                                id = document.id,
                                title = document.get(F.TITLE).asNonBlankString() ?: "Untitled lesson",
                                description = document.get(F.DESCRIPTION).asNonBlankString(),
                                youtubeId = youtubeId,
                                category = document.get(F.CATEGORY).asNonBlankString(),
                                level = document.get(F.LEVEL).asNonBlankString(),
                                durationSeconds = document.get(F.DURATION_SECONDS)
                                    .asLongOrNull()?.toInt(),
                                order = document.get(F.ORDER).asLongOrNull()?.toInt() ?: 0,
                                createdAt = document.getTimestamp(F.CREATED_AT)?.toDate() ?: Date()
                            )
                        }
                    )
                }
            }
}
