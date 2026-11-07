package com.m3urecorder.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Paleta oscura (la de la web antigua). Se sustituirá en el rediseño por temas claro y oscuro.
val BgBase = Color(0xFF090D16)
val BgSurface = Color(0xFF111827)
val BgCard = Color(0xFF161F33)
val BgInput = Color(0xFF0B1120)
val AccentBlue = Color(0xFF3B82F6)
val AccentEmerald = Color(0xFF10B981)
val AccentAmber = Color(0xFFF59E0B)
val AccentRose = Color(0xFFF43F5E)
val TextPrimary = Color(0xFFF1F5F9)
val TextSecondary = Color(0xFF94A3B8)

private val DarkColorPalette = darkColorScheme(
    primary = AccentBlue,
    secondary = AccentEmerald,
    tertiary = AccentAmber,
    background = BgBase,
    surface = BgSurface,
    surfaceVariant = BgCard,
    onPrimary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun M3UTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColorPalette, content = content)
}
