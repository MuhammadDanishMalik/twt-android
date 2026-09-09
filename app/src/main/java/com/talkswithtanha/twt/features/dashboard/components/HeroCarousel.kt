package com.talkswithtanha.twt.features.dashboard.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.talkswithtanha.twt.core.designsystem.advancedShadow
import com.talkswithtanha.twt.core.designsystem.components.PrimaryButton
import com.talkswithtanha.twt.core.designsystem.glassmorphism
import com.talkswithtanha.twt.core.theme.AccentGold
import com.talkswithtanha.twt.core.theme.AccentGoldDark
import com.talkswithtanha.twt.core.theme.GradientEnd
import com.talkswithtanha.twt.core.theme.GradientStart
import com.talkswithtanha.twt.features.dashboard.model.UserProfile

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HeroCarousel(
    user: UserProfile,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(pageCount = { 3 })

    Column(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 24.dp),
            pageSpacing = 16.dp
        ) { page ->
            when (page) {
                0 -> MembershipCard(user)
                1 -> PerformanceSummaryCard()
                2 -> ReferralCard()
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Pager Indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(3) { index ->
                val isSelected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (isSelected) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) AccentGold else Color.Gray.copy(alpha = 0.5f)
                        )
                )
            }
        }
    }
}

@Composable
private fun MembershipCard(user: UserProfile) {
    val gradient = if (user.isPremium) {
        Brush.linearGradient(listOf(AccentGold, AccentGoldDark))
    } else {
        Brush.linearGradient(listOf(GradientStart, GradientEnd))
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .advancedShadow(
                color = if (user.isPremium) AccentGold else Color.Black,
                alpha = 0.3f,
                cornersRadius = 32.dp,
                shadowBlurRadius = 24.dp,
                offsetY = 12.dp
            )
            .clip(RoundedCornerShape(32.dp))
            .background(gradient)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (user.isPremium) "Premium Member" else "Free Member",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
                // App Logo Placeholder
            }
            
            if (!user.isPremium) {
                Column {
                    Text(
                        text = "Unlock live signals & analysis",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.7f))
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    PrimaryButton(
                        text = "Upgrade Now",
                        onClick = { },
                        isPremium = true
                    )
                }
            } else {
                Text(
                    text = "All features unlocked",
                    style = MaterialTheme.typography.bodyLarge.copy(color = Color.White)
                )
            }
        }
    }
}

@Composable
private fun PerformanceSummaryCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .advancedShadow(
                color = Color.Black,
                alpha = 0.15f,
                cornersRadius = 32.dp,
                shadowBlurRadius = 20.dp,
                offsetY = 12.dp
            )
            .clip(RoundedCornerShape(32.dp))
            .glassmorphism(backgroundColor = Color(0xAAFFFFFF))
            .padding(24.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "Today's Performance",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "+142 Pips",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = com.talkswithtanha.twt.core.theme.SuccessGreen
                )
            )
        }
    }
}

@Composable
private fun ReferralCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .advancedShadow(
                color = Color.Black,
                alpha = 0.15f,
                cornersRadius = 32.dp,
                shadowBlurRadius = 20.dp,
                offsetY = 12.dp
            )
            .clip(RoundedCornerShape(32.dp))
            .glassmorphism(backgroundColor = Color(0xAA1E1E1E))
            .padding(24.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "Refer & Earn",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )
            PrimaryButton(
                text = "Invite Friends",
                onClick = { },
                isPremium = true
            )
        }
    }
}
