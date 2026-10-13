package com.kipat.probe.reveal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.kipat.probe.reveal.effects.RevealEffects.drawTemplateGlyph
import com.kipat.probe.reveal.effects.RevealInk
import com.kipat.probe.reveal.effects.RevealPaths
import com.kipat.probe.ui.theme.Kipat

/**
 * A static thumbnail of a template at its recognisable moment, drawn with the Reveal's own
 * primitives on a miniature card. Decorative: the row it sits in carries the name.
 */
@Composable
fun RevealTemplateGlyph(template: RevealTemplateId, modifier: Modifier = Modifier) {
    val colors = Kipat.colors
    val paths = remember { RevealPaths() }
    val ink = remember(colors) { RevealInk(colors.textPrimary, colors.accent, colors.surfaceElevated) }
    Canvas(
        modifier
            .size(72.dp, 52.dp)
            .clip(MaterialTheme.shapes.small)
            .background(colors.background)
            .border(1.dp, colors.divider, MaterialTheme.shapes.small),
    ) {
        drawTemplateGlyph(
            template = template,
            ink = ink,
            cardColor = if (colors.isDark) colors.surfaceElevated else colors.surface,
            avatarColor = colors.surfaceSecondary.takeIf { !colors.isDark } ?: colors.divider,
            edgeColor = colors.outline.copy(alpha = 0.45f),
            hair = 0.75.dp.toPx(),
            paths = paths,
        )
    }
}
