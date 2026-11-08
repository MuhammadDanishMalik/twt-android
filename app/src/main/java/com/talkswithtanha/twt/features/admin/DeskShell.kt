package com.talkswithtanha.twt.features.admin

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.Icon
import com.talkswithtanha.twt.core.designsystem.components.BottomBarItem
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The dealer's app shell. Their Home is the desk, and the floating tab bar
 * holds the desk's queues rather than Signals, Markets and the rest. The bar
 * lives in RootScreen and the queues in the desk screen, so the chosen queue
 * and the counts on the bar are kept here, where both can reach them.
 */
object DeskShell {
    val selected = MutableStateFlow(DeskTab.REQUESTS)

    /** Bar route → count of things waiting there, for the red badges. */
    val badges = MutableStateFlow<Map<String, Int>>(emptyMap())

    fun route(tab: DeskTab) = "desk:${tab.name}"

    fun tabOf(route: String): DeskTab? = DeskTab.entries.firstOrNull { route(it) == route }

    val items: List<BottomBarItem> = DeskTab.entries.map { tab ->
        val icon = when (tab) {
            DeskTab.REQUESTS -> Icons.Filled.Dashboard
            DeskTab.NEEDS -> Icons.Filled.Payments
            DeskTab.WAITING -> Icons.Filled.HourglassEmpty
            DeskTab.CHATS -> Icons.AutoMirrored.Filled.Chat
            DeskTab.HISTORY -> Icons.Filled.History
        }
        BottomBarItem(tab.short, route(tab)) { tint, modifier -> Icon(icon, null, modifier, tint) }
    }
}
