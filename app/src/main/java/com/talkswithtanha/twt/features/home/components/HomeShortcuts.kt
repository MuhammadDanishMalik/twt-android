package com.talkswithtanha.twt.features.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import android.provider.Settings
import kotlinx.coroutines.launch
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.talkswithtanha.twt.core.designsystem.Haptics
import com.talkswithtanha.twt.core.designsystem.IosColors
import kotlinx.coroutines.delay
import kotlin.math.abs

/** How the three home entries are laid out. A member's choice, in Appearance. */
enum class HomeShortcutsLayout(val title: String, val subtitle: String) {
    /** All three, one under the other. Everything visible at once. */
    LIST("List", "All three, one under the other"),
    /** A deck: one card in front, the other two tucked behind it, cycling on
     *  its own and swipeable. Takes a third of the height. */
    STACK("Stack", "A deck that cycles on its own");

    companion object {
        const val STORAGE_KEY = "home.shortcutsLayout"

        fun from(value: String?): HomeShortcutsLayout =
            entries.firstOrNull { it.name == value } ?: LIST
    }
}

@Composable
fun HomeShortcuts(
    layout: HomeShortcutsLayout,
    onWatchLive: () -> Unit,
    onOpenMarketplace: () -> Unit,
    onOpenAcademy: () -> Unit,
    exchangeSubtitle: String,
    modifier: Modifier = Modifier
) {
    val cards: List<@Composable () -> Unit> = listOf(
        {
            HomeEntryCard(
                icon = Icons.Filled.PlayArrow,
                eyebrow = "LIVE NOW",
                title = "Gold outlook — PM session",
                subtitle = "Live from the YouTube channel",
                actionTitle = "Watch",
                isLive = true,
                onClick = onWatchLive
            )
        },
        {
            HomeEntryCard(
                icon = Icons.Filled.SwapHoriz,
                eyebrow = "EXCHANGE",
                title = exchangeSubtitle,
                subtitle = "Buy and sell dollars with Tanha",
                actionTitle = "Open",
                onClick = onOpenMarketplace
            )
        },
        {
            HomeEntryCard(
                icon = Icons.Outlined.School,
                eyebrow = "ACADEMY",
                title = "Learn to trade with Tanha",
                subtitle = "Video lessons and market breakdowns",
                actionTitle = "Open",
                onClick = onOpenAcademy
            )
        }
    )

    // Switching in Appearance folds the list into the deck and back out,
    // rather than the section jumping between two shapes.
    AnimatedContent(
        targetState = layout,
        transitionSpec = {
            (fadeIn(tween(220)) + scaleIn(initialScale = 0.97f, transformOrigin = TransformOrigin(0.5f, 0f)))
                .togetherWith(fadeOut(tween(160)))
        },
        label = "shortcuts"
    ) { current ->
        when (current) {
            HomeShortcutsLayout.LIST -> Column(
                modifier = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                cards.forEach { it() }
            }

            HomeShortcutsLayout.STACK -> ShortcutDeck(cards, modifier)
        }
    }
}

/**
 * Three cards stacked like a wallet: the front one whole, the next two peeking
 * out underneath, smaller and dimmer the further back they sit.
 *
 * It advances on its own every few seconds and on a swipe. The front card flies
 * off in the direction it was thrown and the one behind rises into its place, so
 * cycling reads as physically moving a card to the back of the pile rather than
 * as a slideshow replacing one picture with another.
 *
 * Auto-advance stops while a finger is on it.
 */
