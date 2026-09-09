package com.talkswithtanha.twt.core.designsystem

import android.content.Context
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Two faces, doing two jobs.
 *
 * The display face is Helvetica Now Condensed. It is licensed and deliberately
 * **not committed to this repository**, so it is resolved by *name* at runtime
 * rather than through `R.font.…` — a generated resource id would not compile at
 * all without the files, which would make the licence a build dependency for
 * everyone who clones this.
 *
 * To enable it: drop `helvetica_now_condensed_regular.otf`, `…_medium.otf` and
 * `…_bold.otf` into `app/src/main/res/font/`. Nothing else changes. Without
 * them the app falls back to the system's condensed face, which is what every
 * build in this repository currently uses.
 *
 * Body copy is the system font on purpose. It is what the member's phone already
 * renders best at small sizes, and a condensed face is the wrong choice for a
 * paragraph.
 */
object TwtType {

    private const val REGULAR = "helvetica_now_condensed_regular"
    private const val MEDIUM = "helvetica_now_condensed_medium"
    private const val BOLD = "helvetica_now_condensed_bold"

    /**
     * The licensed family if its files are present, otherwise the system
     * condensed face — which is a genuinely close stand-in on Android, unlike
     * plain sans.
     */
    fun displayFamily(context: Context): FontFamily {
        val regular = fontId(context, REGULAR) ?: return FontFamily.SansSerif
        val medium = fontId(context, MEDIUM) ?: regular
        val bold = fontId(context, BOLD) ?: regular
        return FontFamily(
            Font(regular, FontWeight.Normal),
            Font(medium, FontWeight.Medium),
            Font(bold, FontWeight.Bold)
        )
    }

    val body: FontFamily = FontFamily.SansSerif

    @Suppress("DiscouragedApi") // Resolving by name is the entire point here.
    private fun fontId(context: Context, name: String): Int? =
        context.resources
            .getIdentifier(name, "font", context.packageName)
            .takeIf { it != 0 }
}

/**
 * Tabular figures, for anything that ticks in place or sits in a column.
 *
 * Without it a price updating from 1.0855 to 1.0866 shifts every digit sideways,
 * because proportional figures are not all the same width. In a signals feed
 * that is the difference between a live price and a jitter.
 */
const val TABULAR_FIGURES = "tnum"

fun twtTypography(display: FontFamily): Typography {
    val body = TwtType.body
    return Typography(
        displayLarge = TextStyle(
            fontFamily = display,
            fontWeight = FontWeight.Bold,
            fontSize = 44.sp,
            lineHeight = 48.sp,
            letterSpacing = (-0.5).sp
        ),
        displayMedium = TextStyle(
            fontFamily = display,
            fontWeight = FontWeight.Bold,
            fontSize = 34.sp,
            lineHeight = 40.sp,
            letterSpacing = (-0.25).sp
        ),
        headlineLarge = TextStyle(
            fontFamily = display,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            lineHeight = 34.sp
        ),
        headlineMedium = TextStyle(
            fontFamily = display,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            lineHeight = 28.sp
        ),
        titleLarge = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            lineHeight = 24.sp
        ),
        titleMedium = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp
        ),
        bodyLarge = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
            lineHeight = 22.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            lineHeight = 18.sp
        ),
        labelLarge = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 18.sp
        ),
        labelMedium = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.3.sp
        ),
        labelSmall = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.Medium,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            letterSpacing = 0.6.sp
        )
    )
}
