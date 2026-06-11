package com.locationtracker.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF4FC3F7),
    onPrimary = Color(0xFF0D1B2A),
    primaryContainer = Color(0xFF0288D1),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF4CAF50),
    onSecondary = Color.White,
    background = Color(0xFF0D1B2A),
    onBackground = Color.White,
    surface = Color(0xFF1E2D3D),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1A2A3A),
    onSurfaceVariant = Color(0xFF8899AA),
    outline = Color(0xFF334455),
    error = Color(0xFFEF5350),
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0288D1),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE1F5FE),
    onPrimaryContainer = Color(0xFF0D1B2A),
    secondary = Color(0xFF388E3C),
    onSecondary = Color.White,
    background = Color(0xFFF5F9FF),
    onBackground = Color(0xFF0D1B2A),
    surface = Color.White,
    onSurface = Color(0xFF0D1B2A),
    outline = Color(0xFFCCDDEE)
)

@Composable
fun LocateMeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
