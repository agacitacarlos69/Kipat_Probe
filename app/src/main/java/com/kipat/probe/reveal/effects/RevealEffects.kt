package com.kipat.probe.reveal.effects

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.kipat.probe.reveal.ParticleMotion
import com.kipat.probe.reveal.RevealConfig
import com.kipat.probe.reveal.RevealEngine
import com.kipat.probe.reveal.RevealFrame
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Precomputed per-particle constants. Allocated once per Reveal so the draw loop does no
 * per-frame allocation for particles.
 */
class ParticleField(count: Int, seed: Int = 7) {
    val angle = FloatArray(count)
    val radius = FloatArray(count)
    val size = FloatArray(count)
    val delay = FloatArray(count)
    val spin = FloatArray(count)
    val count: Int = count

    init {
        val r = Random(seed)
        for (i in 0 until count) {
            angle[i] = (i.toFloat() / count) * 2f * PI.toFloat() + r.nextFloat() * 0.6f
            radius[i] = 0.55f + r.nextFloat() * 0.45f
            size[i] = 1.2f + r.nextFloat() * 2.2f
            delay[i] = r.nextFloat() * 0.35f
            spin[i] = if (r.nextBoolean()) 1f else -1f
        }
    }
}

/** Geometry the effects need, computed once per layout. */
class RevealGeometry(val tapOrigin: Offset, val center: Offset, val cardRect: Rect, val maxRadius: Float)

/**
 * Shared Reveal primitives. Every template is a parameterisation of these (see [RevealConfig]);
 * there are no per-template animation engines.
 */
object RevealEffects {

    fun DrawScope.drawReveal(
        frame: RevealFrame,
        config: RevealConfig,
        geo: RevealGeometry,
        particles: ParticleField,
        primary: Color,
        secondary: Color,
        trace: EdgeTrace?,
    ) {
        if (config.glowStrength > 0f) drawGlow(frame, config, geo, primary, secondary)
        if (config.rippleRings > 0) drawRipples(frame, config.rippleRings, config.rippleScale, geo, primary)
        if (config.drip) drawDrip(frame, geo, primary)
        when (config.particleMotion) {
            ParticleMotion.NONE -> Unit
            ParticleMotion.CONVERGE -> drawConverge(frame, geo, particles, config.particleCount, primary, secondary)
            ParticleMotion.BLOOM -> drawBloom(frame, geo, particles, config.particleCount, primary, secondary)
            ParticleMotion.ORBIT -> drawOrbit(frame, geo, particles, config.particleCount, primary, secondary)
            ParticleMotion.GRID -> drawGrid(frame, geo, config.particleCount, primary)
            ParticleMotion.CONSTELLATION -> drawConstellation(frame, geo, particles, config.particleCount, primary, secondary)
        }
        if (trace != null && config.perimeterTrace > 0f) {
            val p = frame.particles.coerceAtLeast(frame.surface)
            // The line fades as the opaque card takes over the edge.
            val alpha = config.perimeterTrace * (1f - 0.85f * frame.surface)
            trace.draw(this, p, primary.copy(alpha = alpha.coerceIn(0f, 1f)), 1.5f * density)
        }
    }

    /** Soft radial bloom at the tap origin that migrates to the card centre. */
    private fun DrawScope.drawGlow(frame: RevealFrame, config: RevealConfig, geo: RevealGeometry, primary: Color, secondary: Color) {
        val a = frame.glow * config.glowStrength
        if (a <= 0.01f) return
        val c = lerp(geo.tapOrigin, geo.center, frame.particles)
        val r = geo.maxRadius * (0.35f + 0.25f * frame.surface)
        drawCircle(
            brush = Brush.radialGradient(
                0f to primary.copy(alpha = 0.32f * a),
                0.45f to secondary.copy(alpha = 0.12f * a),
                1f to Color.Transparent,
                center = c,
                radius = r,
            ),
            radius = r,
            center = c,
            blendMode = BlendMode.Plus,
        )
    }

    /** Concentric rings expanding from the tap origin. */
    /** One ring = the contact acknowledgement (small scale); several = the Ripple template. */
    private fun DrawScope.drawRipples(frame: RevealFrame, rings: Int, scale: Float, geo: RevealGeometry, color: Color) {
        val p = frame.ripple
        if (p <= 0f || p >= 1f) return
        for (i in 0 until rings) {
            val local = ((p - i * 0.18f) / (1f - i * 0.18f)).coerceIn(0f, 1f)
            if (local <= 0f) continue
            val radius = geo.maxRadius * scale * RevealEngine.easeOutCubic(local)
            val alpha = (1f - local) * (0.7f - i * 0.18f)
            drawCircle(color.copy(alpha = alpha.coerceAtLeast(0f)), radius, geo.tapOrigin, style = Stroke(width = (1.5f - i * 0.25f).coerceAtLeast(1f) * density))
        }
    }

