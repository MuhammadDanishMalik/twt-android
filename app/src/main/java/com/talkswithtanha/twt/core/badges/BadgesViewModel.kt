package com.talkswithtanha.twt.core.badges

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.talkswithtanha.twt.core.navigation.AppRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The counts on the tab bar and the chats button, for the shell. */
@HiltViewModel
class BadgesViewModel @Inject constructor(private val badges: Badges) : ViewModel() {

    /** Chats live behind the Feed tab, so their unread counts there too. */
    val tabs: StateFlow<Map<String, Int>> =
        combine(badges.signalsNew, badges.feedNew, badges.chatsUnread) { signals, feed, chats ->
            mapOf(AppRoute.Signals.route to signals, AppRoute.Feed.route to feed + chats)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val chats: StateFlow<Int> = badges.chatsUnread

    /** Opening a tab is looking at what is new in it. */
    fun onRoute(route: String?) {
        when (route) {
            AppRoute.Signals.route -> badges.markSignalsSeen()
            AppRoute.Feed.route -> badges.markFeedSeen()
        }
    }
}
