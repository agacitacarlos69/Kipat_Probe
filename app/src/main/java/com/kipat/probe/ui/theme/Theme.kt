package com.kipat.probe.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = Palette.Signal,
    onPrimary = Palette.Ink950,
    primaryContainer = Color(0xFF0E3A42),
    onPrimaryContainer = Color(0xFFB9F6FC),
    secondary = Palette.Halo,
    onSecondary = Palette.Ink950,
    secondaryContainer = Color(0xFF29234F),
    onSecondaryContainer = Color(0xFFE2DDFF),
    background = Palette.Ink900,
    onBackground = Palette.Ink50,
    surface = Palette.Ink900,
    onSurface = Palette.Ink50,
    surfaceVariant = Palette.Ink800,
    onSurfaceVariant = Palette.Ink300,
    surfaceContainerLowest = Palette.Ink950,
    surfaceContainerLow = Palette.Ink850,
    surfaceContainer = Palette.Ink850,
    surfaceContainerHigh = Palette.Ink800,
    surfaceContainerHighest = Palette.Ink700,
    outline = Palette.Ink600,
    outlineVariant = Palette.Ink700,
    error = Palette.Rose,
    onError = Palette.Ink950,
)

private val LightScheme = lightColorScheme(
    primary = Palette.SignalDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3F6FA),
    onPrimaryContainer = Color(0xFF00363E),
    secondary = Palette.HaloDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6E1FF),
    onSecondaryContainer = Color(0xFF1B0D6B),
    background = Palette.Paper,
    onBackground = Palette.Ink900,
    surface = Palette.Paper,
    onSurface = Palette.Ink900,
    surfaceVariant = Color(0xFFEAEDF3),
    onSurfaceVariant = Color(0xFF4A5368),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Palette.PaperRaised,
    surfaceContainer = Palette.PaperRaised,
    surfaceContainerHigh = Color(0xFFEFF1F6),
    surfaceContainerHighest = Color(0xFFE6E9F0),
    outline = Color(0xFFC3C8D4),
    outlineVariant = Palette.PaperLine,
    error = Palette.RoseDeep,
    onError = Color.White,
)

val LocalKipatColors = staticCompositionLocalOf { DarkKipatColors }

@Composable
fun KipatTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val kipatColors = if (darkTheme) DarkKipatColors else LightKipatColors
    CompositionLocalProvider(LocalKipatColors provides kipatColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = KipatTypography,
            shapes = KipatShapes,
            content = content,
        )
    }
}

/** Convenience accessor: `Kipat.colors.accent`. */
object Kipat {
    val colors: KipatColors
        @Composable @ReadOnlyComposable get() = LocalKipatColors.current
}
