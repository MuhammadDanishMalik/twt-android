package com.talkswithtanha.twt.core.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
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
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.talkswithtanha.twt.MainActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

/**
 * The TWT Access Card on the home screen — the member's counterpart to a streak
 * counter: one big number, the days of access they have left.
 *
 * It turns amber inside the fortnight before renewal and red once access has run
 * out, the same rule and the same colours as the card in the app, and a tap
 * opens the app where that card lives.
 */
class AccessCardWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetStore.load(context)
        provideContent {
            GlanceTheme { AccessWidgetContent(snapshot) }
        }
    }
}

class AccessCardWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AccessCardWidget()
}

// ─── State ──────────────────────────────────────────────────────────────────

/**
 * The same arithmetic as the in-app card, restated for the widget. Days are
 * rounded *up*, as a calendar counts them: a code expiring 64 days from now is
 * 63.99 days away a second after it is issued, and flooring that tells somebody
 * they have 63 days when they were just told they had 64.
 */
private class AccessState(private val access: WidgetAccess, private val now: Date = Date()) {

    private val secondsLeft: Long? = access.expiresAt?.let { (it.time - now.time) / 1000 }

    val isLifetime: Boolean get() = access.expiresAt == null
    val isExpired: Boolean get() = (secondsLeft ?: 1L) <= 0L
    val needsRenewal: Boolean get() = (secondsLeft ?: Long.MAX_VALUE) <= 14L * 86_400L
    private val isFinalDay: Boolean get() = (secondsLeft ?: Long.MAX_VALUE) < 86_400L

    val value: String
        get() {
            val seconds = secondsLeft ?: return "∞"
            if (seconds <= 0) return "0"
            if (isFinalDay) return maxOf(1L, seconds / 3_600).toString()
            return ceil(seconds / 86_400.0).toInt().toString()
        }

    val unit: String
        get() {
            val seconds = secondsLeft ?: return "Lifetime access"
            if (seconds <= 0) return "Access ended"
            if (isFinalDay) return if (seconds / 3_600 <= 1) "hour left" else "hours left"
            return if (value == "1") "day left" else "days left"
        }

    val label: String get() = if (isExpired) "EXPIRED" else if (needsRenewal) "RENEW SOON" else "ACTIVE"

    val tint: Color get() = if (isExpired) Red else if (needsRenewal) Amber else Green

    val validThru: String
        get() = access.expiresAt?.let { SimpleDateFormat("MM/yy", Locale.getDefault()).format(it) } ?: "LIFETIME"

    /** Share of the term still to run, for the bar. Null for a lifetime code or
     *  an account granted before the date was recorded — a bar with a made-up
     *  starting point would be a guess drawn to look like a fact. */
    val fractionRemaining: Float?
        get() {
            val expires = access.expiresAt ?: return null
            val granted = access.grantedAt ?: return null
            val total = (expires.time - granted.time).toFloat()
            if (total <= 0f) return null
            return ((expires.time - now.time) / total).coerceIn(0f, 1f)
        }

    val holder: String get() = access.holderName.trim().ifEmpty { "MEMBER" }.uppercase()
}

// ─── Drawing ────────────────────────────────────────────────────────────────

private val Ink = Color(0xFF0D0D0D)
private val Track = Color(0xFF2B2B2B)
private val Primary = Color(0xFFFFFFFF)
private val Secondary = Color(0xFF9A9A9A)
private val Tertiary = Color(0xFF6B6B6B)
private val Green = Color(0xFF33C759)
private val Red = Color(0xFFF04D4D)
private val Amber = Color(0xFFFF9F0A)

@Composable
private fun AccessWidgetContent(snapshot: WidgetSnapshot) {
    val access = snapshot.access

    Column(
        GlanceModifier
            .fillMaxSize()
            .background(Ink)
            .cornerRadius(20.dp)
            .padding(14.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        if (access == null || !snapshot.isSignedIn) {
            Text(
                "Open Twt",
                style = TextStyle(color = ColorProvider(Primary), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(GlanceModifier.height(4.dp))
            Text(
                "Sign in to see your Access Card.",
                style = TextStyle(color = ColorProvider(Secondary), fontSize = 12.sp),
                maxLines = 2
            )
            return@Column
        }

        val state = AccessState(access)

        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "TWT ACCESS CARD",
                style = TextStyle(color = ColorProvider(Secondary), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                state.label,
                style = TextStyle(color = ColorProvider(state.tint), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            )
        }

        Spacer(GlanceModifier.defaultWeight())

        Text(
            state.value,
            style = TextStyle(color = ColorProvider(Primary), fontSize = 44.sp, fontWeight = FontWeight.Bold)
        )
        Text(
            state.unit,
            style = TextStyle(
                color = ColorProvider(if (state.needsRenewal) state.tint else Secondary),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        )

        Spacer(GlanceModifier.defaultWeight())

        state.fractionRemaining?.let { fraction ->
            Row(GlanceModifier.fillMaxWidth().height(4.dp)) {
                // Glance has no progress bar that can be coloured per state, so
                // the track is two boxes sharing the row by weight. A fraction
                // of zero would collapse the filled half to nothing, hence the
                // floor: an expiring card still shows a sliver.
                Box(
                    GlanceModifier
                        .defaultWeight()
                        .height(4.dp)
                        .background(state.tint)
                        .cornerRadius(2.dp)
                ) {}
                if (fraction < 0.98f) {
                    Spacer(GlanceModifier.width(2.dp))
                    Box(
                        GlanceModifier
                            .defaultWeight()
                            .height(4.dp)
                            .background(Track)
                            .cornerRadius(2.dp)
                    ) {}
                }
            }
            Spacer(GlanceModifier.height(8.dp))
        }

        Row(GlanceModifier.fillMaxWidth()) {
            Column(GlanceModifier.defaultWeight()) {
                Text(
                    "CARDHOLDER",
                    style = TextStyle(color = ColorProvider(Tertiary), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                )
                Text(
                    state.holder,
                    style = TextStyle(color = ColorProvider(Primary), fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1
                )
            }
            Column {
                Text(
                    "VALID THRU",
                    style = TextStyle(color = ColorProvider(Tertiary), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                )
                Text(
                    state.validThru,
                    style = TextStyle(color = ColorProvider(Primary), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                )
            }
        }
    }
}
