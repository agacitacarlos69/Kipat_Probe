package com.kipat.probe.reveal

import androidx.compose.foundation.Canvas
import android.graphics.BitmapFactory
import androidx.core.net.toUri
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kipat.probe.reveal.effects.RevealEffects
import com.kipat.probe.reveal.effects.RevealEffects.drawOver
import com.kipat.probe.reveal.effects.RevealEffects.drawUnder
import com.kipat.probe.reveal.effects.RevealGeometry
import com.kipat.probe.reveal.effects.RevealInk
import com.kipat.probe.reveal.effects.RevealPaths
import com.kipat.probe.ui.components.IdentitySlots
import com.kipat.probe.ui.theme.KipatRadius
import com.kipat.probe.ui.theme.KipatSpacing
import com.kipat.probe.ui.theme.RevealPalette

enum class RevealMilestone { FIRST_VISUAL, IDENTITY_READABLE, VERIFIED, COMPLETE }

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
 * The Reveal at a given moment: backdrop, the template's primitive (under or over the surface),
 * the [KipatIdentitySurface] emerging through the template's surface entry, identity staged in
 * reading order, and actions. Stateless about time, so previews, the style picker and screenshot
 * tests can render any frame.
 *
 * [contained] renders without system-bar insets, the preview label or tap-to-dismiss, for the
 * style picker's live preview.
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
    contained: Boolean = false,
) {
    val timeline = choreography.timeline
    val dark = isSystemInDarkTheme()
    val presentation = remember(payload.template, payload.theme) { RevealPresentation.of(payload.template, payload.theme) }
    val palette = remember(payload.theme, dark) { RevealPalette.of(payload.theme, dark) }
    val style = remember(palette, presentation) { palette.surface(presentation.treatment) }
    val ink = remember(palette, style) { RevealInk(palette.ink, palette.accent, style.container) }
    val paths = remember { RevealPaths() }
    val maskPath = remember { Path() }
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val frame by remember(timeline) { derivedStateOf { RevealEngine.frameAt(elapsedMs(), timeline) } }
    // Keyed on the timeline: a new tap with a different template must not keep the old clock.
    val ctaReady by remember(timeline) { derivedStateOf { frame.cta > 0.5f } }
    val avatar = rememberAvatarBitmap(payload.avatarUri)

    var cardRect by remember { mutableStateOf<Rect?>(null) }
    // Card bounds are taken relative to the stage, so the stage also works scaled inside the picker.
    // [0] stage, [1] card. Either callback may run first, so both recompute.
    val coords = remember { arrayOfNulls<LayoutCoordinates>(2) }
    fun measureCard() {
        val s = coords[0] ?: return
        val c = coords[1] ?: return
        if (s.isAttached && c.isAttached) cardRect = s.localBoundingBoxOf(c, clipBounds = false)
    }
    val geometry = remember(cardRect, density, rtl, presentation.layout) {
        cardRect?.let { revealGeometry(it, presentation.layout, density, rtl) }
    }
    val actions = SurfaceActions(
        connected = connected,
        ready = { ctaReady },
        onConnect = onConnect,
        onSaveContact = onSaveContact,
        onOpenProfile = onOpenProfile,
        onDone = onDismiss,
    )

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { coords[0] = it; measureCard() }
            .then(
                if (contained) {
                    Modifier
                } else {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = "Dismiss",
                        onClick = onDismiss,
                    )
                },
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val ms = elapsedMs()
            // Backdrop: quick to its base, then deepens as the surface resolves; the app underneath stays faintly visible.
            val scrim = (ms / 150f).coerceIn(0f, 1f) * (palette.scrimBase + (palette.scrimPeak - palette.scrimBase) * frame.surface)
            // Contained (picker): the host panel is the backdrop.
            if (!contained) drawRect(palette.scrim, alpha = scrim)
            geometry?.let { drawUnder(choreography, frame, it, ink) }
        }

        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .then(if (contained) Modifier else Modifier.safeDrawingPadding())
                .padding(horizontal = SurfaceMetrics.MARGIN_DP.dp, vertical = KipatSpacing.s16),
        ) {
            if (payload.source == RevealSource.PREVIEW && !contained) {
                Text(
                    "Preview of your Reveal",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.onScrim,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
            val cardShape = RoundedCornerShape(style.radiusDp.dp)
            Box(
                Modifier
                    // Slightly above centre; never taller than the window (scrolls only in that case).
                    .align(BiasAlignment(0f, -0.2f))
                    .widthIn(max = SurfaceMetrics.MAX_WIDTH_DP.dp)
                    .heightIn(max = maxHeight)
                    // Measured outside every transform, so primitives anchor to the settled surface.
                    .onGloballyPositioned { coords[1] = it; measureCard() }
                    // Swallow taps on the surface so only the backdrop dismisses.
                    .pointerInput(Unit) { detectTapGestures { } }
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .drawWithContent {
                        // Orbit, Aperture, Contact Ripple: the surface is uncovered inside a growing circle.
                        val g = geometry
                        val r = if (g == null) {
                            null
                        } else {
                            val local = g.translatedToSurface()
                            val origin = RevealEffects.maskOrigin(choreography, local)
                            RevealEffects.maskRadius(choreography, elapsedMs(), RevealEffects.maskMaxRadius(local.card, origin, local.u), RevealEffects.maskStartRadius(choreography, local))
                                ?.let { origin to it }
                        }
                        if (r == null) {
                            drawContent()
                        } else {
                            maskPath.reset()
                            maskPath.addOval(Rect(r.first, r.second))
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
                        // Light mode only; dark surfaces separate by tone and edge. Spatial Card's shadow forms as it lands.
                        if (!dark && style.shadowDp > 0f) {
                            val landed = if (choreography.surfaceEntry == SurfaceEntry.TILT && choreography.animatedShadow) {
                                1f - tilt / RevealEffects.MAX_TILT_DEGREES
                            } else {
                                1f
                            }
                            shadowElevation = style.shadowDp.dp.toPx() * landed
                            shape = cardShape
                            clip = false
                        }
                    }
                    .drawBehind {
                        // Spatial Card: the surface's edge, visible only while tilted; same layer, same perspective.
                        val tilt = RevealEffects.tiltDegrees(choreography, elapsedMs())
                        if (tilt > 0.05f) {
                            val thickness = 3.dp.toPx() * tilt / RevealEffects.MAX_TILT_DEGREES
                            // Light: a grey edge below the face. Dark: a faint lit edge (a dark one would vanish).
                            drawRoundRect(
                                if (dark) palette.ink.copy(alpha = 0.22f) else palette.card.outline.copy(alpha = 0.45f),
                                topLeft = Offset(0f, thickness),
                                size = size,
                                cornerRadius = CornerRadius(style.radiusDp.dp.toPx()),
                            )
                        }
                    },
            ) {
                KipatIdentitySurface(
                    payload = payload,
                    presentation = presentation,
                    palette = palette,
                    actions = actions,
                    onDismiss = onDismiss,
                    avatar = avatar,
                    wipeName = choreography.nameEntry == NameEntry.WIPE,
                    slots = { frame.let { IdentitySlots(it.avatar, it.name, it.role, it.verify) } },
                    actionsProgress = { frame.cta },
                    // Unusually long content scrolls inside the surface instead of pushing actions off screen.
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
        }

        // Primitives that sit on the avatar (orbit ring, aperture blades) draw above the surface.
        Canvas(Modifier.fillMaxSize()) {
            geometry?.let { drawOver(choreography, frame, it, ink, paths) }
        }
    }
}

/** Primitives anchor to the avatar of the [layout] (top-start of the surface, mirrored in RTL). */
private fun revealGeometry(card: Rect, layout: IdentityLayout, density: Density, rtl: Boolean): RevealGeometry {
    val u = density.density
    val inset = SurfaceMetrics.avatarCenterDp(layout, card.width / u) * u
    val x = if (rtl) card.right - inset else card.left + inset
    return RevealGeometry(
        card = card,
        avatarCenter = Offset(x, card.top + inset),
        avatarRadius = layout.avatarDp / 2f * u,
        cornerRadius = KipatRadius.large.value * u,
        u = u,
    )
}

/** The same geometry in the surface's own coordinates (for its mask). */
private fun RevealGeometry.translatedToSurface(): RevealGeometry {
    val o = card.topLeft
    return RevealGeometry(Rect(Offset.Zero, card.size), avatarCenter - o, avatarRadius, cornerRadius, u, hair)
}

/**
 * Loads a local avatar/logo (`content://`) off the main thread, downsampled. Remote images aren't
 * loaded (the app has no network permission); the monogram stays until a bitmap arrives.
 */
@Composable
private fun rememberAvatarBitmap(uri: String?): ImageBitmap? {
    if (uri == null || !uri.startsWith("content://")) return null
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val parsed = uri.toUri()
                val target = 256
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target) sample *= 2
                resolver.openInputStream(parsed)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                }?.asImageBitmap()
            } catch (_: Exception) {
                null // A missing or unreadable image keeps the monogram.
            }
        }
    }
    return bitmap
}
