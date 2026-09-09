package com.talkswithtanha.twt.core.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.talkswithtanha.twt.core.firebase.FirestorePaths.Collection
import com.talkswithtanha.twt.core.firebase.FirestorePaths.UserField as F
import com.talkswithtanha.twt.core.model.LoginProvider
import com.talkswithtanha.twt.core.model.MembershipType
import com.talkswithtanha.twt.core.model.User
import com.talkswithtanha.twt.core.model.UserRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

interface UserRepository {
    suspend fun fetchUser(uid: String): User?

    /**
     * Live view of one profile. Membership is changed from the admin panel, so
     * this is what lets a member who has just redeemed a code get in without
     * force-quitting — and what closes the app again when a code is revoked.
     */
    fun observeUser(uid: String): Flow<Snapshot<User?>>

    suspend fun createUserIfNeeded(
        uid: String,
        email: String,
        fullName: String?,
        photoUrl: String?,
        provider: LoginProvider,
        country: String?
    ): User

    suspend fun updateProfile(
        uid: String,
        fullName: String? = null,
        phone: String? = null,
        profilePhoto: String? = null,
        country: String? = null,
        bio: String? = null
    )

    suspend fun registerPushToken(uid: String, token: String)

    suspend fun claimDeviceSession(
        uid: String,
        sessionId: String,
        deviceId: String,
        deviceModel: String
    )
}

/**
 * The only file that knows how a [User] is shaped in Firestore.
 *
 * The mapping is written by hand rather than handed to Firestore's POJO
 * serializer. That is more lines, but it buys two things this project
 * specifically needs: the field names are literal strings that can be grepped
 * against the iOS `FirestorePaths.swift` and the admin panel's TypeScript, and a
 * document with one bad field degrades to a sensible default instead of throwing
 * away the whole user.
 */
