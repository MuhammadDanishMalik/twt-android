package com.talkswithtanha.twt.core.designsystem.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talkswithtanha.twt.core.designsystem.Motion
import com.talkswithtanha.twt.core.designsystem.bouncyClick
import com.talkswithtanha.twt.core.designsystem.WebFonts
import com.talkswithtanha.twt.core.designsystem.rememberShimmerProgress

private val SkeletonCardBg = Color(0xFF141414)

/**
 * Words instead of a spinner: "Loading prices…" with a white highlight
 * sweeping through the letters, the way iOS shimmers "slide to unlock".
 * It says what is coming, which a spinning circle never does.
 *
 * The sweep is drawn into the text's own pixels (SrcAtop on an offscreen
 * layer), so only the glyphs light up — never the space around them — and
 * the progress is read in the draw phase, so nothing recomposes per frame.
 */
@Composable
fun LoadingLabel(
    text: String = "Loading",
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 14.sp
) {
    val progress = rememberShimmerProgress()
    Text(
        "$text…",
        color = Color.White.copy(alpha = 0.38f),
        fontSize = fontSize,
        fontWeight = FontWeight.SemiBold,
        fontFamily = WebFonts.Body,
        modifier = modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val (start, end) = shimmerBand(size.width, progress.value)
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White, Color.Transparent),
                        start = Offset(start, 0f),
                        end = Offset(end, 0f)
                    ),
                    blendMode = BlendMode.SrcAtop
                )
            }
    )
}

/** A card-shaped frame for a skeleton, matching the app's cards so nothing jumps when data lands. */
@Composable
fun SkeletonCard(
    modifier: Modifier = Modifier,
    label: String? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(SkeletonCardBg)
            .border(0.5.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(24.dp))
            .padding(vertical = 16.dp)
    ) {
        if (label != null) {
            Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                LoadingLabel(label, fontSize = 12.sp)
            }
            Spacer(Modifier.height(12.dp))
        }
        content()
    }
}

/**
 * Rows of a list that has not arrived: an optional round badge, a title and
 * a subtitle line, and a value on the right. Line widths vary row to row so
 * it reads as text rather than as a striped block.
 */
@Composable
fun SkeletonRows(
    count: Int,
    modifier: Modifier = Modifier,
    leading: Boolean = true,
    trailing: Boolean = true,
    rowHeight: Dp = 48.dp
) {
    val widths = listOf(0.62f, 0.48f, 0.7f, 0.55f, 0.66f, 0.42f)
    Column(modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(count) { i ->
            Row(Modifier.fillMaxWidth().height(rowHeight), verticalAlignment = Alignment.CenterVertically) {
                if (leading) {
                    Shimmer(Modifier.size(26.dp), height = 26.dp, cornerRadius = 13.dp)
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Shimmer(Modifier.fillMaxWidth(widths[i % widths.size]), height = 12.dp, cornerRadius = 6.dp)
                    Shimmer(Modifier.fillMaxWidth(widths[(i + 3) % widths.size] * 0.6f), height = 9.dp, cornerRadius = 5.dp)
                }
                if (trailing) {
                    Spacer(Modifier.width(12.dp))
                    Shimmer(Modifier.width(64.dp), height = 22.dp, cornerRadius = 10.dp)
                }
            }
        }
    }
}

/**
 * Where a chart will be: faint guide lines, a ghost of a price line, and the
 * shimmer travelling over it, with a label saying what is loading.
 */
@Composable
fun ChartSkeleton(label: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Shimmer(Modifier.fillMaxSize().padding(horizontal = 2.dp), height = Dp.Unspecified, cornerRadius = 14.dp)
        Canvas(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 18.dp)) {
            for (q in 0..3) {
                val y = size.height * q / 3f
                drawLine(Color.White.copy(alpha = 0.05f), Offset(0f, y), Offset(size.width, y), 1f)
            }
            val ghost = Path()
            val points = listOf(0.62f, 0.55f, 0.6f, 0.42f, 0.5f, 0.33f, 0.4f, 0.28f, 0.36f, 0.22f)
            points.forEachIndexed { i, p ->
                val x = size.width * i / (points.size - 1)
                if (i == 0) ghost.moveTo(x, size.height * p) else ghost.lineTo(x, size.height * p)
            }
            drawPath(ghost, Color.White.copy(alpha = 0.08f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }
        LoadingLabel(label)
    }
}

/**
 * The button at the foot of a long list or chart. Shows how much is left,
 * and while the next chunk is being prepared turns into a shimmering
 * "Loading more…" so the tap is acknowledged at once.
 */
@Composable
fun LoadMoreButton(
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Load more",
    remaining: String? = null
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .bouncyClick(enabled = !loading, onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = loading,
            transitionSpec = { fadeIn(Motion.gentle()).togetherWith(fadeOut(Motion.quick())) },
            label = "loadMore"
        ) { busy ->
            if (busy) {
                LoadingLabel("Loading more")
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    remaining?.let {
                        Spacer(Modifier.width(8.dp))
                        Text(it, color = Color.White.copy(alpha = 0.45f), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/**
 * How much of a long list or chart is on screen: a first chunk, then
 * another each time the member asks for it. Held in one small state object
 * so a screen reads `chunks.shown` and recomposes only when it moves.
 *
 * [more] holds the "Loading more…" skeleton for a short beat before the
 * rows land, so the new chunk animates in under the finger instead of
 * popping, and a double tap cannot add two chunks.
 */
@Stable
class Chunks(initial: Int, private val step: Int) {
    var shown by mutableIntStateOf(initial)
        private set
    var loading by mutableStateOf(false)
        private set

    suspend fun more() {
        if (loading) return
        loading = true
        kotlinx.coroutines.delay(380)
        shown += step
        loading = false
    }
}

/** A [Chunks] that starts again from [initial] whenever [key] changes — a new filter, a new market. */
@Composable
fun rememberChunks(initial: Int, step: Int = initial, key: Any? = null): Chunks =
    remember(key) { Chunks(initial, step) }
