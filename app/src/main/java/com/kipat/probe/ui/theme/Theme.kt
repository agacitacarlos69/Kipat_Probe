package com.kipat.probe.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private fun KipatColors.toScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentContainer,
        onPrimaryContainer = textPrimary,
        secondary = textSecondary,
        onSecondary = background,
        secondaryContainer = accentContainer,
        onSecondaryContainer = textPrimary,
        background = background,
        onBackground = textPrimary,
        surface = background,
        onSurface = textPrimary,
        surfaceVariant = surfaceSecondary,
        onSurfaceVariant = textSecondary,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceElevated,
        surfaceContainerHighest = surfaceSecondary,
        surfaceTint = background,
        outline = outline,
        outlineVariant = divider,
        error = revoked,
        onError = background,
    )
}

val LocalKipatColors = staticCompositionLocalOf { LightKipatColors }

@Composable
fun KipatTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkKipatColors else LightKipatColors
    CompositionLocalProvider(LocalKipatColors provides colors) {
        MaterialTheme(
            colorScheme = colors.toScheme(),
            typography = KipatTypography,
            shapes = KipatShapes,
            content = content,
        )
    }
}

/** `Kipat.colors.accent` etc. */
object Kipat {
    val colors: KipatColors
        @Composable @ReadOnlyComposable get() = LocalKipatColors.current
}
