package com.talkswithtanha.twt.core.data

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Firestore snapshot listeners as Flows.
 *
 * The iOS equivalent of this is `AnyCancellableToken`, cancelled from a
 * `deinit`. Here the listener is removed in [awaitClose], which runs when the
 * collecting coroutine is cancelled — so a `viewModelScope` collection tears its
 * own listener down in `onCleared()` without anybody having to remember to.
 *
 * ### Why these emit a Result rather than a bare value
 *
 * A snapshot listener has three outcomes, not two: data, no data, and *failed to
 * ask*. Collapsing the last two into "empty" is how a member on a patchy
 * connection ends up looking at an empty signals feed that claims there are no
 * signals — or, much worse, gets signed out by the single-device check because a
 * network error read as "somebody else took this account". Callers must be able
 * to tell the difference, so the failure travels with the value.
 */
sealed interface Snapshot<out T> {
    data class Data<T>(val value: T) : Snapshot<T>
    data class Failed(val error: Throwable) : Snapshot<Nothing>
}

/** Live document, with permission and network errors preserved. */
fun DocumentReference.snapshotFlow(): Flow<Snapshot<DocumentSnapshot?>> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) {
            trySend(Snapshot.Failed(error))
        } else {
            trySend(Snapshot.Data(snapshot))
        }
    }
    awaitClose { registration.remove() }
}

/** Live query, with permission and network errors preserved. */
fun Query.snapshotFlow(): Flow<Snapshot<QuerySnapshot>> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        when {
            error != null -> trySend(Snapshot.Failed(error))
            snapshot != null -> trySend(Snapshot.Data(snapshot))
        }
    }
    awaitClose { registration.remove() }
}

/**
 * Firestore hands numbers back as `Long` or `Double` depending on what the
 * writer used, and the admin panel is JavaScript — which writes whole numbers as
 * integers and everything else as floats. Reading `as? Double` alone silently
 * returns null for a whole number, which is how a signal with `pipsGained: 40`
 * shows no pips at all.
 */
fun Any?.asDoubleOrNull(): Double? = when (this) {
    is Double -> this
    is Long -> this.toDouble()
    is Int -> this.toDouble()
    is Number -> this.toDouble()
    else -> null
}

fun Any?.asLongOrNull(): Long? = when (this) {
    is Long -> this
    is Int -> this.toLong()
    is Double -> Math.round(this)
    is Number -> this.toLong()
    else -> null
}

/** Blank strings are treated as absent — the admin panel writes "" for a field
 *  the editor left empty, and an empty string renders as a gap, not a default. */
fun Any?.asNonBlankString(): String? = (this as? String)?.takeIf { it.isNotBlank() }
