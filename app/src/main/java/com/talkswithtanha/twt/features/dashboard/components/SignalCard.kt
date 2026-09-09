package com.talkswithtanha.twt.features.dashboard.components

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.talkswithtanha.twt.core.designsystem.advancedShadow
import com.talkswithtanha.twt.core.designsystem.components.PremiumBadge
import com.talkswithtanha.twt.core.designsystem.components.PrimaryButton
import com.talkswithtanha.twt.core.designsystem.glassmorphism
import com.talkswithtanha.twt.core.theme.DangerRed
import com.talkswithtanha.twt.core.theme.SuccessGreen
import com.talkswithtanha.twt.features.dashboard.model.Signal
import com.talkswithtanha.twt.features.dashboard.model.SignalType

@Composable
fun SignalCard(
    signal: Signal,
    isUserPremium: Boolean,
    modifier: Modifier = Modifier
) {
    val isBlurred = signal.isPremium && !isUserPremium
    
    Box(
        modifier = modifier
            .width(280.dp)
            .advancedShadow(
                color = Color.Black,
                alpha = 0.08f,
                cornersRadius = 24.dp,
                shadowBlurRadius = 16.dp,
                offsetY = 8.dp
            )
            .clip(RoundedCornerShape(24.dp))
            .glassmorphism(backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                shape = RoundedCornerShape(24.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = signal.pair,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                if (signal.isPremium) {
                    PremiumBadge()
                } else {
                    Text(
                        text = signal.time,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // BUY / SELL Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (signal.type == SignalType.BUY) SuccessGreen.copy(alpha = 0.1f)
                        else DangerRed.copy(alpha = 0.1f)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = signal.type.name,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (signal.type == SignalType.BUY) SuccessGreen else DangerRed
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Data Rows
            Box {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (isBlurred) Modifier.blur(8.dp) else Modifier)
                ) {
                    SignalDataRow("Entry", signal.entry)
                    Spacer(modifier = Modifier.height(8.dp))
                    SignalDataRow("Take Profit", signal.takeProfit, color = SuccessGreen)
                    Spacer(modifier = Modifier.height(8.dp))
                    SignalDataRow("Stop Loss", signal.stopLoss, color = DangerRed)
                }
                
                // Blurred Overlay for Free Users
                if (isBlurred) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Locked",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            PrimaryButton(
                                text = "Unlock",
                                onClick = { },
                                isPremium = true
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SignalDataRow(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = color
            )
        )
    }
}
