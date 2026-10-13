package com.kipat.probe.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kipat.probe.model.AuthenticityState
import com.kipat.probe.model.present
import com.kipat.probe.ui.theme.Kipat
import com.kipat.probe.ui.theme.KipatColors
import com.kipat.probe.ui.theme.KipatSpacing
import com.kipat.probe.ui.theme.KipatType

/** Initials on a quiet neutral disc. No rings, no gradients; photos can replace the initials later. */
@Composable
fun KipatAvatar(
    initials: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    colors: KipatColors = Kipat.colors,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.surfaceSecondary),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials.take(2),
            color = colors.textSecondary,
            style = if (size >= 56.dp) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
        )
    }
}

/**
 * Trust stated calmly: a small glyph and plain words in the state's colour. Demo data is a
 * separate tertiary line. Exposed to accessibility as "Authenticity: <label>. <detail>".
 */
@Composable
fun TrustLine(
    state: AuthenticityState,
    isDemo: Boolean,
    modifier: Modifier = Modifier,
    colors: KipatColors = Kipat.colors,
    showDemoNote: Boolean = true,
) {
    val p = state.present(isDemo)
    val tint = colors.tone(p.tone)
    Column(modifier.clearAndSetSemantics { contentDescription = "Authenticity: ${p.label}. ${p.detail}" }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                imageVector = when {
                    p.showsCheck -> KipatSymbols.VerifiedFilled
                    state == AuthenticityState.REVOKED || state == AuthenticityState.ERROR -> KipatSymbols.Revoked
                    state == AuthenticityState.UNVERIFIED -> KipatSymbols.Warning
                    else -> KipatSymbols.Info
                },
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp),
            )
            Text(p.label, style = MaterialTheme.typography.bodyMedium, color = tint)
        }
        if (showDemoNote && p.demoNote != null) {
            Text(p.demoNote, style = MaterialTheme.typography.bodySmall, color = colors.textTertiary, modifier = Modifier.padding(start = 22.dp, top = 2.dp))
        }
    }
}

/** Per-slot reveal progress (0..1), read in draw/layer phases so animation skips recomposition. */
@Immutable
data class IdentitySlots(
    val avatar: Float = 1f,
    val name: Float = 1f,
    val lines: Float = 1f,
    val trust: Float = 1f,
) {
    companion object {
        val Revealed = IdentitySlots()
    }
}

private val RevealedSlots: () -> IdentitySlots = { IdentitySlots.Revealed }

private fun Modifier.fadeRise(rise: Dp = 6.dp, progress: () -> Float) = graphicsLayer {
    val p = progress()
    alpha = p
    translationY = (1f - p) * rise.toPx()
}

/** Left-to-right clip reveal (adopted from the Higgsfield v2 motion reference for the name). */
private fun Modifier.wipe(progress: () -> Float) = drawWithContent {
    val p = progress()
    if (p >= 1f) drawContent() else if (p > 0f) clipRect(right = size.width * p) { this@drawWithContent.drawContent() }
}

/**
 * The person: avatar, name (the largest type), role, organisation, trust. Used on the page with no
 * container (Home, Person) and inside [RecipientCard] (Profile preview, Reveal).
 */
@Composable
fun IdentityBlock(
    displayName: String,
    subtitle: String?,
    organization: String?,
    initials: String,
    authenticity: AuthenticityState,
    isDemo: Boolean,
    modifier: Modifier = Modifier,
    colors: KipatColors = Kipat.colors,
    avatarSize: Dp = 64.dp,
    slots: () -> IdentitySlots = RevealedSlots,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            KipatAvatar(
                initials,
                size = avatarSize,
                colors = colors,
                modifier = Modifier
                    .graphicsLayer {
                        val p = slots().avatar
                        alpha = p
                        val s = 0.92f + 0.08f * p
                        scaleX = s; scaleY = s
                    }
                    .semantics { contentDescription = "Photo placeholder for $displayName" },
            )
            Spacer(Modifier.weight(1f))
            trailing?.invoke()
        }
        Spacer(Modifier.height(KipatSpacing.s16))
        Text(
            text = displayName,
            style = KipatType.identityName,
            color = colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .semantics { heading() }
                .graphicsLayer { alpha = (slots().name * 1.6f).coerceAtMost(1f) }
                .wipe { slots().name },
        )
        val lines = listOfNotNull(subtitle?.takeIf { it.isNotBlank() }, organization?.takeIf { it.isNotBlank() })
        if (lines.isNotEmpty()) {
            Spacer(Modifier.height(KipatSpacing.s4))
            Column(Modifier.fadeRise { slots().lines }) {
                lines.forEachIndexed { i, line ->
                    Text(
                        text = line,
                        style = if (i == 0) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.height(KipatSpacing.s12))
        TrustLine(authenticity, isDemo, colors = colors, modifier = Modifier.fadeRise(rise = 0.dp) { slots().trust })
    }
}

/**
 * The only card in the shell: what a recipient sees. Opaque elevated surface, 16 dp radius,
 * hairline border, content on a single left-aligned grid (Higgsfield v2 board, variant 1/3).
 */
@Composable
fun RecipientCard(
    modifier: Modifier = Modifier,
    colors: KipatColors = Kipat.colors,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.surfaceElevated)
            .border(1.dp, if (colors.isDark) Color.Transparent else colors.divider, MaterialTheme.shapes.large)
            .padding(KipatSpacing.s24),
    ) { content() }
}
