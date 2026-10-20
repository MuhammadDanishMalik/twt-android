package com.talkswithtanha.twt.core.moderation

import android.content.Context
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** Why a message was reported. [stored] is what the admin panel reads. */
enum class ReportReason(val stored: String, val title: String) {
    SPAM("spam", "Spam or advertising"),
    HARASSMENT("harassment", "Harassment or hate"),
    SCAM("scam", "Scam or fraud"),
    INAPPROPRIATE("inappropriate", "Inappropriate content"),
    OTHER("other", "Something else")
}

/**
 * Members this person has blocked, on this phone.
 *
 * Blocking hides a member's messages from the blocker — it is a personal
 * filter, not a sanction, so it is local and needs no server write. Sanctions
 * are the team's job, from reports. Names are kept beside the ids so the
 * Blocked members list can show who is who without reading the private users
 * collection.
 */
@Singleton
class BlockedUsersStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("blocked_users", Context.MODE_PRIVATE)

    private val _blocked = MutableStateFlow(load())
    /** uid → display name. */
    val blocked: StateFlow<Map<String, String>> = _blocked.asStateFlow()

    fun block(uid: String, name: String) {
        if (uid.isBlank()) return
        _blocked.update { it + (uid to name) }
        save()
    }

    fun unblock(uid: String) {
        _blocked.update { it - uid }
        save()
    }

    private fun save() {
        val json = JSONObject()
        _blocked.value.forEach { (uid, name) -> json.put(uid, name) }
        prefs.edit().putString(KEY, json.toString()).apply()
    }

    private fun load(): Map<String, String> = runCatching {
        val json = JSONObject(prefs.getString(KEY, "{}")!!)
        json.keys().asSequence().associateWith { json.getString(it) }
    }.getOrDefault(emptyMap())

    private companion object {
        const val KEY = "blocked"
    }
}

/**
 * Files a report on a chat message into `reports`, where the team reviews it.
 *
 * Members may only create reports, never read them — the security rules keep
 * the collection staff-only — and the message text is copied in, so a report
 * still says what was wrong after the author edits or deletes the message.
 */
@Singleton
class ReportRepository @Inject constructor(
    private val db: FirebaseFirestore
) {
    suspend fun reportMessage(
        reporterId: String,
        roomId: String,
        messageId: String,
        reportedUserId: String,
        reportedUserName: String,
        messageText: String,
        reason: ReportReason
    ) {
        db.collection(COLLECTION).add(
            mapOf(
                "type" to "message",
                "reporterId" to reporterId,
                "roomId" to roomId,
                "messageId" to messageId,
                "reportedUserId" to reportedUserId,
                "reportedUserName" to reportedUserName.take(80),
                "messageText" to messageText.take(500),
                "reason" to reason.stored,
                "status" to "open",
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    private companion object {
        const val COLLECTION = "reports"
    }
}
