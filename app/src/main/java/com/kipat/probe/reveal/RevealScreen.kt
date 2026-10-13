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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kipat.probe.model.AuthenticityState
import com.kipat.probe.reveal.effects.RevealEffects
import com.kipat.probe.reveal.effects.RevealEffects.drawOver
import com.kipat.probe.reveal.effects.RevealEffects.drawUnder
import com.kipat.probe.reveal.effects.RevealGeometry
import com.kipat.probe.reveal.effects.RevealInk
import com.kipat.probe.reveal.effects.RevealPaths
import com.kipat.probe.ui.components.IdentityBlock
import com.kipat.probe.ui.components.IdentitySlots
import com.kipat.probe.ui.components.KipatPrimaryButton
import com.kipat.probe.ui.components.KipatSecondaryButton
import com.kipat.probe.ui.components.KipatSymbols
import com.kipat.probe.ui.components.RecipientCard
import com.kipat.probe.ui.theme.KipatRadius
import com.kipat.probe.ui.theme.KipatSpacing
import com.kipat.probe.ui.theme.RevealPalette

enum class RevealMilestone { FIRST_VISUAL, IDENTITY_READABLE, VERIFIED, COMPLETE }

/** Card layout constants the primitives anchor to (RecipientCard padding + Reveal avatar). */
private val CardPadding = KipatSpacing.s24
private val RevealAvatar = 56.dp

/**
 * The single Reveal renderer (NFC, deep link, Preview, Replay). Owns the clock and milestones;
 * drawing is [RevealStage]. Elapsed time survives rotation, so the Reveal resumes, not replays.
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
    val choreography = remember(payload.template, quality) { RevealChoreography.forTemplate(payload.template, quality) }
    val timeline = choreography.timeline
    val milestone by rememberUpdatedState(onMilestone)
    var elapsedMs by rememberSaveable(runKey) { mutableLongStateOf(0L) }

    LaunchedEffect(runKey, timeline) {
        val total = timeline.totalDuration.toLong()
        val resumeFrom = elapsedMs
        // Restored after rotation with the Reveal already finished: don't replay haptics/markers.
        if (resumeFrom >= total) return@LaunchedEffect
        var firstFrame = true
        var readableSent = resumeFrom >= timeline.identityReadableAt
        var verifiedSent = resumeFrom >= timeline.verifiedAt
        val start = withFrameMillis { it } - resumeFrom
        while (elapsedMs < total) {
            elapsedMs = (withFrameMillis { it } - start).coerceAtMost(total)
            if (firstFrame) {
                firstFrame = false
                milestone(RevealMilestone.FIRST_VISUAL)
            }
            if (!readableSent && elapsedMs >= timeline.identityReadableAt) {
                readableSent = true
                milestone(RevealMilestone.IDENTITY_READABLE)
            }
            if (!verifiedSent && elapsedMs >= timeline.verifiedAt) {
                verifiedSent = true
                milestone(RevealMilestone.VERIFIED)
            }
        }
        if (firstFrame) milestone(RevealMilestone.FIRST_VISUAL)
        if (!readableSent) milestone(RevealMilestone.IDENTITY_READABLE)
        if (!verifiedSent) milestone(RevealMilestone.VERIFIED)
        milestone(RevealMilestone.COMPLETE)
    }

    RevealStage(
        payload = payload,
        choreography = choreography,
        elapsedMs = { elapsedMs },
        connected = connected,
        onConnect = onConnect,
        onSaveContact = onSaveContact,
        onOpenProfile = onOpenProfile,
        onDismiss = onDismiss,
    )
}

/**
 * The Reveal at a given moment: scrim, the template's primitive (under or over the card), the
 * card with its surface entry, identity staged in reading order, and actions. Stateless about
 * time, so previews and screenshot tests can render any frame.
 *
 * Per-frame values are read only in draw/graphicsLayer lambdas (no per-frame recomposition).
 */
