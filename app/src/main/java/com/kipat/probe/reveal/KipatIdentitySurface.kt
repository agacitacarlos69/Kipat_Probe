package com.kipat.probe.reveal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.kipat.probe.model.AuthenticityState
import com.kipat.probe.model.present
import com.kipat.probe.ui.components.IdentitySlots
import com.kipat.probe.ui.components.KipatSymbols
import com.kipat.probe.ui.theme.KipatColors
import com.kipat.probe.ui.theme.RevealPalette
import com.kipat.probe.ui.theme.SurfaceStyle

/** What the recipient can do. Null actions on [KipatIdentitySurface] = identity only (Profile preview). */
@Immutable
data class SurfaceActions(
    val connected: Boolean,
    /** False until the actions have faded in: taps are ignored, but nothing greys out mid-entrance. */
    val ready: () -> Boolean,
    val onConnect: () -> Unit,
    val onSaveContact: () -> Unit,
    val onOpenProfile: () -> Unit,
    val onDone: () -> Unit,
)

/**
 * The canonical Kipat identity surface: the object every Reveal template settles into, and what
 * Profile shows as "How others see you" (design/reveal-surface/).
 *
 * Reads in one column: identity (anchor + name), context, trust, then attached actions. Height is
 * the content's; nothing is fixed. Per-slot progress ([slots], [actionsProgress]) is read in
 * draw/layer phases so the entrance never recomposes per frame.
 */
