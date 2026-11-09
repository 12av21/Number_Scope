package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = CyberBgDark,
    primaryContainer = CyberSurfaceVariant,
    onPrimaryContainer = ElectricBlue,
    secondary = CyberViolet,
    onSecondary = TextPrimary,
    secondaryContainer = CyberVioletDim,
    onSecondaryContainer = TextPrimary,
    tertiary = CyberGreen,
    onTertiary = CyberBgDark,
    tertiaryContainer = CyberGreenDim,
    onTertiaryContainer = TextPrimary,
    background = CyberBg,
    onBackground = TextPrimary,
    surface = CyberSurface,
    onSurface = TextPrimary,
    surfaceVariant = CyberSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = CyberCardBorder,
    error = CyberRed,
    onError = TextPrimary
)

private val LightColorScheme = lightColorScheme(
    primary = ElectricBlueDim,
    onPrimary = LightSurface,
    primaryContainer = LightCardBorder,
    onPrimaryContainer = LightTextPrimary,
    secondary = CyberVioletDim,
    onSecondary = LightSurface,
    background = LightBg,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightCard,
    onSurfaceVariant = LightTextSecondary,
    outline = LightCardBorder,
    error = CyberRed,
    onError = LightSurface
)

@Composable
fun NumberScopeTheme(
    darkTheme: Boolean = true, // Cyber SOC dark theme by default
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
