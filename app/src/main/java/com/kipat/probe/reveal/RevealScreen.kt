package com.kipat.probe.reveal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kipat.probe.model.AuthenticityState
import com.kipat.probe.reveal.effects.EdgeTrace
import com.kipat.probe.reveal.effects.ParticleField
import com.kipat.probe.reveal.effects.RevealEffects.drawReveal
import com.kipat.probe.reveal.effects.RevealGeometry
import com.kipat.probe.ui.components.IdentityBlock
import com.kipat.probe.ui.components.IdentitySlots
import com.kipat.probe.ui.components.KipatPrimaryButton
import com.kipat.probe.ui.components.KipatSecondaryButton
import com.kipat.probe.ui.components.KipatSymbols
import com.kipat.probe.ui.components.RecipientCard
import com.kipat.probe.ui.theme.KipatRadius
import com.kipat.probe.ui.theme.KipatSpacing
import com.kipat.probe.ui.theme.RevealPalette

enum class RevealMilestone { FIRST_VISUAL, VERIFIED, COMPLETE }

/**
 * The single Reveal renderer (NFC, deep link, Preview, Replay). Quiet Graphite v2: graphite scrim
 * over the real previous app, one contact ring, a cobalt line tracing the card from the contact
 * point, then an opaque card with identity staged in reading order.
 *
 * Per-frame values are read only inside draw/graphicsLayer lambdas (no per-frame recomposition).
 */
