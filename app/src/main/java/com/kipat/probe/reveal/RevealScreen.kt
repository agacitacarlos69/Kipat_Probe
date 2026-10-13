package com.kipat.probe.reveal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kipat.probe.model.AuthenticityState
import com.kipat.probe.model.present
import com.kipat.probe.reveal.effects.ParticleField
import com.kipat.probe.reveal.effects.RevealEffects.drawReveal
import com.kipat.probe.reveal.effects.RevealGeometry
import com.kipat.probe.ui.components.IdentityCardSlots
import com.kipat.probe.ui.components.IdentityCardStyle
import com.kipat.probe.ui.components.KipatIdentityCard
import com.kipat.probe.ui.components.KipatPrimaryButton
import com.kipat.probe.ui.components.KipatSecondaryButton
import com.kipat.probe.ui.theme.KipatSpacing
import com.kipat.probe.ui.theme.RevealPalette

enum class RevealMilestone { FIRST_VISUAL, VERIFIED, COMPLETE }

/**
 * The single Reveal renderer used by NFC taps, the debug deep link, Preview and Replay.
 *
 * Per-frame values are read only inside draw/graphicsLayer lambdas, so the animation runs
 * without recomposing the tree every frame.
 */
@Composable
fun RevealScreen(
    payload: RevealPayload,
    quality: RevealQuality,
    runKey: Int,
    connected: Boolean,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
    onDismiss: () -> Unit,
    onMilestone: (RevealMilestone) -> Unit,
) {
    val config = remember(payload.template, quality) { RevealConfig.forTemplate(payload.template, quality) }
    val timeline = config.timeline
    val palette = remember(payload.theme) { RevealPalette.of(payload.theme) }
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
    val trace = remember(cardRect, config.surfaceEntrance) {
        val r = cardRect
        if (config.surfaceEntrance == SurfaceEntrance.TRACE && r != null) {
            val path = Path().apply { addRoundRect(RoundRect(r, CornerRadius(with(density) { 28.dp.toPx() }))) }
            Triple(path, PathMeasure().apply { setPath(path, false) }, Path())
        } else {
            null
        }
    }

    val source = when (payload.source) {
        RevealSource.NFC_TAG -> "Kipat · NFC tap"
        RevealSource.DEEP_LINK -> "Kipat · debug link"
        RevealSource.PREVIEW -> "Preview"
        RevealSource.REPLAY -> "Replay"
    }
    val auth = payload.authenticity.present(payload.isDemo)

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
        // Scrim + effects: the previous app stays faintly visible underneath.
        Canvas(Modifier.fillMaxSize()) {
            val scrim = (elapsedMs / 180f).coerceIn(0f, 1f)
            drawRect(Brush.verticalGradient(listOf(palette.scrimTop, palette.scrimBottom)), alpha = scrim)
            val rect = cardRect ?: return@Canvas
            val geo = RevealGeometry(
                tapOrigin = Offset(size.width * 0.5f, size.height * 0.16f),
                center = rect.center,
                cardRect = rect,
                maxRadius = size.maxDimension * 0.6f,
            )
            drawReveal(
                frame = RevealEngine.frameAt(elapsedMs, timeline),
                config = config,
                geo = geo,
                particles = particles,
                primary = palette.primary,
                secondary = palette.secondary,
                tracePath = trace?.first,
                traceMeasure = trace?.second,
                traceSegment = trace?.third,
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = KipatSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = KipatSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (payload.isDemo) "$source · demo data" else source,
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.textMuted,
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer { alpha = (elapsedMs / 300f).coerceIn(0f, 1f) },
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(palette.cardSurface),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Dismiss Reveal", tint = palette.text)
                }
            }

            Spacer(Modifier.weight(0.8f))

            KipatIdentityCard(
                displayName = payload.displayName,
                subtitle = payload.subtitle,
                organization = payload.organization,
                initials = payload.initials,
                authenticity = payload.authenticity,
                isDemo = payload.isDemo,
                eyebrow = "Kipat",
                style = IdentityCardStyle.reveal(palette),
                centered = true,
                slots = { frame.let { IdentityCardSlots(it.avatar, it.name, it.role, it.verify) } },
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .onGloballyPositioned { cardRect = it.boundsInRoot() }
                    // Swallow taps on the card so only the backdrop dismisses.
                    .pointerInput(Unit) { detectTapGestures { } }
                    .semantics(mergeDescendants = false) {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = listOfNotNull(payload.displayName, payload.subtitle, payload.organization, auth.label).joinToString(", ")
                    }
                    .graphicsLayer {
                        val s = frame.surface
                        alpha = s
                        when (config.surfaceEntrance) {
                            SurfaceEntrance.RISE -> {
                                val scale = 0.94f + 0.06f * s
                                scaleX = scale; scaleY = scale
                                translationY = (1f - s) * 28.dp.toPx()
                            }
                            SurfaceEntrance.LIQUID -> {
                                val scale = 0.7f + 0.3f * RevealEngine.easeOutBack(s, 1.4f)
                                scaleX = scale; scaleY = scale
                            }
                            SurfaceEntrance.FADE, SurfaceEntrance.TRACE -> {
                                translationY = (1f - s) * 8.dp.toPx()
                            }
                        }
                    },
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = frame.cta
                            translationY = (1f - frame.cta) * 12.dp.toPx()
                        },
                    verticalArrangement = Arrangement.spacedBy(KipatSpacing.sm),
                ) {
                    val primaryLabel = if (connected && payload.canConnect) "Connected" else payload.primaryCta
                    KipatPrimaryButton(
                        text = primaryLabel,
                        icon = if (connected && payload.canConnect) Icons.Filled.Check else null,
                        onClick = onPrimary,
                        enabled = ctaReady && !(connected && payload.canConnect) && payload.authenticity != AuthenticityState.REVOKED,
                        container = palette.primary,
                        content = palette.scrimBottom.copy(alpha = 1f),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    KipatSecondaryButton(
                        text = payload.secondaryCta,
                        onClick = onSecondary,
                        enabled = ctaReady,
                        contentColor = palette.text,
                        borderColor = palette.cardBorder,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.weight(1f))
            Text(
                text = "Tap outside to dismiss",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textMuted,
                modifier = Modifier
                    .padding(bottom = KipatSpacing.lg)
                    .graphicsLayer { alpha = frame.cta * 0.8f },
            )
        }
    }
}
