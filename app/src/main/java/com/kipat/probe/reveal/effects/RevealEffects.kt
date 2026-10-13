package com.kipat.probe.reveal.effects

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import com.kipat.probe.reveal.EntryPrimitive
import com.kipat.probe.reveal.KipatEasing
import com.kipat.probe.reveal.RevealChoreography
import com.kipat.probe.reveal.RevealEngine
import com.kipat.probe.reveal.RevealEngine.eased
import com.kipat.probe.reveal.RevealEngine.linear
import com.kipat.probe.reveal.RevealFrame
import com.kipat.probe.reveal.RevealTemplateId
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Where the primitives draw, in root pixels. [u] converts the design's dp values to pixels (the
 * screen density in the Reveal; smaller in picker glyphs); [hair] is the pixel width of a 1 dp
 * hairline, kept readable at glyph scale.
 */
class RevealGeometry(
    val card: Rect,
    val avatarCenter: Offset,
    val avatarRadius: Float,
    val cornerRadius: Float,
    val u: Float,
    val hair: Float = u,
)

/** Colours for primitives: one ink, the accent (completion only) and the card surface (iris seams). */
class RevealInk(val ink: Color, val accent: Color, val surface: Color)

/** Paths reused every frame so drawing allocates nothing. One instance per Reveal. */
class RevealPaths {
    internal val blade = Path()
    internal val clip = Path()
}

/**
 * The six templates' primary motions (design/reveal-templates/). Each template uses exactly one
 * primitive; beats are in ms from the first Reveal frame and match the template docs.
 *
 * - [drawUnder] runs before the card is drawn (ripple rings, drafting lines): the opaque card
 *   covers whatever part of the primitive lies on it.
 * - [drawOver] runs after the card (orbit ring, aperture blades), which sit on the avatar.
 */
object RevealEffects {

    fun DrawScope.drawUnder(ch: RevealChoreography, frame: RevealFrame, geo: RevealGeometry, ink: RevealInk) {
        when (ch.primitive) {
            EntryPrimitive.RIPPLE -> drawRipple(ch, frame.elapsedMs, geo, ink)
            EntryPrimitive.LINES -> drawLines(ch, frame, geo, ink)
            else -> Unit
        }
    }

    fun DrawScope.drawOver(ch: RevealChoreography, frame: RevealFrame, geo: RevealGeometry, ink: RevealInk, paths: RevealPaths) {
        when (ch.primitive) {
            EntryPrimitive.RIPPLE -> drawContactDot(ch, frame.elapsedMs, geo, ink)
            EntryPrimitive.ORBIT -> drawOrbit(ch, frame, geo, ink)
            EntryPrimitive.APERTURE -> drawAperture(ch, frame, geo, ink, paths)
            else -> Unit
        }
    }

    // ---- Contact Ripple ------------------------------------------------------------------

    /** The contact point: the card's top centre (unchanged since Signature v2). */
    fun rippleOrigin(card: Rect) = Offset(card.center.x, card.top)

    /** Farthest card corner from the contact point, plus a little so the edge clears the corners. */
    fun rippleMaxRadius(geo: RevealGeometry): Float = rippleMaxRadius(geo.card.width, geo.card.height, geo.u)

    /** Same, from the card's size (the bottom corners are equidistant from the top centre). */
    fun rippleMaxRadius(width: Float, height: Float, u: Float): Float = hypot(width / 2f, height) + 8f * u

    /**
     * Radius (px) of the circular reveal that uncovers the card in Contact Ripple, or null once
     * the card is fully uncovered (or the template doesn't mask).
     */
    fun revealRadius(ch: RevealChoreography, elapsedMs: Long, maxRadius: Float): Float? {
        if (ch.primitive != EntryPrimitive.RIPPLE || ch.reduced) return null
        val p = linear(elapsedMs, RIPPLE_START, RIPPLE_DURATION)
        if (p >= 1f) return null
        return maxRadius * KipatEasing.Fast.ease(p)
    }

    /** Ripple timing: ring A is also the edge of the card's circular reveal. */
    private const val RIPPLE_START = 40
    private const val RIPPLE_DURATION = 720

    /** The contact dot sits on the card's top edge, so it draws above the card. */
    private fun DrawScope.drawContactDot(ch: RevealChoreography, ms: Long, geo: RevealGeometry, ink: RevealInk) {
        val o = rippleOrigin(geo.card)
        val a = if (ch.reduced) {
            // Contact acknowledgement: the dot fades in and out once.
            sin(PI.toFloat() * linear(ms, 0, 200))
        } else {
            eased(ms, 0, 100, KipatEasing.Fast) * (1f - eased(ms, 160, 200, KipatEasing.Exit))
        }
        if (a > 0.01f) drawCircle(ink.ink.copy(alpha = 0.9f * a), 2.5f * geo.u, o)
    }