@Composable
fun RevealScreen(
    payload: RevealPayload,
    quality: RevealQuality,
    runKey: Int,
    connected: Boolean,
    onConnect: () -> Unit,
    onSaveContact: () -> Unit,
    onOpenProfile: () -> Unit,
    onDismiss: () -> Unit,
    onMilestone: (RevealMilestone) -> Unit,
) {
    val config = remember(payload.template, quality) { RevealConfig.forTemplate(payload.template, quality) }
    val timeline = config.timeline
    val dark = isSystemInDarkTheme()
    val palette = remember(payload.theme, dark) { RevealPalette.of(payload.theme, dark) }
    val particles = remember(config.particleCount) { ParticleField(config.particleCount.coerceAtLeast(1)) }
    val milestone by rememberUpdatedState(onMilestone)

    // Elapsed time survives configuration changes, so rotation resumes instead of replaying.
    var elapsedMs by rememberSaveable(runKey) { mutableLongStateOf(0L) }
    val frame by remember(timeline) { derivedStateOf { RevealEngine.frameAt(elapsedMs, timeline) } }
    val ctaReady by remember { derivedStateOf { frame.cta > 0.5f } }

    LaunchedEffect(runKey, timeline) {
        val total = timeline.totalDuration.toLong()
        val resumeFrom = elapsedMs
        // Restored after rotation with the Reveal already finished: don't replay haptics/markers.
        if (resumeFrom >= total) return@LaunchedEffect
        var firstFrame = true
        var verifiedSent = resumeFrom >= timeline.verifyStart + timeline.stepDuration
        val start = withFrameMillis { it } - resumeFrom
        while (elapsedMs < total) {
            elapsedMs = (withFrameMillis { it } - start).coerceAtMost(total)
            if (firstFrame) {
                firstFrame = false
                milestone(RevealMilestone.FIRST_VISUAL)
            }
            if (!verifiedSent && elapsedMs >= timeline.verifyStart + timeline.stepDuration) {
                verifiedSent = true
                milestone(RevealMilestone.VERIFIED)
            }
        }
        if (firstFrame) milestone(RevealMilestone.FIRST_VISUAL)
        if (!verifiedSent) milestone(RevealMilestone.VERIFIED)
        milestone(RevealMilestone.COMPLETE)
    }

    var cardRect by remember { mutableStateOf<Rect?>(null) }
    val density = LocalDensity.current
    val trace = remember(cardRect, config.perimeterTrace) {
        val r = cardRect
        if (config.perimeterTrace > 0f && r != null) EdgeTrace(r, with(density) { KipatRadius.large.toPx() }) else null
    }

    Box(
        Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = "Dismiss",
                onClick = onDismiss,
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val f = RevealEngine.frameAt(elapsedMs, timeline)
            // Scrim: quick to 55%, then deepens to 82% as the card resolves; the real app stays faintly visible.
            val scrim = (elapsedMs / 150f).coerceIn(0f, 1f) * (0.55f + 0.27f * f.surface)
            drawRect(palette.scrim, alpha = scrim)
            val rect = cardRect ?: return@Canvas
            val geo = RevealGeometry(
                tapOrigin = Offset(rect.center.x, rect.top),
                center = rect.center,
                cardRect = rect,
                maxRadius = size.maxDimension * 0.6f,
            )
            drawReveal(f, config, geo, particles, palette.accent, palette.card.textSecondary, trace)
        }

        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = KipatSpacing.s24),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (payload.source == RevealSource.PREVIEW) {
                Text(
                    "Preview of your Reveal",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.onScrim,
                    modifier = Modifier.padding(top = KipatSpacing.s16),
                )
            }
            Spacer(Modifier.weight(0.8f))

            Box(
                Modifier
                    .widthIn(max = 420.dp)
                    .onGloballyPositioned { cardRect = it.boundsInRoot() }
                    // Swallow taps on the card so only the backdrop dismisses.
                    .pointerInput(Unit) { detectTapGestures { } }
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .graphicsLayer {
                        val s = frame.surface
                        alpha = s
                        when (config.surfaceEntrance) {
                            SurfaceEntrance.RISE -> {
                                val scale = 0.98f + 0.02f * s
                                scaleX = scale; scaleY = scale
                                translationY = (1f - s) * 8.dp.toPx()
                            }
                            SurfaceEntrance.LIQUID -> {
                                val scale = 0.8f + 0.2f * RevealEngine.easeOutBack(s, 1.2f)
                                scaleX = scale; scaleY = scale
                            }
                            SurfaceEntrance.FADE, SurfaceEntrance.TRACE -> Unit
                        }
                    }
                    .shadow(if (dark) 0.dp else 12.dp, MaterialTheme.shapes.large, clip = false),
            ) {
                RecipientCard(colors = palette.card) {
                    Column {
                        IdentityBlock(
                            displayName = payload.displayName,
                            subtitle = payload.subtitle,
                            organization = payload.organization,
                            initials = payload.initials,
                            authenticity = payload.authenticity,
                            isDemo = payload.isDemo,
                            colors = palette.card,
                            avatarSize = 56.dp,
                            slots = { frame.let { IdentitySlots(it.avatar, it.name, it.role, it.verify) } },
                            trailing = {
                                IconButton(onClick = onDismiss) {
                                    Icon(KipatSymbols.Close, contentDescription = "Close", tint = palette.card.textSecondary)
                                }
                            },
                        )
                        Spacer(Modifier.height(KipatSpacing.s24))
                        RevealActions(
                            payload = payload,
                            palette = palette,
                            connected = connected,
                            ready = ctaReady,
                            onConnect = onConnect,
                            onSaveContact = onSaveContact,
                            onOpenProfile = onOpenProfile,
                            onDone = onDismiss,
                            modifier = Modifier.graphicsLayer {
                                alpha = frame.cta
                                translationY = (1f - frame.cta) * 6.dp.toPx()
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun RevealActions(
    payload: RevealPayload,
    palette: RevealPalette,
    connected: Boolean,
    ready: Boolean,
    onConnect: () -> Unit,
    onSaveContact: () -> Unit,
    onOpenProfile: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(KipatSpacing.s12)) {
        if (!payload.connectable) {
            // Unresolved card: nothing to connect to or save.
            KipatPrimaryButton("Done", onClick = onDone, enabled = ready, container = palette.accent, content = palette.onAccent, modifier = Modifier.fillMaxWidth())
            return@Column
        }
        val isConnected = connected || payload.source == RevealSource.REPLAY
        KipatPrimaryButton(
            text = if (isConnected) "Connected" else payload.primaryCta,
            icon = if (isConnected) KipatSymbols.Check else KipatSymbols.PersonAdd,
            onClick = onConnect,
            enabled = ready && !isConnected && payload.authenticity != AuthenticityState.REVOKED,
            container = palette.accent,
            content = palette.onAccent,
            modifier = Modifier.fillMaxWidth(),
        )
        // "Open profile" only when there is a profile in Kipat to open: a connection, or your own preview.
        val canOpen = isConnected || payload.source == RevealSource.PREVIEW
        Row(horizontalArrangement = Arrangement.spacedBy(KipatSpacing.s12)) {
            KipatSecondaryButton(
                payload.secondaryCta,
                onClick = onSaveContact,
                enabled = ready,
                contentColor = palette.card.textPrimary,
                borderColor = palette.card.outline,
                modifier = Modifier.weight(1f),
            )
            if (canOpen) {
                KipatSecondaryButton(
                    "Open profile",
                    onClick = onOpenProfile,
                    enabled = ready,
                    contentColor = palette.card.textPrimary,
                    borderColor = palette.card.outline,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
