package com.talkswithtanha.twt.core.session

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.talkswithtanha.twt.core.storage.appPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * This installation's identity, for single-device enforcement.
 *
 * Android has no `identifierForVendor`. `ANDROID_ID` is close, but it is
 * scoped per signing key and survives an app reinstall on some versions and not
 * others, and reading it needs no permission but does need a `Settings.Secure`
 * lookup that returns the same value for two different accounts on one phone.
 *
 * What the feature actually needs is "is this the same *installation* that last
 * claimed the account", so a random id generated once and stored is both simpler
 * and more accurate than anything the platform offers. A reinstall producing a
 * new id is correct behaviour: it is a new installation, and it should have to
 * claim the account again.
 */
@Singleton
class DeviceIdentity @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    /** Stable for the life of this install. */
    suspend fun deviceId(): String = stored(DEVICE_ID_KEY)

    /**
     * Rotated every time this device claims the account.
     *
     * The claim writes this value to `users/{uid}.currentSessionId`; the profile
     * listener then compares what comes back against this. A new value on every
     * claim is what makes "another device took over" detectable at all.
     */
    suspend fun currentSessionId(): String = stored(SESSION_ID_KEY)

    suspend fun rotateSessionId(): String {
        val fresh = UUID.randomUUID().toString()
        context.appPreferences.edit { it[SESSION_ID_KEY] = fresh }
        return fresh
    }

    /** "Pixel 8 Pro", "SM-G991B" — what the admin panel shows next to the uid. */
    val deviceModel: String
        get() = listOf(Build.MANUFACTURER, Build.MODEL)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "Android device" }

    private suspend fun stored(key: androidx.datastore.preferences.core.Preferences.Key<String>): String {
        val existing = context.appPreferences.data.first()[key]
        if (!existing.isNullOrBlank()) return existing
        val fresh = UUID.randomUUID().toString()
        context.appPreferences.edit { it[key] = fresh }
        return fresh
    }

    private companion object {
        val DEVICE_ID_KEY = stringPreferencesKey("twt_device_id")
        val SESSION_ID_KEY = stringPreferencesKey("twt_active_session_id")
    }
}
