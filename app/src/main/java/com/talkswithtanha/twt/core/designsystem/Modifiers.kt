package com.talkswithtanha.twt.core.designsystem

import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.glassmorphism(
    backgroundColor: Color = Color(0x99FFFFFF),
    blurRadius: Dp = 16.dp
): Modifier = composed {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        this.then(
            Modifier
                .blur(blurRadius)
                .drawBehind { drawRect(backgroundColor) }
        )
    } else {
        // Fallback for older Android versions
        this.then(
            Modifier.drawBehind { drawRect(backgroundColor) }
        )
    }
}

fun Modifier.advancedShadow(
    color: Color = Color.Black,
    alpha: Float = 0.2f,
    cornersRadius: Dp = 0.dp,
    shadowBlurRadius: Dp = 16.dp,
    offsetY: Dp = 8.dp,
    offsetX: Dp = 0.dp
): Modifier = composed {
    drawBehind {
        val shadowColor = color.copy(alpha = alpha).toArgb()
        val transparentColor = color.copy(alpha = 0f).toArgb()
        
        drawIntoCanvas {
            val paint = Paint()
            val frameworkPaint = paint.asFrameworkPaint()
            frameworkPaint.color = transparentColor
            frameworkPaint.setShadowLayer(
                shadowBlurRadius.toPx(),
                offsetX.toPx(),
                offsetY.toPx(),
                shadowColor
            )
            it.drawRoundRect(
                0f,
                0f,
                this.size.width,
                this.size.height,
                cornersRadius.toPx(),
                cornersRadius.toPx(),
                paint
            )
        }
    }
}
