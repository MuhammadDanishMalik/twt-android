package com.talkswithtanha.twt.core.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.talkswithtanha.twt.core.firebase.FirestorePaths.AccessTokenField as T
import com.talkswithtanha.twt.core.firebase.FirestorePaths.Collection
import com.talkswithtanha.twt.core.firebase.FirestorePaths.UserField as U
import com.talkswithtanha.twt.core.model.AccessCode
import com.talkswithtanha.twt.core.model.AccessGrant
import com.talkswithtanha.twt.core.model.AccessRedemptionError
import com.talkswithtanha.twt.core.model.AccessRedemptionException
import com.talkswithtanha.twt.core.model.MembershipType
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

interface AccessTokenRepository {
    /**
     * Exchanges a typed code for access on this account.
     *
     * Throws [AccessRedemptionException] for every outcome a member can be told
     * about, so the screen never has to interpret a Firestore error.
     */
    suspend fun redeem(code: String, uid: String, email: String): AccessGrant
}

/**
 * Redeeming a code is two writes that must both happen or neither: the token is
 * stamped with the uid that claimed it, and the member's profile is granted
 * access until the token's end date. A `WriteBatch` is what makes that one
 * operation — without it, a network drop between the two leaves either a token
 * burned on an account that never got in, or an account with access and a code
 * still free for somebody else to use.
 *
 * ### Why the client is allowed to write its own access at all
 *
 * It looks like the hole this whole feature exists to close. It is not, and the
 * reason is entirely in firestore.rules: the profile update is accepted *only*
 * when the rules can `get()` the token document and see that it is active,
 * unexpired, unclaimed-or-claimed-by-this-same-uid, and that the expiry being
 * written equals the token's own expiry **to the second**. A tampered client can
 * send whatever it likes; without a real unclaimed code in its hand, every one
 * of those writes is rejected server-side.
 *
 * The alternative — a Cloud Function — is the textbook answer and needs the
 * Blaze plan, a deploy pipeline and a cold start in front of the app's very
 * first screen. The rules do the same job here because the check is a lookup,
 * not a computation.
 */