    private fun DrawScope.drawRipple(ch: RevealChoreography, ms: Long, geo: RevealGeometry, ink: RevealInk) {
        if (ch.reduced) return
        val o = rippleOrigin(geo.card)
        val rMax = rippleMaxRadius(geo)
        val a = linear(ms, RIPPLE_START, RIPPLE_DURATION)
        if (a > 0f && a < 1f) {
            drawCircle(ink.ink.copy(alpha = 0.8f * (1f - a)), rMax * KipatEasing.Fast.ease(a), o, style = Stroke(1.25f * geo.hair))
        }
        val b = linear(ms, 200, 700)
        if (b > 0f && b < 1f) {
            drawCircle(ink.ink.copy(alpha = 0.4f * (1f - b)), 1.25f * rMax * KipatEasing.Fast.ease(b), o, style = Stroke(1f * geo.hair))
        }
    }

    // ---- Precision Orbit -----------------------------------------------------------------

    private fun DrawScope.drawOrbit(ch: RevealChoreography, frame: RevealFrame, geo: RevealGeometry, ink: RevealInk) {
        val c = geo.avatarCenter
        val r0 = geo.avatarRadius + 10f * geo.u
        val settled = 0.35f
        if (ch.reduced) {
            // Static ring: the settled state fades in with the card.
            drawCircle(ink.accent.copy(alpha = settled * frame.surface), r0 + 4f * geo.u, c, style = Stroke(1f * geo.hair))
            return
        }
        val ms = frame.elapsedMs
        // Contact pulse, cleared before the mechanism moves (Higgsfield v3 #4).
        val dotIn = eased(ms, 0, 120, KipatEasing.Fast)
        val dotOut = 1f - eased(ms, 120, 240, KipatEasing.Exit)
        if (dotIn * dotOut > 0.01f) drawCircle(ink.ink.copy(alpha = dotIn * dotOut), 3f * geo.u * dotIn, c)

        val appear = eased(ms, 100, 120, KipatEasing.Fast)
        if (appear <= 0f) return
        // A quarter turn, once; the arcs stay symmetric (180° apart, equal sweep) so it never reads as a spinner.
        val rot = 90f * KipatEasing.emphasized(linear(ms, 120, 430))
        val sweep = 100f + 80f * eased(ms, 300, 250)
        val expand = eased(ms, 450, 250)
        val radius = r0 + 4f * geo.u * expand
        val stroke = Stroke((1.5f - 0.5f * expand) * geo.hair, cap = StrokeCap.Butt)
        val color = lerp(ink.ink, ink.accent, eased(ms, 550, 150))
        val alpha = appear * (1f - (1f - settled) * eased(ms, 1100, 300))
        val topLeft = Offset(c.x - radius, c.y - radius)
        val size = Size(radius * 2, radius * 2)
        if (sweep >= 179.5f) {
            drawCircle(color.copy(alpha = alpha), radius, c, style = stroke)
        } else {
            val start = -90f - sweep / 2f + rot
            drawArc(color.copy(alpha = alpha), start, sweep, false, topLeft, size, style = stroke)
            drawArc(color.copy(alpha = alpha), start + 180f, sweep, false, topLeft, size, style = stroke)
        }
    }

    // ---- Line Assembly -------------------------------------------------------------------

    private fun DrawScope.drawLines(ch: RevealChoreography, frame: RevealFrame, geo: RevealGeometry, ink: RevealInk) {
        val r = geo.card
        val o = 12f * geo.u
        val w = 1f * geo.hair
        val line = ink.ink.copy(alpha = 0.75f)
        if (ch.reduced) {
            // Perimeter fade: all four lines present, leaving as the card fades in.
            val a = 1f - frame.surface
            if (a <= 0.01f) return
            val c = line.copy(alpha = line.alpha * a)
            drawLine(c, Offset(r.left - o, r.top), Offset(r.right + o, r.top), w)
            drawLine(c, Offset(r.left, r.top - o), Offset(r.left, r.bottom + o), w)
            drawLine(c, Offset(r.right, r.top - o), Offset(r.right, r.bottom + o), w)
            drawLine(c, Offset(r.left - o, r.bottom), Offset(r.right + o, r.bottom), w)
            return
        }
        val ms = frame.elapsedMs
        val leave = 1f - eased(ms, 900, 300, KipatEasing.Exit)
        if (leave <= 0f) return
        val mark = eased(ms, 0, 150, KipatEasing.Fast)
        if (mark > 0f) drawCircle(ink.accent.copy(alpha = mark * leave), 2f * geo.u * mark + 0.5f * geo.hair, Offset(r.left, r.top))

        val top = KipatEasing.emphasized(linear(ms, 150, 230))
        if (top > 0f) {
            val c = line.copy(alpha = line.alpha * leave)
            drawLine(c, Offset(r.left - o, r.top), Offset(r.left - o + (r.width + 2 * o) * top, r.top), w)
        }
        val left = KipatEasing.emphasized(linear(ms, 300, 220))
        if (left > 0f) {
            val c = line.copy(alpha = line.alpha * leave)
            drawLine(c, Offset(r.left, r.top - o), Offset(r.left, r.top - o + (r.height + 2 * o) * left), w)
        }
        // The remaining geometry fades in rather than being drawn.
        val rest = eased(ms, 450, 250) * leave
        if (rest > 0f) {
            val c = line.copy(alpha = line.alpha * rest)
            drawLine(c, Offset(r.right, r.top - o), Offset(r.right, r.bottom + o), w)
            drawLine(c, Offset(r.left - o, r.bottom), Offset(r.right + o, r.bottom), w)
        }
    }

