package com.talkswithtanha.twt.core.notifications

import android.content.Context
import androidx.core.content.edit
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The broadcast alerts every member can have: the four forex sessions opening
 * (Sydney, Tokyo, London, New York) and the weekly COT report coming out.
 *
 * They go out to FCM topics from the panel's server (lib/server/market-alerts.ts)
 * rather than to a list of members, so this phone simply subscribes or not.
 * On by default; a choice made in Settings is kept on the device, because a
 * topic subscription belongs to the device, not the account.
 */
@Singleton
class MarketAlerts @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    data class Choices(val sessions: Boolean = true, val cot: Boolean = true)

    private val store = context.getSharedPreferences("market_alerts", Context.MODE_PRIVATE)
    private val _choices = MutableStateFlow(Choices(store.getBoolean(SESSIONS, true), store.getBoolean(COT, true)))
    val choices: StateFlow<Choices> = _choices.asStateFlow()

    /** Brings the topic subscriptions in line with the choices, at every launch. */
    fun start() = apply(_choices.value)

    fun setSessions(on: Boolean) = update(_choices.value.copy(sessions = on))
    fun setCot(on: Boolean) = update(_choices.value.copy(cot = on))

    private fun update(next: Choices) {
        store.edit { putBoolean(SESSIONS, next.sessions); putBoolean(COT, next.cot) }
        _choices.value = next
        apply(next)
    }

    private fun apply(c: Choices) {
        val fcm = runCatching { FirebaseMessaging.getInstance() }.getOrNull() ?: return
        if (c.sessions) fcm.subscribeToTopic(TOPIC_SESSIONS) else fcm.unsubscribeFromTopic(TOPIC_SESSIONS)
        if (c.cot) fcm.subscribeToTopic(TOPIC_COT) else fcm.unsubscribeFromTopic(TOPIC_COT)
    }

    companion object {
        /** The topic names the server sends to. Changing either side breaks both. */
        const val TOPIC_SESSIONS = "market_sessions"
        const val TOPIC_COT = "cot_reports"
        private const val SESSIONS = "sessions"
        private const val COT = "cot"
    }
}
