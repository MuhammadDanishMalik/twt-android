package com.example.twt_android.features.dashboard.model

data class UserProfile(
    val name: String,
    val isPremium: Boolean,
    val avatarUrl: String? = null
)

data class Signal(
    val id: String,
    val pair: String,
    val type: SignalType,
    val entry: String,
    val takeProfit: String,
    val stopLoss: String,
    val risk: String,
    val status: SignalStatus,
    val time: String,
    val isPremium: Boolean
)

enum class SignalType {
    BUY, SELL
}

enum class SignalStatus {
    ACTIVE, WON, LOST, PENDING
}

data class PerformanceStats(
    val winRate: String,
    val accuracy: String,
    val totalPips: String,
    val todayProfit: String,
    val weeklyProfit: String,
    val monthlyGrowth: String,
    val signalsWon: Int,
    val signalsLost: Int
)

data class MarketData(
    val pair: String,
    val price: String,
    val change: Double,
    val status: MarketStatus
)

enum class MarketStatus {
    OPEN, CLOSED, VOLATILE
}

data class AnalysisPost(
    val id: String,
    val category: String,
    val title: String,
    val readingTime: String,
    val date: String,
    val imageUrl: String? = null,
    val isPremium: Boolean
)

data class AcademyCourse(
    val id: String,
    val title: String,
    val duration: String,
    val completionPercentage: Float,
    val thumbnailUrl: String? = null
)

data class CommunityDiscussion(
    val id: String,
    val title: String,
    val unreadCount: Int,
    val memberCount: Int
)

data class DashboardState(
    val isLoading: Boolean = false,
    val user: UserProfile = UserProfile("Muhammad", false),
    val signals: List<Signal> = emptyList(),
    val performance: PerformanceStats = PerformanceStats("0%", "0%", "0", "$0", "$0", "0%", 0, 0),
    val markets: List<MarketData> = emptyList(),
    val analysis: List<AnalysisPost> = emptyList(),
    val academy: List<AcademyCourse> = emptyList(),
    val community: List<CommunityDiscussion> = emptyList()
)