@Singleton
class FirebaseUserRepository @Inject constructor(
    private val db: FirebaseFirestore
) : UserRepository {

    private val users get() = db.collection(Collection.USERS)

    override suspend fun fetchUser(uid: String): User? {
        val snapshot = users.document(uid).get().await()
        return if (snapshot.exists()) map(snapshot, uid) else null
    }

    override fun observeUser(uid: String): Flow<Snapshot<User?>> =
        users.document(uid).snapshotFlow().map { snapshot ->
            when (snapshot) {
                is Snapshot.Failed -> snapshot
                is Snapshot.Data -> Snapshot.Data(
                    snapshot.value?.takeIf { it.exists() }?.let { map(it, uid) }
                )
            }
        }

    override suspend fun createUserIfNeeded(
        uid: String,
        email: String,
        fullName: String?,
        photoUrl: String?,
        provider: LoginProvider,
        country: String?
    ): User {
        val document = users.document(uid)
        val existing = fetchUser(uid)

        if (existing != null) {
            // The account is already known. Refresh only what the auth provider
            // is authoritative about, and leave membership and role alone — this
            // path runs on every sign-in, and writing defaults here would demote
            // a member with a live code back to no access.
            //
            // Deliberately non-throwing, and that is the important part rather
            // than a shortcut. On iOS this threw on every *returning* sign-in:
            // the rules pin a member to a fixed set of self-writable keys, and
            // `email` was not one of them, so the write was refused, the error
            // propagated out of session setup, and somebody who had just
            // authenticated successfully was sent back to the login screen. A
            // first sign-up never hit it, because that path creates the document
            // rather than updating it — which is what made sign-in look broken
            // and sign-up look fine.
            //
            // The rules now allow `email`, so this normally succeeds. It stays
            // non-throwing anyway: refreshing a cosmetic field must never be the
            // reason somebody cannot get into the app, whatever rules happen to
            // be deployed against this build.
            val refresh = mutableMapOf<String, Any>(
                F.EMAIL to email,
                F.UPDATED_AT to FieldValue.serverTimestamp()
            )
            // Backfill only. Someone who signed up before the country field
            // existed gets one on their next sign-in, but a country they chose
            // themselves is never overwritten by a device guess.
            if (existing.country.isNullOrBlank() && !country.isNullOrBlank()) {
                refresh[F.COUNTRY] = country.uppercase()
            }
            runCatching { document.update(refresh).await() }
            return existing
        }

        val newUser = User.newAccount(
            id = uid,
            fullName = fullName?.takeIf { it.isNotBlank() } ?: User.fallbackName(email),
            email = email,
            profilePhoto = photoUrl,
            country = country?.uppercase(),
            loginProvider = provider
        )

        val payload = payload(newUser).toMutableMap()
        // Server time, not device time. A phone with a wrong clock would
        // otherwise sort itself to the top or bottom of every admin list.
        payload[F.CREATED_AT] = FieldValue.serverTimestamp()
        payload[F.UPDATED_AT] = FieldValue.serverTimestamp()

        document.set(payload).await()
        return newUser
    }

    override suspend fun updateProfile(
        uid: String,
        fullName: String?,
        phone: String?,
        profilePhoto: String?,
        country: String?,
        bio: String?
    ) {
        val payload = mutableMapOf<String, Any>(
            F.UPDATED_AT to FieldValue.serverTimestamp()
        )
        // Only fields that were actually supplied are sent. Null means "leave
        // alone", not "clear" — passing everything through would wipe a phone
        // number every time somebody edited their name.
        fullName?.let { payload[F.FULL_NAME] = it }
        phone?.let { payload[F.PHONE] = it }
        profilePhoto?.let { payload[F.PROFILE_PHOTO] = it }
        country?.let { payload[F.COUNTRY] = it.uppercase() }
        // Empty string rather than a skip, so clearing a bio actually clears it.
        // The caller distinguishes "not editing this" (null) from "make it
        // blank" (""), which a screen with a text field genuinely needs.
        bio?.let { payload[F.BIO] = it }

        users.document(uid).update(payload).await()
    }

    override suspend fun registerPushToken(uid: String, token: String) {
        // arrayUnion rather than a read-modify-write: one phone can hold two
        // accounts, and two devices registering at once would otherwise each
        // overwrite the other's token.
        users.document(uid)
            .update(F.FCM_TOKENS, FieldValue.arrayUnion(token))
            .await()
    }

    /**
     * Writes this device's claim on the account.
     *
     * All five keys go in one update, and every one of them must be in the
     * rules' self-writable list. An update is judged **as a whole** — iOS
     * shipped a version of this that included `lastActiveDeviceModel` and
     * `lastActivePlatform` before they were allowed, and the entire write was
     * rejected, so the device could never claim the account at all.
     *
     * The assertion below is not defensive padding: it turns that failure into a
     * loud crash in development rather than a silent permission denial that
     * looks like a network problem in production.
     */
    override suspend fun claimDeviceSession(
        uid: String,
        sessionId: String,
        deviceId: String,
        deviceModel: String
    ) {
        val payload = mapOf(
            F.CURRENT_SESSION_ID to sessionId,
            F.LAST_ACTIVE_DEVICE_ID to deviceId,
            F.LAST_ACTIVE_DEVICE_MODEL to deviceModel,
            F.LAST_ACTIVE_PLATFORM to PLATFORM,
            F.LAST_LOGIN_AT to FieldValue.serverTimestamp(),
            F.UPDATED_AT to FieldValue.serverTimestamp()
        )
        require(F.SELF_WRITABLE.containsAll(payload.keys)) {
            "claimDeviceSession would be rejected whole: " +
                "${payload.keys - F.SELF_WRITABLE} are not self-writable"
        }
        users.document(uid).update(payload).await()
    }

    private fun payload(user: User): Map<String, Any> = buildMap {
        put(F.UID, user.id)
        put(F.FULL_NAME, user.fullName)
        put(F.EMAIL, user.email)
        put(F.LOGIN_PROVIDER, user.loginProvider.stored)
        put(F.MEMBERSHIP_TYPE, user.membershipType.stored)
        put(F.ROLE, user.role.stored)
        put(F.IS_BLOCKED, user.isBlocked)
        // Optionals are omitted rather than written as null, so a document never
        // carries fields that only mean "nothing here".
        user.profilePhoto?.let { put(F.PROFILE_PHOTO, it) }
        user.phone?.let { put(F.PHONE, it) }
        user.bio?.let { put(F.BIO, it) }
        user.country?.let { put(F.COUNTRY, it.uppercase()) }
        user.membershipExpiresAt?.let { put(F.MEMBERSHIP_EXPIRES_AT, Timestamp(it)) }
        user.accessCode?.let { put(F.ACCESS_CODE, it) }
        user.accessGrantedAt?.let { put(F.ACCESS_GRANTED_AT, Timestamp(it)) }
    }

    private fun map(snapshot: DocumentSnapshot, uid: String): User {
        val email = snapshot.getString(F.EMAIL).orEmpty()
        @Suppress("UNCHECKED_CAST")
        val tokens = (snapshot.get(F.FCM_TOKENS) as? List<*>)
            ?.filterIsInstance<String>()
            .orEmpty()

        return User(
            id = uid,
            fullName = snapshot.getString(F.FULL_NAME)?.takeIf { it.isNotBlank() }
                ?: User.fallbackName(email),
            email = email,
            profilePhoto = snapshot.get(F.PROFILE_PHOTO).asNonBlankString(),
            phone = snapshot.get(F.PHONE).asNonBlankString(),
            bio = snapshot.get(F.BIO).asNonBlankString(),
            country = snapshot.get(F.COUNTRY).asNonBlankString(),
            loginProvider = LoginProvider.from(snapshot.getString(F.LOGIN_PROVIDER)),
            membershipType = MembershipType.from(snapshot.getString(F.MEMBERSHIP_TYPE)),
            membershipExpiresAt = snapshot.getTimestamp(F.MEMBERSHIP_EXPIRES_AT)?.toDate(),
            accessCode = snapshot.get(F.ACCESS_CODE).asNonBlankString(),
            accessGrantedAt = snapshot.getTimestamp(F.ACCESS_GRANTED_AT)?.toDate(),
            role = UserRole.from(snapshot.getString(F.ROLE)),
            isBlocked = snapshot.getBoolean(F.IS_BLOCKED) ?: false,
            referralCode = snapshot.get(F.REFERRAL_CODE).asNonBlankString(),
            fcmTokens = tokens,
            currentSessionId = snapshot.get(F.CURRENT_SESSION_ID).asNonBlankString(),
            lastActiveDeviceId = snapshot.get(F.LAST_ACTIVE_DEVICE_ID).asNonBlankString(),
            lastActiveDeviceModel = snapshot.get(F.LAST_ACTIVE_DEVICE_MODEL).asNonBlankString(),
            lastActivePlatform = snapshot.get(F.LAST_ACTIVE_PLATFORM).asNonBlankString(),
            lastLoginAt = snapshot.getTimestamp(F.LAST_LOGIN_AT)?.toDate(),
            createdAt = snapshot.getTimestamp(F.CREATED_AT)?.toDate() ?: Date(),
            updatedAt = snapshot.getTimestamp(F.UPDATED_AT)?.toDate() ?: Date()
        )
    }

    companion object {
        /** What this client writes into `lastActivePlatform`. iOS writes "iOS";
         *  the admin panel shows whichever it finds. */
        const val PLATFORM = "Android"
    }
}
