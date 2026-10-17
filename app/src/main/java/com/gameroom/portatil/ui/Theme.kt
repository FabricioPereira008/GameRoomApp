package com.gameroom.portatil.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Mesmos tokens do GameRoom desktop (frontend_desktop/styles/tokens.py)
object Tokens {
    val bg = Color(0xFF0B0C10)
    val surface = Color(0xFF13151C)
    val surface2 = Color(0xFF1A1D27)
    val text = Color(0xFFEEF0F6)
    val textMuted = Color(0xFF9AA1B5)
    val accent = Color(0xFF8B5CF6)
    val success = Color(0xFF34D399)
    val warning = Color(0xFFFBBF24)
    val danger = Color(0xFFF87171)
}

private val colors = darkColorScheme(
    primary = Tokens.accent,
    onPrimary = Color.White,
    secondary = Tokens.accent,
    background = Tokens.bg,
    onBackground = Tokens.text,
    surface = Tokens.surface,
    onSurface = Tokens.text,
    surfaceVariant = Tokens.surface2,
    onSurfaceVariant = Tokens.textMuted,
    surfaceContainer = Tokens.surface,
    surfaceContainerHigh = Tokens.surface2,
    surfaceContainerHighest = Tokens.surface2,
    error = Tokens.danger,
)

@Composable
fun GameRoomTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