@Composable
fun RevealStage(
    payload: RevealPayload,
    choreography: RevealChoreography,
    elapsedMs: () -> Long,
    connected: Boolean,
    onConnect: () -> Unit,
    onSaveContact: () -> Unit,
    onOpenProfile: () -> Unit,
    onDismiss: () -> Unit,
) {
    val timeline = choreography.timeline
    val dark = isSystemInDarkTheme()
    val palette = remember(payload.theme, dark) { RevealPalette.of(payload.theme, dark) }
    val ink = remember(palette) { RevealInk(palette.ink, palette.accent, palette.card.surfaceElevated) }
    val paths = remember { RevealPaths() }
    val maskPath = remember { Path() }
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val frame by remember(timeline) { derivedStateOf { RevealEngine.frameAt(elapsedMs(), timeline) } }
    // Keyed on the timeline: a new tap with a different template must not keep the old clock.
    val ctaReady by remember(timeline) { derivedStateOf { frame.cta > 0.5f } }

    var cardRect by remember { mutableStateOf<Rect?>(null) }
    val geometry = remember(cardRect, density, rtl) { cardRect?.let { revealGeometry(it, density, rtl) } }

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
            val ms = elapsedMs()
            // Scrim: quick to its base, then deepens as the card resolves; the app underneath stays faintly visible.
            val scrim = (ms / 150f).coerceIn(0f, 1f) * (palette.scrimBase + (palette.scrimPeak - palette.scrimBase) * frame.surface)
            drawRect(palette.scrim, alpha = scrim)
            geometry?.let { drawUnder(choreography, frame, it, ink) }
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

            val cardShape = MaterialTheme.shapes.large
            Box(
                Modifier
                    .widthIn(max = 420.dp)
                    // Measured outside every transform, so primitives anchor to the settled card.
                    .onGloballyPositioned { cardRect = it.boundsInRoot() }
                    // Swallow taps on the card so only the backdrop dismisses.
                    .pointerInput(Unit) { detectTapGestures { } }
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .drawWithContent {
                        // Contact Ripple: the card is uncovered inside the ripple's leading edge.
                        val maxRadius = RevealEffects.rippleMaxRadius(size.width, size.height, density.density)
                        val r = RevealEffects.revealRadius(choreography, elapsedMs(), maxRadius)
                        if (r == null) {
                            drawContent()
                        } else {
                            maskPath.reset()
                            maskPath.addOval(Rect(Offset(size.width / 2f, 0f), r))
                            clipPath(maskPath) { this@drawWithContent.drawContent() }
                        }
                    }
                    .graphicsLayer {
                        val s = frame.surface
                        alpha = s
                        val tilt = RevealEffects.tiltDegrees(choreography, elapsedMs())
                        when (choreography.surfaceEntry) {
                            SurfaceEntry.FADE_RISE -> translationY = (1f - s) * 10.dp.toPx()
                            SurfaceEntry.FADE_SCALE -> {
                                val from = if (choreography.reduced) 0.98f else 0.99f
                                val scale = from + (1f - from) * s
                                scaleX = scale; scaleY = scale
                            }
                            SurfaceEntry.TILT -> {
                                // Pivot on the near (bottom) edge; top leans away 6° and settles flat.
                                transformOrigin = TransformOrigin(0.5f, 1f)
                                cameraDistance = 12f * density.density
                                rotationX = tilt
                                translationY = tilt / RevealEffects.MAX_TILT_DEGREES * 16.dp.toPx()
                            }
                            SurfaceEntry.CIRCULAR_REVEAL, SurfaceEntry.FADE -> Unit
                        }
                        // Light mode only. Spatial Card's shadow forms as it lands; elsewhere it's constant.
                        if (!dark) {
                            val landed = if (choreography.surfaceEntry == SurfaceEntry.TILT && choreography.animatedShadow) {
                                1f - tilt / RevealEffects.MAX_TILT_DEGREES
                            } else {
                                1f
                            }
                            shadowElevation = 12.dp.toPx() * landed
                            shape = cardShape
                            clip = false
                        }
                    }
                    .drawBehind {
                        // Spatial Card: the card's edge, visible only while tilted; same layer, same perspective.
                        val tilt = RevealEffects.tiltDegrees(choreography, elapsedMs())
                        if (tilt > 0.05f) {
                            val thickness = 3.dp.toPx() * tilt / RevealEffects.MAX_TILT_DEGREES
                            // Light: a grey edge below the face. Dark: a faint lit edge (a dark one would vanish).
                            drawRoundRect(
                                if (dark) palette.ink.copy(alpha = 0.22f) else palette.card.outline.copy(alpha = 0.45f),
                                topLeft = Offset(0f, thickness),
                                size = size,
                                cornerRadius = CornerRadius(KipatRadius.large.toPx()),
                            )
                        }
                    },
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
                            avatarSize = RevealAvatar,
                            wipeName = choreography.nameEntry == NameEntry.WIPE,
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

        // Primitives that sit on the avatar (orbit ring, aperture blades) draw above the card.
        Canvas(Modifier.fillMaxSize()) {
            geometry?.let { drawOver(choreography, frame, it, ink, paths) }
        }
    }
}

private fun revealGeometry(card: Rect, density: Density, rtl: Boolean): RevealGeometry {
    val u = density.density
    val inset = (CardPadding.value + RevealAvatar.value / 2f) * u
    val x = if (rtl) card.right - inset else card.left + inset
    return RevealGeometry(
        card = card,
        avatarCenter = Offset(x, card.top + inset),
        avatarRadius = RevealAvatar.value / 2f * u,
        cornerRadius = KipatRadius.large.value * u,
        u = u,
    )
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
