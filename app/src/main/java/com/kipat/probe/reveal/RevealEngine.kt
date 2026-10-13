package com.kipat.probe.reveal

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * When the card and its content arrive, in milliseconds from the first Reveal frame. Each
 * template's primary motion has its own beats (see [com.kipat.probe.reveal.effects.RevealEffects]
 * and design/reveal-templates/). These are design targets, not device measurements.
 */
data class RevealTimeline(
    val surfaceStart: Int,
    val surfaceDuration: Int,
    val avatarStart: Int,
    val nameStart: Int,
    val roleStart: Int,
    val verifyStart: Int,
    val ctaStart: Int,
    val stepDuration: Int,
    /** When the template's primitive has fully finished (rings gone, lines faded, tilt flat). */
    val primitiveEndMs: Int = 0,
) {
    /** When the actions are in place. */
    val contentSettledAt: Int get() = ctaStart + stepDuration

    /**
     * When the Reveal is complete and the clock stops: content settled *and* the primitive done,
     * so nothing is ever frozen mid-motion. After this the frame is static.
     */
    val totalDuration: Int get() = maxOf(contentSettledAt, primitiveEndMs)

    /** When name and role/organisation are fully shown. */
    val identityReadableAt: Int get() = maxOf(nameStart, roleStart) + stepDuration

    /** When the trust line is fully shown (the verified haptic fires here). */
    val verifiedAt: Int get() = verifyStart + stepDuration

    companion object {
        // Content, trust and actions arrive as the surface reaches them (design/reveal-surface/):
        // no empty card, no empty lower half waiting for buttons.
        val CONTACT_RIPPLE = RevealTimeline(40, 120, 120, 180, 240, 300, 380, 300, primitiveEndMs = 900)
        val PRECISION_ORBIT = RevealTimeline(560, 80, 560, 700, 760, 780, 860, 300, primitiveEndMs = 1160)
        val LINE_ASSEMBLY = RevealTimeline(600, 300, 640, 680, 720, 780, 840, 300, primitiveEndMs = 1200)
        val APERTURE = RevealTimeline(40, 120, 40, 600, 640, 700, 780, 300, primitiveEndMs = 640)
        val SPATIAL_CARD = RevealTimeline(0, 250, 60, 100, 140, 340, 400, 300, primitiveEndMs = 700)
        val MINIMAL_FADE = RevealTimeline(40, 280, 40, 40, 40, 300, 380, 240)

        /** Reduced motion: one 200 ms fade; everything readable as soon as it starts. */
        val REDUCED = RevealTimeline(0, 200, 0, 0, 0, 0, 0, 200)
    }
}

/** Every channel at one instant (eased 0..1), plus the raw clock for template primitives. */
data class RevealFrame(
    val elapsedMs: Long,
    val surface: Float,
    val avatar: Float,
    val name: Float,
    val role: Float,
    val verify: Float,
    val cta: Float,
) {
    val isComplete: Boolean get() = cta >= 1f
}

/**
 * Kipat motion curves (design/reveal-templates/README.md#kipat-motion-system). Pure Kotlin so the
 * choreography is unit testable; values are Material 3's published easing curves.
 */
object KipatEasing {
    /** M3 standard. Contact marks, small fades, actions. */
    val Fast = CubicBezier(0.2f, 0f, 0f, 1f)
    /** M3 emphasized decelerate. Anything entering. */
    val Standard = CubicBezier(0.05f, 0.7f, 0.1f, 1f)
    /** M3 emphasized accelerate. Primitives leaving. */
    val Exit = CubicBezier(0.3f, 0f, 0.8f, 0.15f)

    /** M3 emphasized: two cubic segments joined at (0.166666, 0.4). A template's one geometric move. */
    fun emphasized(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        val split = 0.166666f
        return if (x < split) {
            0.4f * EmphasizedA.ease(x / split)
        } else {
            0.4f + 0.6f * EmphasizedB.ease((x - split) / (1f - split))
        }
    }

