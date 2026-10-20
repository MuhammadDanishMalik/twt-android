package com.talkswithtanha.twt.features.auth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.talkswithtanha.twt.R
import com.talkswithtanha.twt.core.designsystem.WebBackdrop
import com.talkswithtanha.twt.core.designsystem.WebColors
import com.talkswithtanha.twt.core.designsystem.WebFonts

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
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 300f)) }

    val breathe = rememberInfiniteTransition(label = "splash")
    val glow by breathe.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "glow"
    )

    WebBackdrop {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(R.drawable.twt_logo),
                contentDescription = "TWT",
                modifier = Modifier
                    .size(104.dp)
                    .graphicsLayer {
                        val s = 0.6f + 0.4f * pop.value
                        scaleX = s
                        scaleY = s
                        rotationZ = (1f - pop.value) * -12f
                        alpha = pop.value.coerceIn(0f, 1f)
                    }
                    .shadow(
                        elevation = (24 * glow).dp,
                        shape = CircleShape,
                        ambientColor = WebColors.Brand,
                        spotColor = WebColors.Brand
                    )
                    .clip(CircleShape)
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = "TALKS WITH TANHA",
                style = TextStyle(
                    fontFamily = WebFonts.Mono,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    letterSpacing = 0.24.em,
                    color = WebColors.Text3
                ),
                modifier = Modifier.graphicsLayer { alpha = pop.value.coerceIn(0f, 1f) * glow }
            )
        }
    }
}
