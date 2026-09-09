package com.talkswithtanha.twt.features.dashboard.viewmodel

import androidx.lifecycle.ViewModel
import com.talkswithtanha.twt.features.dashboard.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DashboardViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardState())
    val uiState: StateFlow<DashboardState> = _uiState.asStateFlow()

    init {
        loadMockData()
    }

    private fun loadMockData() {
        val signals = listOf(
            Signal("1", "EUR/USD", SignalType.BUY, "1.0920", "1.0980", "1.0890", "2%", SignalStatus.ACTIVE, "10 min ago", isPremium = false),
            Signal("2", "XAU/USD", SignalType.SELL, "2040.50", "2020.00", "2050.00", "1.5%", SignalStatus.ACTIVE, "1 hr ago", isPremium = true),
            Signal("3", "GBP/JPY", SignalType.BUY, "188.50", "189.50", "188.00", "1%", SignalStatus.PENDING, "2 hrs ago", isPremium = true)
        )

        val markets = listOf(
            MarketData("Gold", "2042.30", 0.45, MarketStatus.OPEN),
            MarketData("EUR/USD", "1.0925", -0.12, MarketStatus.OPEN),
            MarketData("BTC/USD", "43200.00", 2.5, MarketStatus.VOLATILE),
            MarketData("GBP/USD", "1.2640", 0.05, MarketStatus.OPEN)
        )

        val analysis = listOf(
            AnalysisPost("1", "Technical", "Gold approaching major resistance zone", "4 min read", "Today", null, isPremium = false),
            AnalysisPost("2", "Fundamental", "Impact of upcoming NFP on USD pairs", "8 min read", "Yesterday", null, isPremium = true)
        )

        val academy = listOf(
            AcademyCourse("1", "Forex Basics 101", "2h 30m", 0.75f),
            AcademyCourse("2", "Advanced Price Action", "4h 15m", 0.1f)
        )
        
        val community = listOf(
            CommunityDiscussion("1", "EUR/USD Daily Setup", 12, 450),
            CommunityDiscussion("2", "Prop Firm Passing Strategies", 5, 1200)
        )

        _uiState.value = DashboardState(
            user = UserProfile("Muhammad", isPremium = false),
            signals = signals,
            performance = PerformanceStats("78%", "92%", "450", "$1,240", "$5,300", "12%", 45, 12),
            markets = markets,
            analysis = analysis,
            academy = academy,
            community = community
        )
    }
}