    // The two halves of M3 emphasized, normalised to the unit square.
    private val EmphasizedA = CubicBezier(0.05f / 0.166666f, 0f, 0.133333f / 0.166666f, 0.06f / 0.4f)
    private val EmphasizedB = CubicBezier(
        (0.208333f - 0.166666f) / 0.833334f, (0.82f - 0.4f) / 0.6f,
        (0.25f - 0.166666f) / 0.833334f, 1f,
    )

    /**
     * Normalised step response of a damped spring released from 0 towards 1 (mass 1). With
     * [dampingRatio] 0.9 the overshoot is ~0.15%: it settles once, never bounces visibly.
     */
    fun spring(elapsedMs: Long, stiffness: Float = 300f, dampingRatio: Float = 0.9f): Float {
        if (elapsedMs <= 0) return 0f
        val t = elapsedMs / 1000f
        val w = sqrt(stiffness)
        val z = dampingRatio
        val v = if (z < 1f) {
            val wd = w * sqrt(1f - z * z)
            1f - exp(-z * w * t) * (cos(wd * t) + (z * w / wd) * sin(wd * t))
        } else {
            1f - exp(-w * t) * (1f + w * t)
        }
        return v
    }
}

/** CSS-style cubic Bézier easing from (0,0) to (1,1); solved with Newton steps then bisection. */
class CubicBezier(private val x1: Float, private val y1: Float, private val x2: Float, private val y2: Float) {
    private fun bez(t: Float, a: Float, b: Float): Float {
        val u = 1f - t
        return 3f * u * u * t * a + 3f * u * t * t * b + t * t * t
    }

    private fun slopeX(t: Float): Float {
        val u = 1f - t
        return 3f * u * u * x1 + 6f * u * t * (x2 - x1) + 3f * t * t * (1f - x2)
    }

    fun ease(x: Float): Float {
        if (x <= 0f) return 0f
        if (x >= 1f) return 1f
        var t = x
        repeat(6) {
            val err = bez(t, x1, x2) - x
            val d = slopeX(t)
            if (abs(err) < 1e-5f) return bez(t, y1, y2)
            if (abs(d) < 1e-6f) return@repeat
            t = (t - err / d).coerceIn(0f, 1f)
        }
        var lo = 0f
        var hi = 1f
        repeat(20) {
            val mid = (lo + hi) / 2f
            if (bez(mid, x1, x2) < x) lo = mid else hi = mid
        }
        return bez((lo + hi) / 2f, y1, y2)
    }
}

/** Pure timing math for the Reveal. No Android or Compose types, so it is unit testable. */
object RevealEngine {

    fun linear(elapsedMs: Long, startMs: Int, durationMs: Int): Float {
        if (durationMs <= 0) return if (elapsedMs >= startMs) 1f else 0f
        return ((elapsedMs - startMs).toFloat() / durationMs).coerceIn(0f, 1f)
    }

    /** [linear] through a [CubicBezier]. */
    fun eased(elapsedMs: Long, startMs: Int, durationMs: Int, curve: CubicBezier = KipatEasing.Standard): Float =
        curve.ease(linear(elapsedMs, startMs, durationMs))

    fun frameAt(elapsedMs: Long, timeline: RevealTimeline): RevealFrame {
        val step = timeline.stepDuration
        return RevealFrame(
            elapsedMs = elapsedMs,
            surface = eased(elapsedMs, timeline.surfaceStart, timeline.surfaceDuration),
            avatar = eased(elapsedMs, timeline.avatarStart, step),
            name = eased(elapsedMs, timeline.nameStart, step),
            role = eased(elapsedMs, timeline.roleStart, step),
            verify = eased(elapsedMs, timeline.verifyStart, step),
            cta = eased(elapsedMs, timeline.ctaStart, step),
        )
    }
}
