package com.kipat.probe.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.kipat.probe.model.StatusTone
import com.kipat.probe.reveal.RevealTheme

/** Raw palette. Screens should use [KipatColors] / MaterialTheme roles, not these directly. */
internal object Palette {
    // Ink (dark neutrals, slightly blue)
    val Ink950 = Color(0xFF06080D)
    val Ink900 = Color(0xFF0B0E15)
    val Ink850 = Color(0xFF10141D)
    val Ink800 = Color(0xFF161B26)
    val Ink700 = Color(0xFF212838)
    val Ink600 = Color(0xFF323B4F)
    val Ink400 = Color(0xFF6B7489)
    val Ink300 = Color(0xFF98A1B5)
    val Ink100 = Color(0xFFE3E7EF)
    val Ink50 = Color(0xFFF1F3F8)

    // Paper (light neutrals)
    val Paper = Color(0xFFF5F6FA)
    val PaperRaised = Color(0xFFFFFFFF)
    val PaperLine = Color(0xFFE2E5ED)

    // Accents — "Signal" cyan and "Halo" violet
    val Signal = Color(0xFF5EE6F2)
    val SignalDeep = Color(0xFF0B8496)
    val Halo = Color(0xFF9B8CFF)
    val HaloDeep = Color(0xFF5845D6)

    // Status
    val Mint = Color(0xFF5BE3A8)
    val MintDeep = Color(0xFF0F8A5A)
    val Amber = Color(0xFFF4BC62)
    val AmberDeep = Color(0xFF9A6200)
    val Rose = Color(0xFFFF7A88)
    val RoseDeep = Color(0xFFC0313F)
}

/** Kipat semantic colours that Material 3's scheme doesn't cover. */
@Immutable
data class KipatColors(
    val accent: Color,
    val accentSecondary: Color,
    val onAccent: Color,
    val glass: Color,
    val glassBorder: Color,
    val glow: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val divider: Color,
    val authentic: Color,
    val pending: Color,
    val warning: Color,
    val revoked: Color,
    val inactive: Color,
    val isDark: Boolean,
) {
    fun tone(tone: StatusTone): Color = when (tone) {
        StatusTone.POSITIVE -> authentic
        StatusTone.PENDING -> pending
        StatusTone.WARNING -> warning
        StatusTone.NEGATIVE -> revoked
        StatusTone.NEUTRAL -> inactive
    }
}

internal val DarkKipatColors = KipatColors(
    accent = Palette.Signal,
    accentSecondary = Palette.Halo,
    onAccent = Palette.Ink950,
    glass = Color(0x1AFFFFFF),
    glassBorder = Color(0x26FFFFFF),
    glow = Palette.Signal,
    textPrimary = Palette.Ink50,
    textSecondary = Palette.Ink300,
    textTertiary = Palette.Ink400,
    divider = Color(0x14FFFFFF),
    authentic = Palette.Mint,
    pending = Palette.Signal,
    warning = Palette.Amber,
    revoked = Palette.Rose,
    inactive = Palette.Ink400,
    isDark = true,
)

internal val LightKipatColors = KipatColors(
    accent = Palette.SignalDeep,
    accentSecondary = Palette.HaloDeep,
    onAccent = Color.White,
    glass = Color(0xB3FFFFFF),
    glassBorder = Color(0x14000000),
    glow = Palette.Signal,
    textPrimary = Palette.Ink900,
    textSecondary = Color(0xFF4A5368),
    textTertiary = Color(0xFF6B7489),
    divider = Palette.PaperLine,
    authentic = Palette.MintDeep,
    pending = Palette.SignalDeep,
    warning = Palette.AmberDeep,
    revoked = Palette.RoseDeep,
    inactive = Color(0xFF6B7489),
    isDark = false,
)

/** Colours for the Reveal overlay. The Reveal is always dark so it reads over any app. */
@Immutable
data class RevealPalette(
    val scrimTop: Color,
    val scrimBottom: Color,
    val primary: Color,
    val secondary: Color,
    val cardSurface: Color,
    val cardBorder: Color,
    val text: Color,
    val textMuted: Color,
) {
    companion object {
        fun of(theme: RevealTheme): RevealPalette = when (theme) {
            RevealTheme.NIGHT -> RevealPalette(Color(0xCC05070C), Color(0xF205070C), Palette.Signal, Palette.Halo, Color(0xD9121722), Color(0x33A9F3FF), Palette.Ink50, Palette.Ink300)
            RevealTheme.AURORA -> RevealPalette(Color(0xCC03100F), Color(0xF2040C10), Color(0xFF6CF2C9), Color(0xFF6FB6FF), Color(0xD90E1A1C), Color(0x3393FFE0), Palette.Ink50, Color(0xFF9FB7B3))
            RevealTheme.EMBER -> RevealPalette(Color(0xCC0E0806), Color(0xF20C0807), Color(0xFFFFB86B), Color(0xFFFF7A9A), Color(0xD91C1412), Color(0x33FFD2A8), Palette.Ink50, Color(0xFFBFA9A0))
            RevealTheme.MONO -> RevealPalette(Color(0xCC08080A), Color(0xF208080A), Color(0xFFE9ECF2), Color(0xFF8E96A8), Color(0xD9141519), Color(0x33FFFFFF), Palette.Ink50, Palette.Ink300)
        }
    }
}