    // ---- Aperture ------------------------------------------------------------------------

    private const val BLADES = 6

    private fun DrawScope.drawAperture(ch: RevealChoreography, frame: RevealFrame, geo: RevealGeometry, ink: RevealInk, paths: RevealPaths) {
        if (ch.reduced) return
        val ms = frame.elapsedMs
        val alpha = frame.surface * (1f - eased(ms, 480, 160, KipatEasing.Exit))
        if (alpha <= 0.01f) return
        val c = geo.avatarCenter
        val ro = geo.avatarRadius + 8f * geo.u
        val anticipation = -5f * eased(ms, 120, 100, KipatEasing.Fast)
        val open = KipatEasing.emphasized(linear(ms, 220, 340))
        // Never fully closed (radial seams read as a pie chart) and never past the rim (the
        // construction breaks there): see design/higgsfield/critique-v3.md.
        val r = ro * (0.12f + 0.86f * open)
        val rotation = anticipation + 17f * open
        drawIris(c, ro, r, rotation, ink.ink.copy(alpha = 0.88f * alpha), ink.surface.copy(alpha = alpha), geo.hair, paths)
    }

    /**
     * A flat iris: the annulus between a rotated hexagon (radius [r]) and a circle ([ro]), split
     * into blades along the hexagon's extended edges. Seams are stroked in [seam] (the surface
     * colour), so no offscreen layer is needed.
     */
    fun DrawScope.drawIris(c: Offset, ro: Float, r: Float, rotationDeg: Float, blade: Color, seam: Color, hair: Float, paths: RevealPaths) {
        val th = rotationDeg * PI.toFloat() / 180f
        val step = 2f * PI.toFloat() / BLADES
        val oval = Rect(c.x - ro, c.y - ro, c.x + ro, c.y + ro)
        paths.clip.reset()
        paths.clip.addOval(oval)
        clipPath(paths.clip) {
            for (i in 0 until BLADES) {
                val a0 = th + step * i
                val a1 = th + step * (i + 1)
                val a2 = th + step * (i - 1)
                val vx = c.x + r * cos(a0); val vy = c.y + r * sin(a0)
                val nx = c.x + r * cos(a1); val ny = c.y + r * sin(a1)
                val px = c.x + r * cos(a2); val py = c.y + r * sin(a2)
                // e: edge (v → next) extended beyond next to the rim; f: edge (prev → v) extended beyond v.
                val (ex, ey) = extendToRim(c, ro, vx, vy, nx, ny)
                val (fx, fy) = extendToRim(c, ro, px, py, vx, vy)
                var start = atan2(ey - c.y, ex - c.x)
                val end = atan2(fy - c.y, fx - c.x)
                while (start < end) start += 2f * PI.toFloat()
                paths.blade.reset()
                paths.blade.moveTo(vx, vy)
                paths.blade.lineTo(nx, ny)
                paths.blade.lineTo(ex, ey)
                paths.blade.arcTo(oval, start * 180f / PI.toFloat(), -(start - end) * 180f / PI.toFloat(), false)
                paths.blade.close()
                drawPath(paths.blade, blade)
            }
            for (i in 0 until BLADES) {
                val a0 = th + step * i
                val a1 = th + step * (i + 1)
                val vx = c.x + r * cos(a0); val vy = c.y + r * sin(a0)
                val nx = c.x + r * cos(a1); val ny = c.y + r * sin(a1)
                val (ex, ey) = extendToRim(c, ro, vx, vy, nx, ny)
                drawLine(seam, Offset(nx, ny), Offset(ex, ey), hair)
            }
        }
    }

