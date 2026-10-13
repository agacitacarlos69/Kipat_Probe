package com.kipat.probe.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kipat.probe.model.AuthenticityState
import com.kipat.probe.ui.theme.Kipat
import com.kipat.probe.ui.theme.KipatRadius
import com.kipat.probe.ui.theme.KipatSpacing
import com.kipat.probe.ui.theme.RevealPalette

/** Initials avatar with a soft two-tone ring. Real images can slot in later via the same API. */
@Composable
fun KipatAvatar(
    initials: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    ring: Color = Kipat.colors.accent,
    ringSecondary: Color = Kipat.colors.accentSecondary,
    fill: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    text: Color = Kipat.colors.textPrimary,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(ring, ringSecondary)))
            .padding(if (size >= 56.dp) 2.dp else 1.5.dp)
            .clip(CircleShape)
            .background(fill),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials.take(2),
            color = text,
            style = if (size >= 64.dp) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleSmall,
        )
    }
}

/** Visual style for an identity card; lets the Reveal reuse the card with its own palette. */
@Immutable
data class IdentityCardStyle(
    val surface: Color,
    val border: Color,
    val text: Color,
    val textMuted: Color,
    val accent: Color,
    val accentSecondary: Color,
    val avatarFill: Color,
) {
    companion object {
        @Composable
        fun app() = IdentityCardStyle(
            surface = Kipat.colors.glass,
            border = Kipat.colors.glassBorder,
            text = Kipat.colors.textPrimary,
            textMuted = Kipat.colors.textSecondary,
            accent = Kipat.colors.accent,
            accentSecondary = Kipat.colors.accentSecondary,
            avatarFill = MaterialTheme.colorScheme.surfaceContainerHighest,
        )

        fun reveal(p: RevealPalette) = IdentityCardStyle(
            surface = p.cardSurface,
            border = p.cardBorder,
            text = p.text,
            textMuted = p.textMuted,
            accent = p.primary,
            accentSecondary = p.secondary,
            avatarFill = Color(0xFF0B0E15),
        )
    }
}

/**
 * Per-slot reveal progress (0..1). The app shows a fully revealed card; the Reveal drives these
 * from its timeline so the exact same layout is used everywhere.
 */
@Immutable
data class IdentityCardSlots(
    val avatar: Float = 1f,
    val name: Float = 1f,
    val role: Float = 1f,
    val badge: Float = 1f,
) {
    companion object {
        val Revealed = IdentityCardSlots()
    }
}

/** Reads progress inside the graphics layer so per-frame Reveal updates skip recomposition. */
private fun Modifier.slot(rise: Dp = 10.dp, progress: () -> Float): Modifier = graphicsLayer {
    val p = progress()
    alpha = p
    translationY = (1f - p) * rise.toPx()
}

private val RevealedSlots: () -> IdentityCardSlots = { IdentityCardSlots.Revealed }

/**
 * The Kipat identity card: avatar/logo, name, role/program, organization, authenticity.
 * [actions] renders under the identity (Reveal CTAs, "Preview Reveal" on Home).
 */
@Composable
fun KipatIdentityCard(
    displayName: String,
    subtitle: String?,
    organization: String?,
    initials: String,
    authenticity: AuthenticityState,
    isDemo: Boolean,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    style: IdentityCardStyle = IdentityCardStyle.app(),
    slots: () -> IdentityCardSlots = RevealedSlots,
    centered: Boolean = false,
    actions: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(KipatRadius.xl)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(style.surface, style.surface.copy(alpha = style.surface.alpha * 0.82f))))
            .border(1.dp, Brush.linearGradient(listOf(style.accent.copy(alpha = 0.45f), style.border, style.accentSecondary.copy(alpha = 0.25f))), shape)
            .padding(KipatSpacing.xl),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        if (eyebrow != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(KipatIcons.Mark, contentDescription = null, tint = style.accent, modifier = Modifier.size(14.dp))
                Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall, color = style.textMuted)
            }
            Spacer(Modifier.height(KipatSpacing.lg))
        }
        KipatAvatar(
            initials = initials,
            size = 64.dp,
            ring = style.accent,
            ringSecondary = style.accentSecondary,
            fill = style.avatarFill,
            text = style.text,
            modifier = Modifier
                .graphicsLayer {
                    val a = slots().avatar
                    alpha = a
                    val s = 0.8f + 0.2f * a
                    scaleX = s; scaleY = s
                }
                .semantics { contentDescription = "Avatar for $displayName" },
        )
        Spacer(Modifier.height(KipatSpacing.lg))
        Text(
            text = displayName,
            style = MaterialTheme.typography.headlineSmall,
            color = style.text,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.slot { slots().name },
        )
        val lines = listOfNotNull(subtitle?.takeIf { it.isNotBlank() }, organization?.takeIf { it.isNotBlank() })
        if (lines.isNotEmpty()) {
            Spacer(Modifier.height(KipatSpacing.xs))
            Column(Modifier.slot { slots().role }, horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start) {
                lines.forEachIndexed { i, line ->
                    Text(
                        text = line,
                        style = if (i == 0) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                        color = if (i == 0) style.text.copy(alpha = 0.86f) else style.textMuted,
                        textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.height(KipatSpacing.lg))
        KipatStatusBadge(authenticity, isDemo, modifier = Modifier.slot(rise = 0.dp) { slots().badge })
        if (actions != null) {
            Spacer(Modifier.height(KipatSpacing.xl))
            actions()
        }
    }
}
