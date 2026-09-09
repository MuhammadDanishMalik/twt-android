package com.talkswithtanha.twt.features.auth

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.talkswithtanha.twt.core.designsystem.Spacing
import com.talkswithtanha.twt.core.designsystem.TwtColors
import com.talkswithtanha.twt.core.designsystem.components.TwtScreen

/**
 * What a returning member sees for the fraction of a second it takes Firebase to
 * hand back a cached credential.
 *
 * Deliberately has no timeout and no navigation of its own — `RootViewModel`
 * moves off it the moment the session resolves. A splash that navigates on a
 * timer races the thing it is waiting for.
 */
@Composable
fun SplashScreen() {
    val transition = rememberInfiniteTransition(label = "splash")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "alpha"
    )

    TwtScreen {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "TWT",
                style = MaterialTheme.typography.displayLarge,
                color = TwtColors.Gold,
                modifier = Modifier.alpha(alpha)
            )
            Text(
                text = "TALKS WITH TANHA",
                style = MaterialTheme.typography.labelSmall,
                color = TwtColors.TextTertiary,
                modifier = Modifier.alpha(alpha)
            )
        }
    }
}
