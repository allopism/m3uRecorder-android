package com.m3urecorder.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Tema elegido en Ajustes. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Colores de la app: los mismos "tokens" que la web (static/css/app.css), para que
 * las dos se vean como un mismo producto.
 */
@Immutable
data class AppColors(
    val isDark: Boolean,
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val border: Color,
    val borderStrong: Color,
    val text: Color,
    val text2: Color,
    val text3: Color,
    val accent: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val rec: Color,
    val recSoft: Color,
    val ok: Color,
    val warn: Color,
    val warnSoft: Color,
    /** Fondo de los logos: claro también en modo oscuro, porque muchos logos son oscuros. */
    val logoBg: Color,
    // Monogramas (canales sin logo): luminosidad del fondo, del texto y saturación (HSL)
    val monoBgLightness: Float,
    val monoTextLightness: Float,
    val monoSaturation: Float,
)

private val Light = AppColors(
    isDark = false,
    bg = Color(0xFFF5F6F8),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF0F1F4),
    border = Color(0x1A0F172A),
    borderStrong = Color(0x330F172A),
    text = Color(0xFF15171C),
    text2 = Color(0xFF565D6B),
    text3 = Color(0xFF8A909C),
    accent = Color(0xFF3B5BDB),
    accentSoft = Color(0x1A3B5BDB),
    onAccent = Color(0xFFFFFFFF),
    rec = Color(0xFFE03131),
    recSoft = Color(0x17E03131),
    ok = Color(0xFF2B8A3E),
    warn = Color(0xFFC76A00),
    warnSoft = Color(0x1AE67700),
    logoBg = Color(0xFFF1F2F5),
    monoBgLightness = 0.92f,
    monoTextLightness = 0.30f,
    monoSaturation = 0.55f,
)

private val Dark = AppColors(
    isDark = true,
    bg = Color(0xFF0E1014),
    surface = Color(0xFF161920),
    surface2 = Color(0xFF1E222B),
    border = Color(0x14FFFFFF),
    borderStrong = Color(0x29FFFFFF),
    text = Color(0xFFE9EBEF),
    text2 = Color(0xFFA3A9B5),
    text3 = Color(0xFF737985),
    accent = Color(0xFF7B96FF),
    accentSoft = Color(0x247B96FF),
    onAccent = Color(0xFF0E1014),
    rec = Color(0xFFFF6B6B),
    recSoft = Color(0x21FF6B6B),
    ok = Color(0xFF69DB7C),
    warn = Color(0xFFFFB057),
    warnSoft = Color(0x1FFFB057),
    logoBg = Color(0xFFE7E9ED),
    monoBgLightness = 0.24f,
    monoTextLightness = 0.82f,
    monoSaturation = 0.35f,
)

private val LocalAppColors = staticCompositionLocalOf { Light }

/** Acceso a los colores desde cualquier pantalla: `AppTheme.colors.accent`. */
object AppTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable get() = LocalAppColors.current
}

private val AppTypography = Typography(
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.5.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun M3UTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val c = if (dark) Dark else Light
    // Los componentes de Material (campos, menús, diálogos…) toman estos mismos colores
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.accent, onPrimary = c.onAccent, primaryContainer = c.accentSoft, onPrimaryContainer = c.accent,
            secondary = c.accent, onSecondary = c.onAccent,
            secondaryContainer = c.accentSoft, onSecondaryContainer = c.text,
            background = c.bg, onBackground = c.text,
            surface = c.surface, onSurface = c.text, surfaceVariant = c.surface2, onSurfaceVariant = c.text2,
            surfaceContainerLowest = c.bg, surfaceContainerLow = c.surface, surfaceContainer = c.surface,
            surfaceContainerHigh = c.surface2, surfaceContainerHighest = c.surface2,
            outline = c.borderStrong, outlineVariant = c.border,
            error = c.rec, onError = c.onAccent, errorContainer = c.recSoft, onErrorContainer = c.rec,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = c.onAccent, primaryContainer = c.accentSoft, onPrimaryContainer = c.accent,
            secondary = c.accent, onSecondary = c.onAccent,
            secondaryContainer = c.accentSoft, onSecondaryContainer = c.text,
            background = c.bg, onBackground = c.text,
            surface = c.surface, onSurface = c.text, surfaceVariant = c.surface2, onSurfaceVariant = c.text2,
            surfaceContainerLowest = c.surface, surfaceContainerLow = c.surface, surfaceContainer = c.surface,
            surfaceContainerHigh = c.surface, surfaceContainerHighest = c.surface2,
            outline = c.borderStrong, outlineVariant = c.border,
            error = c.rec, onError = c.onAccent, errorContainer = c.recSoft, onErrorContainer = c.rec,
        )
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalAppColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}
