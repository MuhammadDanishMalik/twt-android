package com.talkswithtanha.twt.core.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * The counterpart to iOS's `@AppStorage`: small local values that belong to this
 * installation rather than to the account.
 *
 * One store for the whole app. DataStore throws if two instances are created for
 * the same file, so the delegate is declared exactly once, here.
 */
val Context.appPreferences: DataStore<Preferences> by preferencesDataStore(name = "twt_prefs")