@Composable
private fun ShortcutDeck(
    cards: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val count = cards.size

    var front by remember { mutableIntStateOf(0) }
    var isTouching by remember { mutableStateOf(false) }

    /**
     * Live horizontal offset of the front card, in pixels.
     *
     * Plain state, updated straight from the gesture callback, because the
     * release handler has to read the distance the finger actually travelled.
     * Accumulating into an `Animatable` instead means `snapTo` has to be
     * launched, and a fast swipe reaches `onDragEnd` before those coroutines
     * have run — so the throw is measured against a stale value and a real
     * swipe is treated as a tap that missed. That is exactly how swiping the
     * deck backwards silently did nothing.
     */
    var dragPx by remember { mutableFloatStateOf(0f) }

    /** Drives the card only while it is springing back or flying off. */
    val settle = remember { Animatable(0f) }
    var settling by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val cardOffset = if (settling) settle.value else dragPx

    /** How far the cards behind peek out below the front one. */
    val peek = 11.dp

    /**
     * Whether the deck is allowed to move on its own.
     *
     * A member who has turned animations off at the system level is asking
     * exactly this of an app: nothing that moves without being touched. The
     * deck still works — it just waits to be swiped.
     */
    val animationsOn = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) != 0f
    }

    fun advance(step: Int, withHaptic: Boolean) {
        // A detent, not a tap. The card is snapping into a position, and the
        // difference is what makes the deck feel like it has stops in it rather
        // than like a button that happens to be draggable.
        if (withHaptic) Haptics.tick(haptics)
        // Wrapped twice, because Kotlin's remainder keeps the sign of the left
        // operand: a plain `(front - 1) % count` is -1 at the front of the deck,
        // which is not an index.
        front = ((front + step) % count + count) % count
    }

    LaunchedEffect(front, isTouching, animationsOn) {
        if (isTouching || !animationsOn) return@LaunchedEffect
        delay(4500)
        // Silently. The iOS deck fires a selection haptic on its timer, which
        // means a phone sitting on a desk buzzes every four and a half seconds;
        // a haptic should confirm something the member did.
        advance(step = 1, withHaptic = false)
    }

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(bottom = peek * (count - 1))
                .pointerInput(count) {
                    val throwDistance = 70.dp.toPx()
                    val offScreen = size.width.toFloat() * 1.2f

                    fun release() {
                        isTouching = false
                        val thrown = dragPx
                        settling = true
                        scope.launch {
                            settle.snapTo(thrown)
                            dragPx = 0f
                            if (abs(thrown) > throwDistance) {
                                Haptics.tap(haptics)
                                // Off in the direction it was pushed, then the
                                // deck rotates — so the card leaves the way it
                                // was thrown rather than blinking to the back
                                // of the pile.
                                settle.animateTo(
                                    targetValue = if (thrown > 0) offScreen else -offScreen,
                                    animationSpec = tween(160)
                                )
                                advance(step = if (thrown > 0) -1 else 1, withHaptic = false)
                            } else {
                                settle.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = 0.7f,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                            }
                            settle.snapTo(0f)
                            settling = false
                        }
                    }

                    detectHorizontalDragGestures(
                        onDragStart = { isTouching = true },
                        onDragEnd = { release() },
                        onDragCancel = { release() }
                    ) { change, amount ->
                        change.consume()
                        // Straight onto the card, with no animation in between:
                        // while a finger is down the card has to sit under it,
                        // and a spring here reads as lag rather than as weight.
                        dragPx += amount
                    }
                }
        ) {
            cards.forEachIndexed { index, card ->
                val depth = (index - front + count) % count

                // Each card eases into its new depth, so advancing lifts the one
                // behind into place instead of teleporting it.
                val scale by animateFloatAsState(
                    targetValue = 1f - depth * 0.05f,
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 320f),
                    label = "scale"
                )
                val lift by animateFloatAsState(
                    targetValue = depth.toFloat(),
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 320f),
                    label = "lift"
                )
                val dim by animateFloatAsState(
                    targetValue = if (depth == 0) 0f else 0.18f + depth * 0.14f,
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = 300f),
                    label = "dim"
                )

                Box(
                    Modifier
                        .fillMaxWidth()
                        .zIndex((count - depth).toFloat())
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0.5f, 1f)
                            translationY = lift * peek.toPx()
                            if (depth == 0) {
                                translationX = cardOffset
                                // Pivoting from the bottom edge, so the card
                                // tips like something held at its base rather
                                // than spinning about its middle.
                                rotationZ = cardOffset / 28f
                            }
                        }
                        // An opaque blank under every card, so the edges that
                        // peek out are real card edges and nothing behind is
                        // ever visible through a card in front of it.
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF1A1A1A), Color(0xFF111111))
                            )
                        )
                        .border(0.5.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(20.dp))
                ) {
                    // Only the front card's content is composed at all.
                    //
                    // Drawing the others at zero alpha — which is what the iOS
                    // deck does — leaves them in the tree: TalkBack reads three
                    // cards stacked on one another, and an invisible Watch
                    // button sits under the visible Open one waiting to catch a
                    // tap. What makes the deck read as a deck is the *edges*
                    // peeking out, and those are the backing below, not the
                    // content.
                    if (depth == 0) {
                        card()
                    }
                    if (dim > 0f) {
                        // Dimmed with a solid shade rather than transparency, so
                        // a card behind can never be seen through.
                        Box(
                            Modifier
                                .matchParentSize()
                                .background(Color.Black.copy(alpha = dim))
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(count) { index ->
                val selected = index == front
                val width by animateFloatAsState(
                    targetValue = if (selected) 16f else 6f,
                    animationSpec = spring(dampingRatio = 0.75f),
                    label = "dot"
                )
                Box(
                    Modifier
                        .width(width.dp)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(if (selected) Color.White else Color.White.copy(alpha = 0.25f))
                )
            }
        }
    }
}

/**
 * The three ways off the home screen, drawn by one component.
 *
 * **No hue at all.** The set is black, white and grey: near-black ground,
 * hairline rim, white glyph, white button. Separation comes from luminance and a
 * half-point border rather than from colour — three cards each picking their own
 * accent is what makes a screen read as assembled rather than designed.
 *
 * The one exception is the live dot, and it earns it: red there is a state, not
 * decoration.
 */
@Composable
fun HomeEntryCard(
    icon: ImageVector,
    eyebrow: String,
    title: String,
    subtitle: String,
    actionTitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLive: Boolean = false
) {
    val haptics = LocalHapticFeedback.current

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 520f),
        label = "press"
    )

    val pulse by rememberInfiniteTransition(label = "live").animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "pulse"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = press
                scaleY = press
            }
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(listOf(Color(0xFF1A1A1A), Color(0xFF111111)))
            )
            .border(0.5.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interaction,
                indication = null
            ) {
                Haptics.tap(haptics)
                onClick()
            }
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f))
                .border(0.5.dp, Color.White.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.92f))
        }

        Column(Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isLive) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(IosColors.SellRed.copy(alpha = pulse))
                    )
                }
                Text(
                    text = eyebrow,
                    color = Color.White.copy(alpha = 0.42f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }

        // Identical on all three cards. This is the whole point of the
        // component: the action reads as the same action everywhere, and only
        // the label changes.
        Box(
            Modifier
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f))
                .border(0.5.dp, Color.White.copy(alpha = 0.20f), CircleShape)
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Text(
                text = actionTitle,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