    /**
     * Signature "drip": a bead of light falls from the tap point to the card centre, stretching
     * as it accelerates and landing just as the surface resolves.
     */
    private fun DrawScope.drawDrip(frame: RevealFrame, geo: RevealGeometry, color: Color) {
        val p = frame.particles
        if (p <= 0f || frame.surface >= 1f) return
        val head = lerp(geo.tapOrigin, geo.center, p)
        val tailT = (p - 0.22f).coerceAtLeast(0f)
        val tail = lerp(geo.tapOrigin, geo.center, tailT)
        val fade = 1f - frame.surface
        drawLine(
            brush = Brush.linearGradient(listOf(Color.Transparent, color.copy(alpha = 0.75f * fade)), start = tail, end = head),
            start = tail,
            end = head,
            strokeWidth = 3f * density,
            cap = StrokeCap.Round,
        )
        drawCircle(color.copy(alpha = 0.95f * fade), radius = (3.5f + 2f * p) * density, center = head)
    }

    /** Particles drawn inward toward the card centre (not an explosion). */
    private fun DrawScope.drawConverge(frame: RevealFrame, geo: RevealGeometry, f: ParticleField, n: Int, primary: Color, secondary: Color) {
        val p = frame.particles
        if (p <= 0f) return
        val fadeOut = 1f - frame.surface
        if (fadeOut <= 0f) return
        for (i in 0 until min(n, f.count)) {
            val t = ((p - f.delay[i]) / (1f - f.delay[i])).coerceIn(0f, 1f)
            val e = RevealEngine.easeInOutCubic(t)
            val dist = geo.maxRadius * 0.6f * f.radius[i] * (1f - e)
            val ang = f.angle[i] + f.spin[i] * 0.6f * e
            val x = geo.center.x + cos(ang) * dist
            val y = geo.center.y + sin(ang) * dist
            val alpha = (0.4f + 0.6f * e) * fadeOut
            drawCircle(if (i % 3 == 0) secondary.copy(alpha = alpha) else primary.copy(alpha = alpha), f.size[i] * density, Offset(x, y))
        }
    }

    /** Particles bloom outward from the centre and settle into a halo around the card. */
    private fun DrawScope.drawBloom(frame: RevealFrame, geo: RevealGeometry, f: ParticleField, n: Int, primary: Color, secondary: Color) {
        val p = frame.particles
        if (p <= 0f) return
        val halo = min(geo.cardRect.width, geo.cardRect.height) * 0.62f
        for (i in 0 until min(n, f.count)) {
            val t = ((p - f.delay[i] * 0.5f) / (1f - f.delay[i] * 0.5f)).coerceIn(0f, 1f)
            val out = RevealEngine.easeOutBack(t, 1.1f)
            val dist = halo * (0.2f + 0.8f * f.radius[i]) * out
            val ang = f.angle[i] + f.spin[i] * 0.25f * frame.surface
            val x = geo.center.x + cos(ang) * dist * 1.25f
            val y = geo.center.y + sin(ang) * dist
            val alpha = (t * (1f - 0.6f * frame.cta)).coerceIn(0f, 1f) * 0.9f
            drawCircle(if (i % 4 == 0) secondary.copy(alpha = alpha) else primary.copy(alpha = alpha), f.size[i] * density * (1.3f - 0.4f * t), Offset(x, y))
        }
    }

    private fun DrawScope.drawOrbit(frame: RevealFrame, geo: RevealGeometry, f: ParticleField, n: Int, primary: Color, secondary: Color) {
        val p = frame.particles
        if (p <= 0f) return
        val rx = geo.cardRect.width * 0.62f
        val ry = geo.cardRect.height * 0.58f
        val fade = 1f - 0.7f * frame.cta
        for (i in 0 until min(n, f.count)) {
            val ang = f.angle[i] + p * 2.2f * PI.toFloat() * f.spin[i]
            val x = geo.center.x + cos(ang) * rx * (0.9f + 0.1f * f.radius[i])
            val y = geo.center.y + sin(ang) * ry * (0.9f + 0.1f * f.radius[i])
            drawCircle((if (i % 2 == 0) primary else secondary).copy(alpha = 0.8f * p * fade), f.size[i] * density, Offset(x, y))
        }
    }

