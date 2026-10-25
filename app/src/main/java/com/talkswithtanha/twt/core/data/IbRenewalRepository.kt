package com.talkswithtanha.twt.core.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/** One request to renew an IB member's month with a screenshot of their IB analytics. */
data class IbRenewal(
    val id: String,
    val status: Status,
    val reviewNote: String?,
    val createdAt: Date?,
    val extendedTo: Date?
) {
    enum class Status { PENDING, APPROVED, DECLINED }
}

/**
 * `ibRenewals`: an IB member's month renews by showing the analytics screen
 * from their broker's IB portal, which the team checks in the panel's
 * Verification tab and accepts (the month is extended) or declines.
 *
 * The screenshot goes to `paymentProofs/{uid}/`, the private folder receipts
 * already use: an IB report carries account numbers and balances, so only the
 * member and staff can read it — see storage.rules.
 */
@Singleton
class IbRenewalRepository @Inject constructor(
    private val db: FirebaseFirestore
) {
    private val renewals get() = db.collection("ibRenewals")

    /** Their most recent request, live — the screen shows its state. */
    fun observeLatest(uid: String): Flow<IbRenewal?> =
        renewals
            .whereEqualTo("userId", uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(1)
            .snapshotFlow()
            .map { snap ->
                val doc = (snap as? Snapshot.Data)?.value?.documents?.firstOrNull() ?: return@map null
                IbRenewal(
                    id = doc.id,
                    status = when (doc.getString("status")) {
                        "approved" -> IbRenewal.Status.APPROVED
                        "declined" -> IbRenewal.Status.DECLINED
                        else -> IbRenewal.Status.PENDING
                    },
                    reviewNote = doc.getString("reviewNote")?.takeIf { it.isNotBlank() },
                    createdAt = doc.getTimestamp("createdAt")?.toDate(),
                    extendedTo = doc.getTimestamp("extendedTo")?.toDate()
                )
            }

    /** Uploads the screenshot privately, then files the request. */
    suspend fun submit(
        uid: String,
        name: String,
        email: String,
        expiresAt: Date?,
        jpeg: ByteArray
    ) {
        val ref = FirebaseStorage.getInstance().reference
            .child("paymentProofs/$uid/ib-renewal-${System.currentTimeMillis()}.jpg")
        ref.putBytes(jpeg, StorageMetadata.Builder().setContentType("image/jpeg").build()).await()
        val url = ref.downloadUrl.await().toString()

        renewals.add(
            buildMap {
                put("userId", uid)
                put("userName", name)
                put("userEmail", email)
                put("screenshotUrl", url)
                put("status", "pending")
                expiresAt?.let { put("membershipExpiresAt", com.google.firebase.Timestamp(it)) }
                put("createdAt", FieldValue.serverTimestamp())
            }
        ).await()
    }
}
