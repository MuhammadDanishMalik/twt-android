package com.talkswithtanha.twt.core.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.talkswithtanha.twt.core.firebase.FirestorePaths.Collection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** The deal alerts a member can switch off, per channel. Keys match the panel's server. */
enum class DealAlert(val key: String, val label: String, val hint: String) {
    CREATED("created", "Deal opened", "Where to pay and your deadline"),
    APPROVED("approved", "Payment confirmed", "When to add your receiving account"),
    COMPLETED("completed", "Funds sent", "When the desk has paid you"),
    CANCELLED("cancelled", "Deal cancelled", "Including when it was not paid in time")
}

data class NotificationPrefs(
    val antiPhishingCode: String = "",
    val email: Map<String, Boolean> = emptyMap(),
    val push: Map<String, Boolean> = emptyMap(),
    /** Sounds and the two-minute buzz on deal screens. */
    val sound: Boolean = true
) {
    /** A missing choice means on. */
    fun emailOn(alert: DealAlert) = email[alert.key] != false
    fun pushOn(alert: DealAlert) = push[alert.key] != false

    companion object {
        private val CODE = Regex("^[A-Za-z0-9]{4,20}$")
        fun validCode(code: String) = CODE.matches(code)
    }
}

/**
 * `users/{uid}/prefs/notifications`: the anti-phishing code and which deal
 * alerts arrive by email and by push. The same document the website's
 * settings write, and the one the panel's server reads before sending
 * anything. Appeals and security mail ignore it — decided on the server.
 */
@Singleton
class PrefsRepository @Inject constructor(
    private val db: FirebaseFirestore
) {
    private fun ref(uid: String) =
        db.collection(Collection.USERS).document(uid).collection("prefs").document("notifications")

    fun observe(uid: String): Flow<NotificationPrefs> =
        ref(uid).snapshotFlow().map { snap ->
            val doc = (snap as? Snapshot.Data)?.value
            if (doc == null || !doc.exists()) NotificationPrefs()
            else NotificationPrefs(
                antiPhishingCode = doc.getString("antiPhishingCode").orEmpty(),
                email = flags(doc.get("email")),
                push = flags(doc.get("push")),
                sound = doc.getBoolean("sound") != false
            )
        }

    suspend fun save(uid: String, prefs: NotificationPrefs) {
        val data = mutableMapOf<String, Any>(
            "email" to prefs.email,
            "push" to prefs.push,
            "sound" to prefs.sound,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (prefs.antiPhishingCode.isNotBlank()) data["antiPhishingCode"] = prefs.antiPhishingCode
        ref(uid).set(data).await()
    }

    private fun flags(raw: Any?): Map<String, Boolean> =
        (raw as? Map<*, *>).orEmpty().mapNotNull { (k, v) ->
            val key = k as? String ?: return@mapNotNull null
            val on = v as? Boolean ?: return@mapNotNull null
            key to on
        }.toMap()
}