@Singleton
class FirebaseAccessTokenRepository @Inject constructor(
    private val db: FirebaseFirestore
) : AccessTokenRepository {

    override suspend fun redeem(code: String, uid: String, email: String): AccessGrant {
        val normalised = AccessCode.normalise(code)
        if (!AccessCode.isWellFormed(normalised)) {
            throw AccessRedemptionException(AccessRedemptionError.Malformed)
        }

        val tokenRef = db.collection(Collection.ACCESS_TOKENS).document(normalised)

        // `Source.SERVER`, not the default.
        //
        // The default would answer out of the local cache when one is warm, and
        // the one thing that must not be read from a cache is whether a code has
        // already been claimed. A stale "unclaimed" here sends the batch anyway;
        // the rules would refuse it, but the member gets "already in use" for a
        // code that is genuinely theirs on a slow connection.
        //
        // The collection allows `get` and never `list`, so this is a keyed
        // lookup by document id. A query here would come back permission-denied
        // by design.
        val snapshot = try {
            tokenRef.get(Source.SERVER).await()
        } catch (e: FirebaseFirestoreException) {
            throw AccessRedemptionException(mapped(e))
        }

        // No document means no such code. iOS has a fallback branch here that
        // tries to grant access anyway for "joining codes" that predate the
        // token collection; it is dead code, because the rules require
        // `exists(tokenPath(...))` and refuse that write every time. It is not
        // ported — the observable behaviour is identical and this way the app
        // does not fire a write it knows will be denied.
        if (!snapshot.exists()) {
            throw AccessRedemptionException(AccessRedemptionError.NotFound)
        }

        // Checked client-side so the member gets a sentence they can act on. The
        // rules check all of it again server-side; this is for the message, not
        // for the security.
        if (snapshot.getBoolean(T.IS_ACTIVE) != true) {
            throw AccessRedemptionException(AccessRedemptionError.Revoked)
        }

        val expiresAt = snapshot.getTimestamp(T.EXPIRES_AT)
        if (expiresAt != null && !expiresAt.toDate().after(Date())) {
            throw AccessRedemptionException(AccessRedemptionError.Expired)
        }

        val claimedBy = snapshot.get(T.CLAIMED_BY).asNonBlankString()
        if (claimedBy != null && claimedBy != uid) {
            throw AccessRedemptionException(AccessRedemptionError.AlreadyClaimed)
        }

        val batch = db.batch()

        // The member may stamp their own uid and nothing else. Re-claiming a
        // code already claimed by this same account is allowed on purpose — a
        // reinstall or a second device must not need a support message — so
        // `claimedAt` is left alone when it is already set rather than being
        // pushed forward to now.
        val tokenUpdate = mutableMapOf<String, Any>(
            T.CLAIMED_BY to uid,
            T.CLAIMED_BY_EMAIL to email,
            T.UPDATED_AT to FieldValue.serverTimestamp()
        )
        if (claimedBy == null) {
            tokenUpdate[T.CLAIMED_AT] = FieldValue.serverTimestamp()
        }
        batch.update(tokenRef, tokenUpdate)

        // The expiry is **copied verbatim**, not recomputed.
        //
        // The rule requires the value written here to equal the token's own
        // `expiresAt` exactly. Deriving it — "now plus thirty days", or even
        // re-reading the same Timestamp and rebuilding it from millis — produces
        // a value that differs by the seconds the round trip took, and the whole
        // write is rejected. The Timestamp object that came off the token
        // document is passed straight through.
        //
        // A null expiry means "never expires", and null is what has to be
        // written: the rule compares against `t.get('expiresAt', null)`, so
        // omitting the key would leave the old value in place and fail the
        // comparison.
        // A literal null rather than a field delete, matching what iOS writes.
        // Both satisfy the rule -- it reads `get('membershipExpiresAt', null)`,
        // which treats absent and null alike -- but every never-expiring account
        // already in the database carries an explicit null, written by iOS, and
        // there is no reason for the two clients to leave differently shaped
        // documents behind.
        val userUpdate = mapOf<String, Any?>(
            U.ACCESS_CODE to normalised,
            U.ACCESS_GRANTED_AT to FieldValue.serverTimestamp(),
            U.MEMBERSHIP_TYPE to MembershipType.PREMIUM.stored,
            U.MEMBERSHIP_EXPIRES_AT to expiresAt,
            U.UPDATED_AT to FieldValue.serverTimestamp()
        )
        require(U.REDEMPTION_KEYS.containsAll(userUpdate.keys)) {
            "redemption would be rejected whole: " +
                "${userUpdate.keys - U.REDEMPTION_KEYS} are outside the redemption rule"
        }
        @Suppress("UNCHECKED_CAST")
        batch.update(
            db.collection(Collection.USERS).document(uid),
            userUpdate as Map<String, Any>
        )

        try {
            batch.commit().await()
        } catch (e: FirebaseFirestoreException) {
            throw AccessRedemptionException(mapped(e))
        }

        return AccessGrant(
            expiresAt = expiresAt?.toDate(),
            label = snapshot.get(T.LABEL).asNonBlankString()
        )
    }

    /**
     * Turns a Firestore error into something a member can read.
     *
     * `PERMISSION_DENIED` is the interesting one. By the time the batch runs the
     * client has already checked the token itself, so a denial means the state
     * changed underneath us — almost always somebody else redeeming the same
     * code in the seconds between the read and the write.
     */
    private fun mapped(e: FirebaseFirestoreException): AccessRedemptionError =
        when (e.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                AccessRedemptionError.AlreadyClaimed
            FirebaseFirestoreException.Code.NOT_FOUND ->
                AccessRedemptionError.NotFound
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                AccessRedemptionError.Offline
            else -> AccessRedemptionError.Unknown(e.localizedMessage ?: "Something went wrong.")
        }
}
