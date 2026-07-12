package com.example.twt_android.features.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.twt_android.core.designsystem.advancedShadow
import com.example.twt_android.core.designsystem.glassmorphism
import com.example.twt_android.core.theme.AccentGold
import com.example.twt_android.core.theme.SuccessGreen
import com.example.twt_android.features.dashboard.model.AcademyCourse
import com.example.twt_android.features.dashboard.model.AnalysisPost
import com.example.twt_android.features.dashboard.model.MarketData

@Composable
fun PerformanceCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(160.dp)
            .advancedShadow(
                color = Color.Black,
                alpha = 0.05f,
                cornersRadius = 24.dp,
                shadowBlurRadius = 12.dp,
                offsetY = 6.dp
            )
            .clip(RoundedCornerShape(24.dp))
            .glassmorphism(backgroundColor = MaterialTheme.colorScheme.surface)
            .padding(20.dp)
    ) {
        Column {
            Icon(
                imageVector = Icons.Default.TrendingUp,
                contentDescription = null,
                tint = SuccessGreen,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            )
        }
    }
}

@Composable
fun MarketCard(
    market: MarketData,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(140.dp)
            .advancedShadow(
                color = Color.Black,
                alpha = 0.05f,
                cornersRadius = 20.dp,
                shadowBlurRadius = 10.dp,
                offsetY = 4.dp
            )
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = market.pair,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = market.price,
                style = MaterialTheme.typography.bodyLarge
            )
            val isPositive = market.change >= 0
            Text(
                text = "${if(isPositive) "+" else ""}${market.change}%",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = if (isPositive) SuccessGreen else com.example.twt_android.core.theme.DangerRed,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

@Composable
fun AnalysisCard(
    post: AnalysisPost,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(280.dp)
            .height(200.dp)
            .advancedShadow(
                color = Color.Black,
                alpha = 0.1f,
                cornersRadius = 24.dp,
                shadowBlurRadius = 16.dp,
                offsetY = 8.dp
            )
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        if (post.imageUrl != null) {
            AsyncImage(
                model = post.imageUrl,
                contentDescription = post.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(120.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(Color.DarkGray)
            )
        }
        
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = post.category,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = AccentGold,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = post.readingTime,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = post.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1
            )
        }
    }
}

@Composable
fun AcademyCard(
    course: AcademyCourse,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .width(320.dp)
            .advancedShadow(
                color = Color.Black,
                alpha = 0.05f,
                cornersRadius = 20.dp,
                shadowBlurRadius = 10.dp,
                offsetY = 4.dp
            )
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AccentGold.copy(alpha = 0.2f))
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = course.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { course.completionPercentage },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = AccentGold,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${(course.completionPercentage * 100).toInt()}% Completed",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            )
        }
    }
}
