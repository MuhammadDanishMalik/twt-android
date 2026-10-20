package com.talkswithtanha.twt.core.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.action.actionStartActivity as actionStartIntent
import android.net.Uri
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.wrapContentHeight
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.talkswithtanha.twt.MainActivity
import com.talkswithtanha.twt.core.notifications.FollowedSignalNotifier
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * The member's trades on the home screen.
 *
 * Followed trades come first, then the live feed, then the latest closed calls,
 * so it is never blank on a quiet day. Small shows one trade and an arrow to the
 * next; wider sizes add the levels and then a short list.
 *
 * ### Why it is drawn this plainly
 *
 * Glance is not Compose. It compiles to RemoteViews, which means no arbitrary
 * drawing, no gradients, no shadows, and a layout budget the launcher can refuse
 * outright if it is exceeded. Everything here is boxes, text and colour — the
 * same information as the iOS widget, with none of the decoration that would
 * not survive the translation.
 */
class SignalsWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetStore.load(context)
        val cursor = WidgetStore.cursor(context)

        provideContent {
            GlanceTheme {
                SignalsWidgetContent(snapshot, cursor)
            }
        }
    }
}

/** Advances the small widget to the next trade, and redraws. */
class NextSignalAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        WidgetStore.setCursor(context, WidgetStore.cursor(context) + 1)
        // This widget by id, not `updateAll`: the callback runs while the host's
        // Glance session for this instance is already torn down, and updateAll
        // was being coalesced away — the cursor moved and the card did not.
        SignalsWidget().update(context, glanceId)
    }
}

class SignalsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SignalsWidget()
}

// ─── Drawing ────────────────────────────────────────────────────────────────

private val Ink = Color(0xFF0D0D0D)
private val Card = Color(0xFF1A1A1A)
private val Primary = Color(0xFFFFFFFF)
private val Secondary = Color(0xFF9A9A9A)
private val Tertiary = Color(0xFF6B6B6B)
private val Green = Color(0xFF33C759)
private val Red = Color(0xFFF04D4D)
private val Amber = Color(0xFFFF9F0A)

