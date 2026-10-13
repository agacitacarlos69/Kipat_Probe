package com.kipat.probe.reveal

import kotlin.math.pow

/**
 * Reveal choreography in milliseconds, measured from the moment the Reveal starts drawing.
 *
 * [SIGNATURE] targets identity readable in under a second and everything settled by ~1.55 s
 * (faster than the original 1.8 s spec). These are design targets, not measured device results.
 */
data class RevealTimeline(
    val rippleStart: Int,
    val rippleDuration: Int,
    val glowPeakAt: Int,
    val particlesStart: Int,
    val particlesDuration: Int,
    val surfaceStart: Int,
    val surfaceDuration: Int,
    val avatarStart: Int,
    val nameStart: Int,
    val roleStart: Int,
    val verifyStart: Int,
    val ctaStart: Int,
    val stepDuration: Int,
) {
    /** When everything has settled and the Reveal is considered complete. */
    val totalDuration: Int get() = ctaStart + stepDuration

    companion object {
        /**
         * Signature v2 (design/higgsfield/critique-v2.md): contact ring 0–300, edge trace 300–650,
         * card 500–800, avatar 700, name 850, lines 950, trust 1100, actions 1250, settled 1550.
         */
        val SIGNATURE = RevealTimeline(
            rippleStart = 0, rippleDuration = 300, glowPeakAt = 150,
            particlesStart = 300, particlesDuration = 350,
            surfaceStart = 500, surfaceDuration = 300,
            avatarStart = 700, nameStart = 850, roleStart = 950,
            verifyStart = 1100, ctaStart = 1250, stepDuration = 300,
        )

        val MINIMAL = RevealTimeline(
            rippleStart = 0, rippleDuration = 0, glowPeakAt = 120,
            particlesStart = 0, particlesDuration = 0,
            surfaceStart = 60, surfaceDuration = 320,
            avatarStart = 160, nameStart = 260, roleStart = 360,
            verifyStart = 460, ctaStart = 560, stepDuration = 240,
        )

        /** Reduced motion: one short fade, everything readable immediately after. */
        val REDUCED = RevealTimeline(
            rippleStart = 0, rippleDuration = 0, glowPeakAt = 0,
            particlesStart = 0, particlesDuration = 0,
            surfaceStart = 0, surfaceDuration = 200,
            avatarStart = 0, nameStart = 0, roleStart = 0,
            verifyStart = 0, ctaStart = 0, stepDuration = 200,
        )
    }
}

/** Snapshot of every Reveal channel at one instant. All values are eased 0..1. */
data class RevealFrame(
    val ripple: Float,
    val glow: Float,
    val particles: Float,
    val surface: Float,
    val avatar: Float,
    val name: Float,
    val role: Float,
    val verify: Float,
    val cta: Float,
) {
    val isComplete: Boolean get() = cta >= 1f

    companion object {
        val SETTLED = RevealFrame(1f, 0.35f, 1f, 1f, 1f, 1f, 1f, 1f, 1f)
    }
}

/** Pure timing math for the Reveal. No Android or Compose types so it is unit testable. */
object RevealEngine {

    fun linear(elapsedMs: Long, startMs: Int, durationMs: Int): Float {
        if (durationMs <= 0) return if (elapsedMs >= startMs) 1f else 0f
        return ((elapsedMs - startMs).toFloat() / durationMs).coerceIn(0f, 1f)
    }

    fun easeOutCubic(t: Float): Float = 1f - (1f - t).pow(3)

    fun easeInOutCubic(t: Float): Float =
        if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f

    /** Slight overshoot for the identity surface; settles exactly at 1. */
    fun easeOutBack(t: Float, overshoot: Float = 1.2f): Float {
        val c3 = overshoot + 1f
        return 1f + c3 * (t - 1f).pow(3) + overshoot * (t - 1f).pow(2)
    }

    fun frameAt(elapsedMs: Long, timeline: RevealTimeline): RevealFrame {
        val step = timeline.stepDuration
        val glowRise = linear(elapsedMs, 0, timeline.glowPeakAt.coerceAtLeast(1))
        val glowFall = linear(elapsedMs, timeline.glowPeakAt, timeline.surfaceStart + timeline.surfaceDuration - timeline.glowPeakAt)
        val glow = if (timeline.glowPeakAt == 0) 0.35f else easeOutCubic(glowRise) * (1f - 0.65f * easeInOutCubic(glowFall))
        return RevealFrame(
            ripple = easeOutCubic(linear(elapsedMs, timeline.rippleStart, timeline.rippleDuration)),
            glow = glow,
            particles = easeInOutCubic(linear(elapsedMs, timeline.particlesStart, timeline.particlesDuration)),
            surface = easeOutCubic(linear(elapsedMs, timeline.surfaceStart, timeline.surfaceDuration)),
            avatar = easeOutCubic(linear(elapsedMs, timeline.avatarStart, step)),
            name = easeOutCubic(linear(elapsedMs, timeline.nameStart, step)),
            role = easeOutCubic(linear(elapsedMs, timeline.roleStart, step)),
            verify = easeOutCubic(linear(elapsedMs, timeline.verifyStart, step)),
            cta = easeOutCubic(linear(elapsedMs, timeline.ctaStart, step)),
        )
    }
}
