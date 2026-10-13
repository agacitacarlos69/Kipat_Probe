package com.kipat.probe.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.kipat.probe.model.StatusTone
import com.kipat.probe.reveal.RevealTheme
import com.kipat.probe.reveal.SurfaceTreatment

/**
 * Quiet Graphite palette. Values and measured contrast are documented in
 * design/system/foundation.md — change both together.
 */
internal object Palette {
    // Light
    val PaperBackground = Color(0xFFF6F6F4)
    val PaperSurface = Color(0xFFFFFFFF)
    val PaperSecondary = Color(0xFFEEEEEB)
    val PaperDivider = Color(0xFFE2E2DE)
    val PaperOutline = Color(0xFF8A8D95)
    val InkPrimary = Color(0xFF17181C)
    val InkSecondary = Color(0xFF4E525B)
    val InkTertiary = Color(0xFF6A6E78)
    val Cobalt = Color(0xFF2747C8)
    val CobaltContainer = Color(0xFFE5E9FA)
    val Verified = Color(0xFF16794A)
    val Warning = Color(0xFF8A5A00)
    val Revoked = Color(0xFFB42318)

    // Dark
    val GraphiteBackground = Color(0xFF131416)
    val GraphiteSurface = Color(0xFF1A1B1E)
    val GraphiteSecondary = Color(0xFF212226)
    val GraphiteElevated = Color(0xFF26272B)
    val GraphiteDivider = Color(0xFF2B2C30)
    val GraphiteOutline = Color(0xFF737680)
    val MistPrimary = Color(0xFFEDEDEF)
    val MistSecondary = Color(0xFFA9ACB3)
    val MistTertiary = Color(0xFF8B8F98)
    val CobaltLight = Color(0xFF9DB0FF)
    val CobaltDeep = Color(0xFF0F1A45)
    val CobaltContainerDark = Color(0xFF222B4D)
    val VerifiedDark = Color(0xFF5BC48C)
    val WarningDark = Color(0xFFE0A645)
    val RevokedDark = Color(0xFFF2766B)

    /**
     * Reveal backdrop over the previous app. Light is a grey a step darker than every light
     * surface, so the identity surface separates by tone, not by shadow alone.
     */
    val Scrim = Color(0xFF0B0C0E)
    val ScrimLight = Color(0xFFE4E4DF)

    // Reveal identity surfaces (design/reveal-surface/surface-directions.md). Text contrast on
    // each is measured ≥ 4.5:1 for primary, secondary and tertiary ink.
    val SurfacePaperLight = Color(0xFFFCFCFA)
    val SurfaceTonalLight = Color(0xFFF7F5F0)
    val SurfaceBorderLight = Color(0xFFDEDED9)
    val SurfaceFloatBorderLight = Color(0xFFE4E4E0)
    val SurfacePaperDark = Color(0xFF1F2023)
    val SurfaceTonalDark = Color(0xFF232428)
    val SurfaceBorderDark = Color(0xFF34363C)
}

/** Theme-independent colours: QR codes stay dark-on-white in every theme so all scanners read them. */
object KipatFixedColors {
    val QrInk = Palette.InkPrimary
    val QrPaper = Color.White
}

/** Kipat semantic colours. Screens read these (or MaterialTheme roles), never raw values. */
@Immutable
data class KipatColors(
    val background: Color,
    val surface: Color,
    val surfaceSecondary: Color,
    val surfaceElevated: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val divider: Color,
    /** Borders of interactive elements (secondary buttons, fields); ≥ 3:1 against surfaces. */
    val outline: Color,
    val accent: Color,
    val onAccent: Color,
    val accentContainer: Color,
    val verified: Color,
    val warning: Color,
    val revoked: Color,
    val isDark: Boolean,
) {
    fun tone(tone: StatusTone): Color = when (tone) {
        StatusTone.POSITIVE -> verified
        StatusTone.PENDING -> accent
        StatusTone.WARNING -> warning
        StatusTone.NEGATIVE -> revoked
        StatusTone.NEUTRAL -> textSecondary
    }
}

internal val LightKipatColors = KipatColors(
    background = Palette.PaperBackground,
    surface = Palette.PaperSurface,
    surfaceSecondary = Palette.PaperSecondary,
    surfaceElevated = Palette.PaperSurface,
    textPrimary = Palette.InkPrimary,
    textSecondary = Palette.InkSecondary,
    textTertiary = Palette.InkTertiary,
    divider = Palette.PaperDivider,
    outline = Palette.PaperOutline,
    accent = Palette.Cobalt,
    onAccent = Color.White,
    accentContainer = Palette.CobaltContainer,
    verified = Palette.Verified,
    warning = Palette.Warning,
    revoked = Palette.Revoked,
    isDark = false,
)

