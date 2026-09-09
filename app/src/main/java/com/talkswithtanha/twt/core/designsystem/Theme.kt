package com.talkswithtanha.twt.core.designsystem

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColors = darkColorScheme(
    primary = TwtColors.Gold,
    onPrimary = TwtColors.Background,
    primaryContainer = TwtColors.GoldWash,
    onPrimaryContainer = TwtColors.Gold,
    background = TwtColors.Background,
    onBackground = TwtColors.TextPrimary,
    surface = TwtColors.Surface,
    onSurface = TwtColors.TextPrimary,
    surfaceVariant = TwtColors.SurfaceElevated,
    onSurfaceVariant = TwtColors.TextSecondary,
    outline = TwtColors.HairlineStrong,
    outlineVariant = TwtColors.Hairline,
    error = TwtColors.Sell,
    onError = TwtColors.TextPrimary
)

private val LightColors = lightColorScheme(
    primary = TwtColors.GoldDim,
    onPrimary = TwtColors.LightSurface,
    background = TwtColors.LightBackground,
    onBackground = TwtColors.LightTextPrimary,
    surface = TwtColors.LightSurface,
    onSurface = TwtColors.LightTextPrimary,
    surfaceVariant = TwtColors.LightBackground,
    onSurfaceVariant = TwtColors.LightTextSecondary,
    outline = TwtColors.LightHairline,
    outlineVariant = TwtColors.LightHairline,
    error = TwtColors.Sell
)

/**
 * The app's theme.
 *
 * **Dynamic colour is deliberately absent.** Material You would repaint the
 * accent from the member's wallpaper; gold is the brand, and a signals app whose
 * buy/sell colours drift with a wallpaper is worse than one that ignores the
 * platform convention.
 */
@Composable
fun TwtTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val typography = remember(context) { twtTypography(TwtType.displayFamily(context)) }
    val colors = if (darkTheme) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Edge to edge: the app paints its own background behind the system
            // bars, which is what lets the bottom bar float 8dp above the
            // gesture pill rather than sitting in a letterboxed strip.
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = typography,
        content = content
    )
}
