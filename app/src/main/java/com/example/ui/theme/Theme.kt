package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TVDarkColorScheme = darkColorScheme(
    primary = TVCyan,
    onPrimary = Color.Black,
    primaryContainer = TVCyanVariant,
    onPrimaryContainer = Color.White,
    secondary = TVAmber,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF78350F),
    onSecondaryContainer = TVAmberBright,
    background = TVNavyDark,
    onBackground = TextPrimary,
    surface = TVNavyCard,
    onSurface = TextPrimary,
    surfaceVariant = TVCardBorder,
    onSurfaceVariant = TextSecondary,
    error = TVRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = TVDarkColorScheme,
        typography = Typography,
        content = content
    )
}
