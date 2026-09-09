package com.talkswithtanha.twt.core.session

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import com.talkswithtanha.twt.core.model.LoginProvider
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** What a completed sign-in tells us about the person. */
data class AuthResult(
    val uid: String,
    val email: String,
    val fullName: String?,
    val photoUrl: String?,
    val provider: LoginProvider
)

/**
 * One case per thing a member can actually be told.
 *
 * Firebase returns the same "invalid credential" for an unknown address as for a
 * wrong password — it refuses to confirm whether an account exists, which is
 * correct of it and unhelpful to a member who has genuinely mistyped their
 * email. [InvalidCredentials] says both possibilities out loud rather than
 * insisting the password is wrong.
 */
sealed class AuthError(val message: String) {
    data object InvalidCredentials : AuthError(
        "That email and password do not match an account. Check both, or create an account."
    )
    data object EmailInUse : AuthError(
        "There is already an account with that email. Sign in instead."
    )
    data object WeakPassword : AuthError(
        "That password is too short. Use at least six characters."
    )
    data object MalformedEmail : AuthError("That does not look like an email address.")
    data object Offline : AuthError("No connection. Check your internet and try again.")
    data object Cancelled : AuthError("Sign-in cancelled.")
    class Unknown(detail: String) : AuthError(detail)
}

class AuthException(val reason: AuthError) : Exception(reason.message)

/**
 * Firebase Auth, wrapped so nothing above this layer imports Firebase.
 *
 * Sign in with Apple is deliberately absent. It exists on iOS because Apple
 * requires it of any app offering third-party sign-in; on Android it would mean
 * a web redirect flow for a provider none of these members use.
 */
@Singleton
class AuthService @Inject constructor(
    private val auth: FirebaseAuth
) {

    val currentUid: String? get() = auth.currentUser?.uid

    /**
     * Emits the uid on every auth state change, and once immediately with the
     * restored session — which is what makes a returning member skip the login
     * screen.
     */
    fun observeAuthState(onChange: (String?) -> Unit): AutoCloseable {
        val listener = FirebaseAuth.AuthStateListener { onChange(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        return AutoCloseable { auth.removeAuthStateListener(listener) }
    }

    suspend fun signIn(email: String, password: String): AuthResult = wrap {
        val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
        result.user!!.toAuthResult(LoginProvider.EMAIL)
    }

    suspend fun signUp(email: String, password: String, fullName: String): AuthResult = wrap {
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val user = result.user!!
        // Set the display name on the Auth record too, not just the Firestore
        // profile. Anything that reads the token — a Cloud Function later, the
        // Firebase console now — sees a name rather than a bare uid.
        if (fullName.isNotBlank()) {
            user.updateProfile(userProfileChangeRequest { displayName = fullName }).await()
        }
        user.toAuthResult(LoginProvider.EMAIL).copy(fullName = fullName.ifBlank { null })
    }

    /** [idToken] comes from Credential Manager — see `GoogleSignInClient`. */
    suspend fun signInWithGoogle(idToken: String): AuthResult = wrap {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = auth.signInWithCredential(credential).await()
        result.user!!.toAuthResult(LoginProvider.GOOGLE)
    }

    suspend fun sendPasswordReset(email: String) = wrap {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    fun signOut() = auth.signOut()

    /**
     * Deletes the Auth account.
     *
     * The Firestore profile is deliberately *not* deleted, and neither is the
     * access token released. A member's history is evidence, and a client-side
     * cascade over chat messages and deals would half-finish the first time the
     * network dropped. Removing the Auth account is what actually revokes
     * access; the records are Tanha's to purge from the admin panel, and
     * releasing a code so it can be redeemed again is his decision, not the
     * app's.
     */
    suspend fun deleteAccount() = wrap {
        auth.currentUser?.delete()?.await() ?: Unit
    }

    private fun com.google.firebase.auth.FirebaseUser.toAuthResult(
        provider: LoginProvider
    ) = AuthResult(
        uid = uid,
        email = email.orEmpty(),
        fullName = displayName?.takeIf { it.isNotBlank() },
        photoUrl = photoUrl?.toString(),
        provider = provider
    )

    private inline fun <T> wrap(block: () -> T): T = try {
        block()
    } catch (e: FirebaseAuthWeakPasswordException) {
        throw AuthException(AuthError.WeakPassword)
    } catch (e: FirebaseAuthUserCollisionException) {
        throw AuthException(AuthError.EmailInUse)
    } catch (e: FirebaseAuthInvalidUserException) {
        throw AuthException(AuthError.InvalidCredentials)
    } catch (e: FirebaseAuthInvalidCredentialsException) {
        throw AuthException(
            if (e.errorCode == "ERROR_INVALID_EMAIL") AuthError.MalformedEmail
            else AuthError.InvalidCredentials
        )
    } catch (e: java.io.IOException) {
        throw AuthException(AuthError.Offline)
    } catch (e: AuthException) {
        throw e
    } catch (e: Exception) {
        throw AuthException(AuthError.Unknown(e.localizedMessage ?: "Something went wrong."))
    }
}