@Composable
private fun SignalsWidgetContent(snapshot: WidgetSnapshot, cursor: Int) {
    val size = LocalSize.current
    val wide = size.width >= 250.dp
    val tall = size.height >= 200.dp

    Column(
        GlanceModifier
            .fillMaxSize()
            .background(Ink)
            .cornerRadius(20.dp)
            .padding(14.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        val cyclable = snapshot.cyclable
        if (cyclable.isEmpty()) {
            EmptyWidget(snapshot.isSignedIn)
            return@Column
        }

        val featured = cyclable[((cursor % cyclable.size) + cyclable.size) % cyclable.size]

        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("TWT", style = TextStyle(color = androidx.glance.unit.ColorProvider(Secondary), fontSize = 11.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.defaultWeight())
            if (snapshot.liveCount > 0) {
                Text(
                    "${snapshot.liveCount} live",
                    style = TextStyle(color = androidx.glance.unit.ColorProvider(Green), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                )
            }
        }

        Spacer(GlanceModifier.height(if (tall) 10.dp else 6.dp))

        FeaturedSignal(featured, wide)

        if (tall) {
            val others = snapshot.signals.filter { it.id != featured.id }.take(if (size.height >= 300.dp) 3 else 1)
            if (others.isNotEmpty()) {
                Spacer(GlanceModifier.height(10.dp))
                others.forEach { signal ->
                    SignalRow(signal)
                    Spacer(GlanceModifier.height(6.dp))
                }
            }
        }

        Spacer(GlanceModifier.defaultWeight())

        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${featured.progressLine} · ${shortAgo(featured.postedAt)}",
                style = TextStyle(color = androidx.glance.unit.ColorProvider(Tertiary), fontSize = 10.sp),
                maxLines = 1,
                // Weighted so it truncates against the button rather than
                // sliding underneath it.
                modifier = GlanceModifier.defaultWeight()
            )
            Spacer(GlanceModifier.width(6.dp))
            if (cyclable.size > 1) {
                Box(
                    GlanceModifier
                        .size(28.dp)
                        .background(Card)
                        .cornerRadius(14.dp)
                        .clickable(actionRunCallback<NextSignalAction>()),
                    contentAlignment = Alignment.Center
                ) {
                    Text("›", style = TextStyle(color = androidx.glance.unit.ColorProvider(Primary), fontSize = 16.sp, fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

/**
 * Opening one trade from the widget.
 *
 * The same extra a tapped notification carries, so both land on the same screen
 * — `MainActivity` already reads it. The `data` URI is there only to make each
 * PendingIntent distinct: without it the launcher reuses the first one and every
 * row opens whichever signal happened to be drawn first.
 */
private fun openSignal(context: Context, signalId: String): Intent =
    Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        data = Uri.parse("twt://signal/" + signalId)
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra(FollowedSignalNotifier.EXTRA_SIGNAL_ID, signalId)
    }

@Composable
private fun FeaturedSignal(signal: WidgetSignal, wide: Boolean) {
    val tint = when {
        signal.isLost -> Red
        signal.status == "PENDING" -> Amber
        else -> Green
    }

    val context = LocalContext.current

    Column(
        GlanceModifier
            .fillMaxWidth()
            .clickable(actionStartIntent(openSignal(context, signal.id)))
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                signal.pair,
                style = TextStyle(
                    color = androidx.glance.unit.ColorProvider(Primary),
                    fontSize = if (wide) 20.sp else 17.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            Spacer(GlanceModifier.width(6.dp))
            Box(
                GlanceModifier
                    .background(if (signal.isBuy) Green else Red)
                    .cornerRadius(6.dp)
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(
                    signal.direction,
                    style = TextStyle(color = androidx.glance.unit.ColorProvider(Color.Black), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                )
            }
            Spacer(GlanceModifier.defaultWeight())
            // Only when there is room. At 2×2 the status ran into the direction
            // chip and truncated to "PEND", and the footer says it anyway.
            if (wide) {
                Text(
                    signal.statusLabel,
                    style = TextStyle(color = androidx.glance.unit.ColorProvider(tint), fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
            }
        }

        Spacer(GlanceModifier.height(8.dp))

        if (wide) {
            Row(GlanceModifier.fillMaxWidth()) {
                Level("ENTRY", signal.entry, Primary, GlanceModifier.defaultWeight())
                Level("STOP", signal.stopLoss, Red, GlanceModifier.defaultWeight())
                Level("TARGET", signal.finalTarget, Green, GlanceModifier.defaultWeight())
            }
        } else {
            Column {
                Text(
                    if (signal.pipsText != null) "PIPS" else "ENTRY",
                    style = TextStyle(color = androidx.glance.unit.ColorProvider(Tertiary), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                )
                Text(
                    signal.pipsText ?: signal.entry,
                    style = TextStyle(
                        color = androidx.glance.unit.ColorProvider(if (signal.pipsText != null) tint else Primary),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
            }
        }

        Spacer(GlanceModifier.height(8.dp))
        MilestoneBar(signal)
    }
}

/**
 * The detail screen's milestone track, reduced to what RemoteViews can draw:
 * one filled box per stop. Green runs from the entry to the furthest target hit;
 * red fills the first box when the trade was stopped out.
 */
@Composable
private fun MilestoneBar(signal: WidgetSignal) {
    val stops = signal.targets.size + 2
    val reached = when {
        signal.isLost -> 0
        signal.isWon && signal.hitCount == 0 -> stops - 1
        else -> 1 + signal.hitCount
    }

    Row(GlanceModifier.fillMaxWidth().height(4.dp)) {
        repeat(stops) { index ->
            val filled = when {
                signal.isLost -> index == 0
                else -> index in 1..reached
            }
            val colour = when {
                signal.isLost && index == 0 -> Red
                filled -> Green
                else -> Color(0xFF2B2B2B)
            }
            Box(
                GlanceModifier
                    .defaultWeight()
                    .height(4.dp)
                    .padding(horizontal = 1.dp)
            ) {
                Box(GlanceModifier.fillMaxSize().background(colour).cornerRadius(2.dp)) {}
            }
        }
    }
}

@Composable
private fun Level(label: String, value: String, tint: Color, modifier: GlanceModifier) {
    Column(modifier) {
        Text(label, style = TextStyle(color = androidx.glance.unit.ColorProvider(Tertiary), fontSize = 9.sp, fontWeight = FontWeight.Bold))
        Text(
            value,
            style = TextStyle(color = androidx.glance.unit.ColorProvider(tint), fontSize = 13.sp, fontWeight = FontWeight.Bold),
            maxLines = 1
        )
    }
}

@Composable
private fun SignalRow(signal: WidgetSignal) {
    val context = LocalContext.current

    Row(
        GlanceModifier
            .fillMaxWidth()
            .background(Card)
            .cornerRadius(12.dp)
            .clickable(actionStartIntent(openSignal(context, signal.id)))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (signal.isBuy) "▲" else "▼",
            style = TextStyle(
                color = androidx.glance.unit.ColorProvider(if (signal.isBuy) Green else Red),
                fontSize = 10.sp
            )
        )
        Spacer(GlanceModifier.width(8.dp))
        Text(
            signal.pair,
            style = TextStyle(color = androidx.glance.unit.ColorProvider(Primary), fontSize = 13.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight()
        )
        Spacer(GlanceModifier.width(6.dp))
        Text(
            signal.pipsText?.let { "$it pips" } ?: signal.statusLabel,
            style = TextStyle(
                // The same rule as the featured card: red when it stopped out,
                // amber while it is still waiting for its entry, green otherwise.
                color = androidx.glance.unit.ColorProvider(
                    when {
                        signal.isLost -> Red
                        signal.status == "PENDING" -> Amber
                        else -> Green
                    }
                ),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun EmptyWidget(isSignedIn: Boolean) {
    Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (isSignedIn) "No signals yet" else "Open Twt",
            style = TextStyle(color = androidx.glance.unit.ColorProvider(Primary), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        )
        Spacer(GlanceModifier.height(4.dp))
        Text(
            if (isSignedIn) {
                "New calls from Tanha land here the moment they are posted."
            } else {
                "Sign in to see your trades here."
            },
            style = TextStyle(color = androidx.glance.unit.ColorProvider(Secondary), fontSize = 12.sp),
            maxLines = 3
        )
    }
}

/** "12m ago" — short enough to sit beside the progress line. */
internal fun shortAgo(date: Date): String {
    val seconds = ((Date().time - date.time) / 1000).coerceAtLeast(0)
    return when {
        seconds < 60 -> "just now"
        seconds < 3_600 -> "${TimeUnit.SECONDS.toMinutes(seconds)}m ago"
        seconds < 86_400 -> "${TimeUnit.SECONDS.toHours(seconds)}h ago"
        else -> "${TimeUnit.SECONDS.toDays(seconds)}d ago"
    }
}
