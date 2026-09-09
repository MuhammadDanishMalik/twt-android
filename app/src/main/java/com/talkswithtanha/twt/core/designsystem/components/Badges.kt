package com.talkswithtanha.twt.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.talkswithtanha.twt.core.designsystem.Spacing
import com.talkswithtanha.twt.core.designsystem.TwtColors
import com.talkswithtanha.twt.core.model.SignalStatus
import com.talkswithtanha.twt.core.model.SignalType

/** A small pill of text. The base every other badge here is built from. */
@Composable
fun TwtChip(
    text: String,
    modifier: Modifier = Modifier,
    contentColor: Color = TwtColors.TextSecondary,
    containerColor: Color = TwtColors.SurfaceElevated,
    borderColor: Color? = null
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .let { if (borderColor != null) it.border(0.5.dp, borderColor, RoundedCornerShape(8.dp)) else it }
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor
        )
    }
}

/**
 * Buy or sell.
 *
 * The only place the semantic green and red are allowed to appear as a fill.
 * They mean direction; borrowing them for a "saved" toast would make a
 * confirmation read as a trade.
 */
@Composable
fun DirectionBadge(type: SignalType, modifier: Modifier = Modifier) {
    val isBuy = type == SignalType.BUY
    TwtChip(
        text = type.stored,
        modifier = modifier,
        contentColor = if (isBuy) TwtColors.Buy else TwtColors.Sell,
        containerColor = if (isBuy) TwtColors.BuyWash else TwtColors.SellWash,
        borderColor = if (isBuy) TwtColors.Buy.copy(alpha = 0.3f) else TwtColors.Sell.copy(alpha = 0.3f)
    )
}

/**
 * Where a trade stands.
 *
 * The stored strings carry the tick and cross characters — `CLOSED ✓` — so the
 * label is the raw value rather than something prettier, which keeps what the
 * member sees identical to what iOS and the admin panel show.
 */
@Composable
fun StatusBadge(status: SignalStatus, modifier: Modifier = Modifier) {
    val color = when {
        status.isLost -> TwtColors.Sell
        status.isWon -> TwtColors.Buy
        status == SignalStatus.PENDING -> TwtColors.Warning
        else -> TwtColors.TextSecondary
    }
    TwtChip(
        text = status.stored,
        modifier = modifier,
        contentColor = color,
        containerColor = color.copy(alpha = 0.10f)
    )
}

/**
 * The one place gold is used as a badge.
 *
 * Note what this does *not* say: there is no price, no plan and no "upgrade"
 * anywhere in this app, deliberately — access comes from a code Tanha issues.
 */
@Composable
fun PremiumBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(TwtColors.GoldWash)
            .border(0.5.dp, TwtColors.Gold.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Bolt,
            contentDescription = null,
            tint = TwtColors.Gold,
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = "MEMBER",
            style = MaterialTheme.typography.labelSmall,
            color = TwtColors.Gold
        )
    }
}