internal val DarkKipatColors = KipatColors(
    background = Palette.GraphiteBackground,
    surface = Palette.GraphiteSurface,
    surfaceSecondary = Palette.GraphiteSecondary,
    surfaceElevated = Palette.GraphiteElevated,
    textPrimary = Palette.MistPrimary,
    textSecondary = Palette.MistSecondary,
    textTertiary = Palette.MistTertiary,
    divider = Palette.GraphiteDivider,
    outline = Palette.GraphiteOutline,
    accent = Palette.CobaltLight,
    onAccent = Palette.CobaltDeep,
    accentContainer = Palette.CobaltContainerDark,
    verified = Palette.VerifiedDark,
    warning = Palette.WarningDark,
    revoked = Palette.RevokedDark,
    isDark = true,
)

/**
 * Reveal colours. Everything follows the system theme: a paper scrim with graphite ink in light,
 * a graphite scrim with mist ink in dark (design/reveal-templates/). The card is opaque for
 * legibility over any app. The accent comes from the person's chosen Reveal accent and appears only
 * at completion (orbit ring, contact mark) and on Connect.
 */
@Immutable
data class RevealPalette(
    val scrim: Color,
    /** Scrim opacity on the first frame and once the card has resolved. */
    val scrimBase: Float,
    val scrimPeak: Float,
    /** Text placed directly on the scrim (the preview label). */
    val onScrim: Color,
    /** Template primitives: arcs, rings, lines, blades. One ink, never a gradient. */
    val ink: Color,
    val accent: Color,
    val onAccent: Color,
    val card: KipatColors,
) {
    /** The identity surface's material for [treatment]: container, edge, depth and rule. */
    fun surface(treatment: SurfaceTreatment): SurfaceStyle {
        val dark = card.isDark
        return when (treatment) {
            SurfaceTreatment.PAPER -> if (dark) {
                SurfaceStyle(Palette.SurfacePaperDark, Palette.SurfaceBorderDark, 0f, 16f, Palette.SurfaceBorderDark)
            } else {
                SurfaceStyle(Palette.SurfacePaperLight, Palette.SurfaceBorderLight, 2f, 16f, Palette.SurfaceBorderLight)
            }
            SurfaceTreatment.TONAL -> if (dark) {
                SurfaceStyle(Palette.SurfaceTonalDark, Palette.SurfaceBorderDark, 0f, 16f, Palette.SurfaceBorderDark)
            } else {
                SurfaceStyle(Palette.SurfaceTonalLight, null, 6f, 16f, Palette.PaperDivider)
            }
            // Outline tokens: ≥ 3:1 against the surface (3.23 light, 3.6 dark).
            SurfaceTreatment.STRUCTURAL -> if (dark) {
                SurfaceStyle(Palette.SurfacePaperDark, Palette.GraphiteOutline, 0f, 12f, Palette.GraphiteOutline, fullBleedRule = true)
            } else {
                SurfaceStyle(Palette.SurfacePaperLight, Palette.PaperOutline, 0f, 12f, Palette.PaperOutline, fullBleedRule = true)
            }
            SurfaceTreatment.FLOATING -> if (dark) {
                SurfaceStyle(Palette.GraphiteElevated, Palette.SurfaceBorderDark, 0f, 16f, Palette.SurfaceBorderDark)
            } else {
                SurfaceStyle(Color.White, Palette.SurfaceFloatBorderLight, 16f, 16f, Palette.SurfaceBorderLight)
            }
        }
    }

    companion object {
        fun of(theme: RevealTheme, dark: Boolean): RevealPalette {
            val card = if (dark) DarkKipatColors else LightKipatColors
            val (accent, onAccent) = when (theme) {
                RevealTheme.NIGHT -> card.accent to card.onAccent
                RevealTheme.AURORA -> if (dark) Color(0xFF6FD3BF) to Color(0xFF00201A) else Color(0xFF0B6E5F) to Color.White
                RevealTheme.EMBER -> if (dark) Color(0xFFF0B07A) to Color(0xFF2E1400) else Color(0xFF9C4A0B) to Color.White
                RevealTheme.MONO -> if (dark) Color(0xFFE3E4E8) to Color(0xFF17181C) else Color(0xFF2B2D33) to Color.White
            }
            return if (dark) {
                RevealPalette(Palette.Scrim, 0.62f, 0.88f, Palette.MistPrimary, Palette.MistPrimary, accent, onAccent, card)
            } else {
                RevealPalette(Palette.ScrimLight, 0.72f, 0.92f, Palette.InkPrimary, Palette.InkPrimary, accent, onAccent, card)
            }
        }
    }
}

/** One identity-surface material. Dimensions are dp; [border] null means no edge line. */
@Immutable
data class SurfaceStyle(
    val container: Color,
    val border: Color?,
    /** Light-mode shadow elevation; dark surfaces separate by tone and edge instead. */
    val shadowDp: Float,
    val radiusDp: Float,
    /** The rule between identity and actions. */
    val rule: Color,
    /** STRUCTURAL draws the rule edge to edge; others inset it to the content. */
    val fullBleedRule: Boolean = false,
)