    /** Continue the ray a → b beyond b until it meets the circle (c, ro). Returns the hit point. */
    private fun extendToRim(c: Offset, ro: Float, ax: Float, ay: Float, bx: Float, by: Float): Pair<Float, Float> {
        var dx = bx - ax
        var dy = by - ay
        val len = hypot(dx, dy).coerceAtLeast(1e-4f)
        dx /= len; dy /= len
        val px = bx - c.x
        val py = by - c.y
        val b = px * dx + py * dy
        val cc = px * px + py * py - ro * ro
        val t = -b + sqrt((b * b - cc).coerceAtLeast(0f))
        return (bx + dx * t) to (by + dy * t)
    }

    // ---- Spatial Card --------------------------------------------------------------------

    const val MAX_TILT_DEGREES = 6f

    /** rotationX of the card: 6° → 0 on the Kipat settle spring (ζ 0.9). Zero when reduced. */
    fun tiltDegrees(ch: RevealChoreography, elapsedMs: Long): Float =
        if (ch.primitive != EntryPrimitive.EDGE || ch.reduced) 0f
        else MAX_TILT_DEGREES * (1f - KipatEasing.spring(elapsedMs)).coerceIn(0f, 1f)

    // ---- Picker glyphs -------------------------------------------------------------------

    /** The moment each template is recognisable by; the picker draws the real primitive at it. */
    fun keyframeMs(template: RevealTemplateId): Long = when (template) {
        RevealTemplateId.CONTACT_RIPPLE -> 330
        RevealTemplateId.PRECISION_ORBIT -> 380
        RevealTemplateId.LINE_ASSEMBLY -> 560
        RevealTemplateId.APERTURE -> 200
        RevealTemplateId.SPATIAL_CARD -> 0
        RevealTemplateId.MINIMAL_FADE -> 160
    }

    /**
     * A small static frame of [template] at [keyframeMs], drawn with the same primitives as the
     * Reveal on a miniature card (a 300 × 190 dp card scaled into the canvas).
     */
    fun DrawScope.drawTemplateGlyph(template: RevealTemplateId, ink: RevealInk, cardColor: Color, avatarColor: Color, edgeColor: Color, hair: Float, paths: RevealPaths) {
        val ch = RevealChoreography.forTemplate(template)
        val ms = keyframeMs(template)
        val frame = RevealEngine.frameAt(ms, ch.timeline)
        val u = size.width * 0.78f / 300f
        val w = 300f * u
        val h = 190f * u
        val card = Rect(Offset((size.width - w) / 2f, (size.height - h) / 2f), Size(w, h))
        val geo = RevealGeometry(card, Offset(card.left + 52f * u, card.top + 52f * u), 28f * u, 16f * u, u, hair)

        drawUnder(ch, frame, geo, ink)
        val reveal = revealRadius(ch, ms, rippleMaxRadius(geo))
        val drawCard: DrawScope.() -> Unit = {
            if (template == RevealTemplateId.SPATIAL_CARD) {
                // Illustrative: the arriving card, top edge slightly further away, its edge showing.
                val inset = w * 0.03f
                paths.blade.reset()
                paths.blade.moveTo(card.left + inset, card.top + 3f * u)
                paths.blade.lineTo(card.right - inset, card.top + 3f * u)
                paths.blade.lineTo(card.right, card.bottom)
                paths.blade.lineTo(card.left, card.bottom)
                paths.blade.close()
                drawRoundRect(edgeColor, Offset(card.left, card.top + 6f * u), card.size, CornerRadius(16f * u))
                drawPath(paths.blade, cardColor)
            } else {
                // A 30% floor keeps the card's context visible in every thumbnail.
                drawRoundRect(cardColor.copy(alpha = cardColor.alpha * frame.surface.coerceAtLeast(0.3f)), card.topLeft, card.size, CornerRadius(16f * u))
            }
            drawCircle(avatarColor.copy(alpha = frame.avatar * frame.surface.coerceAtLeast(0.4f)), 28f * u, geo.avatarCenter)
            val textLeft = card.left + 24f * u
            drawRoundRect(ink.ink.copy(alpha = 0.85f * frame.name), Offset(textLeft, card.top + 100f * u), Size(150f * u, 16f * u), CornerRadius(3f * u))
            drawRoundRect(ink.ink.copy(alpha = 0.45f * frame.role), Offset(textLeft, card.top + 128f * u), Size(110f * u, 11f * u), CornerRadius(3f * u))
        }
        if (reveal != null) {
            paths.clip.reset()
            val o = rippleOrigin(card)
            paths.clip.addOval(Rect(o.x - reveal, o.y - reveal, o.x + reveal, o.y + reveal))
            clipPath(paths.clip) { drawCard() }
        } else {
            drawCard()
        }
        drawOver(ch, frame, geo, ink, paths)
    }
}