@Composable
fun KipatIdentitySurface(
    payload: RevealPayload,
    presentation: RevealPresentation,
    palette: RevealPalette,
    modifier: Modifier = Modifier,
    actions: SurfaceActions? = null,
    onDismiss: (() -> Unit)? = null,
    avatar: ImageBitmap? = null,
    wipeName: Boolean = false,
    slots: () -> IdentitySlots = { IdentitySlots.Revealed },
    actionsProgress: () -> Float = { 1f },
    /** On a shell screen there is no backdrop or shadow: always draw an edge so the surface holds. */
    inShell: Boolean = false,
) {
    val style = palette.surface(presentation.treatment).let { if (inShell && it.border == null) it.copy(border = it.rule) else it }
    val colors = palette.card
    val shape = RoundedCornerShape(style.radiusDp.dp)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(style.container)
            .then(if (style.border != null) Modifier.border(1.dp, style.border, shape) else Modifier),
    ) {
        val pad = SurfaceMetrics.paddingDp(maxWidth.value).dp
        Column(Modifier.fillMaxWidth().padding(top = pad)) {
            IdentityHeader(
                payload = payload,
                layout = presentation.layout,
                palette = palette,
                surface = style.container,
                avatar = avatar,
                wipeName = wipeName,
                slots = slots,
                reserveEnd = onDismiss != null,
                modifier = Modifier.padding(horizontal = pad),
            )
            Spacer(Modifier.height(16.dp))
            AuthenticityRow(
                state = payload.authenticity,
                isDemo = payload.isDemo,
                colors = colors,
                style = style,
                accent = palette.accent,
                modifier = Modifier
                    .padding(horizontal = pad)
                    .layerAlpha { slots().trust },
            )
            if (actions == null) {
                Spacer(Modifier.height(pad))
            } else {
                Spacer(Modifier.height(20.dp))
                // The rule belongs to the actions: it arrives with them, so nothing waits empty.
                HorizontalDivider(
                    thickness = 1.dp,
                    color = style.rule,
                    modifier = (if (style.fullBleedRule) Modifier else Modifier.padding(horizontal = pad))
                        .graphicsLayer { alpha = actionsProgress() },
                )
                Spacer(Modifier.height(20.dp))
                SurfaceActionsBlock(
                    payload = payload,
                    actions = actions,
                    palette = palette,
                    surface = style.container,
                    modifier = Modifier
                        .padding(horizontal = pad)
                        .padding(bottom = 8.dp)
                        .graphicsLayer {
                            val p = actionsProgress()
                            alpha = p
                            translationY = (1f - p) * 6.dp.toPx()
                        },
                )
            }
        }
        if (onDismiss != null) {
            // Same corner in every layout; quiet, but a full 48 dp target.
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                Icon(KipatSymbols.Close, contentDescription = "Close", tint = colors.textTertiary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun IdentityHeader(
    payload: RevealPayload,
    layout: IdentityLayout,
    palette: RevealPalette,
    surface: Color,
    avatar: ImageBitmap?,
    wipeName: Boolean,
    slots: () -> IdentitySlots,
    reserveEnd: Boolean,
    modifier: Modifier = Modifier,
) {
    val mark: @Composable () -> Unit = {
        IdentityMark(
            initials = payload.initials,
            displayName = payload.displayName,
            size = layout.avatarDp.dp,
            isOrganization = payload.isOrganization,
            image = avatar,
            accent = palette.accent,
            surface = surface,
            modifier = Modifier.graphicsLayer {
                val p = slots().avatar
                alpha = p
                val s = 0.92f + 0.08f * p
                scaleX = s; scaleY = s
            },
        )
    }
    val text: @Composable (Modifier) -> Unit = { m ->
        Column(m) {
            IdentityName(payload.displayName, layout, palette.card, wipeName, slots)
            IdentityContext(payload.subtitle, payload.organization, palette.card, Modifier.layerAlpha(rise = 4.dp) { slots().lines })
        }
    }
    when (layout) {
        IdentityLayout.PROFILE_HEADER -> Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            mark()
            Spacer(Modifier.width(16.dp))
            // The close button sits in the top-end corner; keep the name clear of it.
            text(Modifier.weight(1f).padding(top = 2.dp, end = if (reserveEnd) 28.dp else 0.dp))
        }
        IdentityLayout.COMPACT, IdentityLayout.EDITORIAL -> Column(modifier.fillMaxWidth()) {
            mark()
            Spacer(Modifier.height(if (layout == IdentityLayout.EDITORIAL) 20.dp else 16.dp))
            text(Modifier)
        }
    }
}

@Composable
private fun IdentityName(name: String, layout: IdentityLayout, colors: KipatColors, wipe: Boolean, slots: () -> IdentitySlots) {
    Text(
        text = name,
        style = TextStyle(fontSize = layout.nameSp.sp, lineHeight = layout.nameLineSp.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.01).em),
        color = colors.textPrimary,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .semantics { heading() }
            .then(
                if (wipe) {
                    Modifier
                        .graphicsLayer { alpha = (slots().name * 1.6f).coerceAtMost(1f) }
                        .drawWithContent {
                            val p = slots().name
                            if (p >= 1f) drawContent() else if (p > 0f) clipRect(right = size.width * p) { this@drawWithContent.drawContent() }
                        }
                } else {
                    Modifier.layerAlpha(rise = 6.dp) { slots().name }
                },
            ),
    )
}

/** What they do, then where: role in 16 sp, organisation quieter in 14 sp. Wraps rather than clips. */
@Composable
private fun IdentityContext(role: String?, organization: String?, colors: KipatColors, modifier: Modifier = Modifier) {
    val r = role?.takeIf { it.isNotBlank() }
    val o = organization?.takeIf { it.isNotBlank() }
    if (r == null && o == null) return
    Column(modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        r?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        o?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}

/**
 * The identity anchor: a photo, an organisation's logo (rounded square), or a designed monogram —
 * the person's accent at 12% with the initials in the accent, never a grey placeholder disc.
 */
@Composable
fun IdentityMark(
    initials: String,
    displayName: String,
    size: Dp,
    isOrganization: Boolean,
    image: ImageBitmap?,
    accent: Color,
    surface: Color,
    modifier: Modifier = Modifier,
) {
    val shape: Shape = if (isOrganization) RoundedCornerShape(size * 0.24f) else CircleShape
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(accent.copy(alpha = 0.12f).compositeOver(surface))
            .then(
                // A photo or logo is content; a monogram only repeats the name that follows it,
                // so screen readers skip it (preflight A11Y-2).
                when {
                    image != null && isOrganization -> Modifier.semantics { contentDescription = "Logo of $displayName" }
                    image != null -> Modifier.semantics { contentDescription = "Photo of $displayName" }
                    else -> Modifier.clearAndSetSemantics { }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        } else {
            Text(
                text = initials.take(2).uppercase(),
                color = accent,
                style = TextStyle(fontSize = (size.value * 0.36f).sp, fontWeight = FontWeight.Medium, letterSpacing = 0.02.em),
            )
        }
    }
}

/**
 * Trust stated as part of the identity: one glyph and the state in words; demo data as a small
 * chip on the same row; one line of detail only when something is wrong. Colour is never the only
 * signal. Accessible as "Authenticity: <label>. <detail>".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuthenticityRow(
    state: AuthenticityState,
    isDemo: Boolean,
    colors: KipatColors,
    style: SurfaceStyle,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val p = state.present(isDemo)
    val tint = when (state) {
        AuthenticityState.AUTHENTIC -> colors.verified
        AuthenticityState.REVOKED -> colors.revoked
        AuthenticityState.ERROR -> colors.warning
        else -> colors.textSecondary
    }
    val showDetail = state == AuthenticityState.REVOKED || state == AuthenticityState.ERROR
    Column(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "Authenticity: ${p.label}. ${p.detail}" }) {
        Row(Modifier.heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            TrustGlyph(state, tint, accent)
            Spacer(Modifier.width(8.dp))
            // The demo chip sits at the end of the row and wraps below the label when the row is
            // too narrow (large font), instead of squeezing the state words (preflight FONT-2).
            FlowRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(p.label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = tint, modifier = Modifier.padding(end = 12.dp))
                if (p.demoNote != null) {
                    Text(
                        p.demoNote,
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                        color = colors.textTertiary,
                        maxLines = 1,
                        modifier = Modifier
                            .border(1.dp, style.rule, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
        if (showDetail) {
            Text(
                p.detail,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(start = 26.dp, top = 2.dp),
            )
        }
    }
}

@Composable
private fun TrustGlyph(state: AuthenticityState, tint: Color, accent: Color) {
    val icon: ImageVector? = when (state) {
        AuthenticityState.AUTHENTIC -> KipatSymbols.VerifiedFilled
        AuthenticityState.REVOKED -> KipatSymbols.Revoked
        AuthenticityState.ERROR -> KipatSymbols.Warning
        AuthenticityState.UNVERIFIED, AuthenticityState.UNKNOWN -> KipatSymbols.Info
        AuthenticityState.CHECKING -> null
    }
    if (icon != null) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    } else {
        // Checking: a quiet, static partial ring in the accent. Progress is stated in words; no spinner.
        Canvas(Modifier.size(18.dp)) {
            val w = 1.75.dp.toPx()
            drawCircle(tint.copy(alpha = 0.25f), radius = size.minDimension / 2f - w, style = Stroke(w))
            drawArc(accent, -90f, 250f, false, topLeft = androidx.compose.ui.geometry.Offset(w, w),
                size = androidx.compose.ui.geometry.Size(size.width - 2 * w, size.height - 2 * w), style = Stroke(w, cap = StrokeCap.Round))
        }
    }
}

/**
 * One primary action attached to the identity, then a light row of secondary actions. No stacked
 * outlined buttons: this is a person with actions, not a form.
 */
@Composable
private fun SurfaceActionsBlock(payload: RevealPayload, actions: SurfaceActions, palette: RevealPalette, surface: Color, modifier: Modifier = Modifier) {
    val colors = palette.card
    Column(modifier.fillMaxWidth()) {
        if (!payload.connectable) {
            // Unresolved card: nothing to connect to or save.
            PrimaryAction("Done", null, palette, surface, enabled = true, settled = false, onClick = { if (actions.ready()) actions.onDone() })
            return@Column
        }
        val isConnected = actions.connected || payload.source == RevealSource.REPLAY
        PrimaryAction(
            text = if (isConnected) "Connected" else payload.primaryCta,
            icon = if (isConnected) KipatSymbols.Check else null,
            palette = palette,
            surface = surface,
            enabled = !isConnected && payload.authenticity != AuthenticityState.REVOKED,
            settled = isConnected,
            onClick = { if (actions.ready()) actions.onConnect() },
        )
        // "Open profile" only when there is a profile in Kipat to open: a connection, or your own preview.
        val canOpen = isConnected || payload.source == RevealSource.PREVIEW
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryAction(payload.secondaryCta, KipatSymbols.ContactPage, colors, Modifier.weight(1f)) { if (actions.ready()) actions.onSaveContact() }
            if (canOpen) {
                SecondaryAction("Open profile", KipatSymbols.Person, colors, Modifier.weight(1f)) { if (actions.ready()) actions.onOpenProfile() }
            }
        }
    }
}

@Composable
private fun PrimaryAction(text: String, icon: ImageVector?, palette: RevealPalette, surface: Color, enabled: Boolean, settled: Boolean, onClick: () -> Unit) {
    // "Connected" is a settled state, not a broken button: a tint of the accent, not disabled grey.
    val settledContainer = palette.accent.copy(alpha = 0.14f).compositeOver(surface)
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = palette.accent,
            contentColor = palette.onAccent,
            disabledContainerColor = if (settled) settledContainer else palette.card.textPrimary.copy(alpha = 0.08f),
            disabledContentColor = if (settled) palette.accent else palette.card.textPrimary.copy(alpha = 0.38f),
        ),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun SecondaryAction(text: String, icon: ImageVector, colors: KipatColors, modifier: Modifier, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), shape = MaterialTheme.shapes.medium) {
        Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun Modifier.layerAlpha(rise: Dp = 0.dp, progress: () -> Float) = graphicsLayer {
    val p = progress()
    alpha = p
    translationY = (1f - p) * rise.toPx()
}
