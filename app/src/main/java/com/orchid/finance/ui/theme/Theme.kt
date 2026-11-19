package com.orchid.finance.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = Gold,
    onPrimary = Navy800,
    primaryContainer = GoldDim,
    onPrimaryContainer = GoldSoft,
    secondary = GoldSoft,
    onSecondary = Navy800,
    background = Navy900,
    onBackground = TextPrimary,
    surface = Navy700,
    onSurface = TextPrimary,
    surfaceVariant = Navy600,
    onSurfaceVariant = Muted,
    outline = Line,
    error = Red,
    onError = Color.White
)

@Composable
fun OrchidTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkScheme,
        typography = Typography(),
        content = content
    )
}