    /** Soft grid that fills in, then dissolves as the card arrives. */
    private fun DrawScope.drawGrid(frame: RevealFrame, geo: RevealGeometry, n: Int, color: Color) {
        val cols = 6
        val rows = (n / cols).coerceAtLeast(1)
        val rect = geo.cardRect
        val cw = rect.width / cols
        val ch = rect.height / rows
        val fade = 1f - frame.surface
        if (fade <= 0f) return
        for (r in 0 until rows) for (c in 0 until cols) {
            val order = ((r * 7 + c * 3) % n).toFloat() / n
            val t = ((frame.particles - order * 0.6f) / 0.4f).coerceIn(0f, 1f)
            if (t <= 0f) continue
            val inset = 3f * density
            drawRoundRect(
                color.copy(alpha = 0.22f * t * fade),
                topLeft = Offset(rect.left + c * cw + inset, rect.top + r * ch + inset),
                size = Size(cw - 2 * inset, ch - 2 * inset),
                cornerRadius = CornerRadius(4f * density),
            )
        }
    }

    private fun DrawScope.drawConstellation(frame: RevealFrame, geo: RevealGeometry, f: ParticleField, n: Int, primary: Color, secondary: Color) {
        val p = frame.particles
        if (p <= 0f) return
        val count = min(n, f.count)
        val fade = 1f - 0.75f * frame.surface
        val rx = geo.cardRect.width * 0.7f
        val ry = geo.cardRect.height * 0.7f
        var px = 0f
        var py = 0f
        for (i in 0 until count) {
            val x = geo.center.x + cos(f.angle[i]) * rx * f.radius[i]
            val y = geo.center.y + sin(f.angle[i]) * ry * f.radius[i]
            val t = ((p - i.toFloat() / count * 0.7f) / 0.3f).coerceIn(0f, 1f)
            if (i > 0 && t > 0f) {
                drawLine(secondary.copy(alpha = 0.35f * t * fade), Offset(px, py), Offset(px + (x - px) * t, py + (y - py) * t), strokeWidth = 1f * density)
            }
            drawCircle(primary.copy(alpha = t * fade), f.size[i] * density, Offset(x, y))
            px = x
            py = y
        }
    }

    private fun lerp(a: Offset, b: Offset, t: Float) = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
}

/**
 * The Kipat contact line: starts at the card's top centre (where the contact ring lands) and runs
 * down both sides to meet at the bottom centre. Paths and measures are built once per card size;
 * drawing reuses one segment path, so there is no per-frame allocation.
 */
class EdgeTrace(rect: Rect, cornerRadius: Float) {
    private val right = PathMeasure()
    private val left = PathMeasure()
    private val segment = Path()

    init {
        val r = cornerRadius.coerceAtMost(minOf(rect.width, rect.height) / 2f)
        val cx = rect.center.x
        val rightPath = Path().apply {
            moveTo(cx, rect.top)
            lineTo(rect.right - r, rect.top)
            arcTo(Rect(rect.right - 2 * r, rect.top, rect.right, rect.top + 2 * r), -90f, 90f, false)
            lineTo(rect.right, rect.bottom - r)
            arcTo(Rect(rect.right - 2 * r, rect.bottom - 2 * r, rect.right, rect.bottom), 0f, 90f, false)
            lineTo(cx, rect.bottom)
        }
        val leftPath = Path().apply {
            moveTo(cx, rect.top)
            lineTo(rect.left + r, rect.top)
            arcTo(Rect(rect.left, rect.top, rect.left + 2 * r, rect.top + 2 * r), -90f, -90f, false)
            lineTo(rect.left, rect.bottom - r)
            arcTo(Rect(rect.left, rect.bottom - 2 * r, rect.left + 2 * r, rect.bottom), 180f, -90f, false)
            lineTo(cx, rect.bottom)
        }
        right.setPath(rightPath, false)
        left.setPath(leftPath, false)
    }

    fun draw(scope: DrawScope, progress: Float, color: Color, strokeWidth: Float) {
        if (progress <= 0f || color.alpha <= 0.01f) return
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        for (m in arrayOf(right, left)) {
            segment.reset()
            m.getSegment(0f, m.length * progress.coerceAtMost(1f), segment, true)
            scope.drawPath(segment, color, style = stroke)
        }
    }
}
