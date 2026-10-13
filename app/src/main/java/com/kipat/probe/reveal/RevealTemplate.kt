package com.kipat.probe.reveal

/** How completely a template is implemented in the native runtime today. */
enum class TemplateSupport(val label: String) {
    /** Dedicated choreography tuned for this template. */
    FULL("Full"),
    /** Runs on shared primitives with template-specific parameters. */
    PARAMETERIZED("Preview"),
}

/**
 * The initial Reveal catalog from the engineering spec. All ten are modelled; three are fully
 * choreographed and the rest are parameterisations of the same shared engine primitives.
 */
enum class RevealTemplateId(
    val wireId: String,
    val label: String,
    val description: String,
    val support: TemplateSupport,
    val requiresPlus: Boolean = false,
) {
    SIGNATURE_DRIP("drip", "Signature", "A contact ring, then a line traces your card as it appears", TemplateSupport.FULL),
    PARTICLE_BLOOM("particle-bloom", "Particle Bloom", "Dots spread out and settle around your card", TemplateSupport.FULL),
    RIPPLE("ripple", "Ripple", "Concentric rings from the tap point", TemplateSupport.PARAMETERIZED),
    ORBIT("orbit", "Orbit", "Light orbits the card before it lands", TemplateSupport.PARAMETERIZED, requiresPlus = true),
    SOFT_GLOW("soft-glow", "Soft Glow", "A quiet bloom of light", TemplateSupport.PARAMETERIZED),
    LIQUID_LOGO("liquid-logo", "Liquid Logo", "Your mark swells into place", TemplateSupport.PARAMETERIZED, requiresPlus = true),
    LINE_TRACE("line-trace", "Line Trace", "A single line traces the card outline", TemplateSupport.PARAMETERIZED),
    MINIMAL_FADE("minimal-fade", "Minimal", "A quick, quiet fade", TemplateSupport.FULL),
    PIXEL_BUILD("pixel-build", "Pixel Build", "The card assembles from a soft grid", TemplateSupport.PARAMETERIZED, requiresPlus = true),
    CONSTELLATION("constellation", "Constellation", "Points connect into your card", TemplateSupport.PARAMETERIZED, requiresPlus = true);

    companion object {
        val DEFAULT = SIGNATURE_DRIP

        /** Unknown or missing template IDs fall back to the signature Reveal, never crash. */
        fun fromWireId(value: String?): RevealTemplateId {
            val v = value?.trim().orEmpty()
            return entries.firstOrNull { it.wireId.equals(v, ignoreCase = true) || it.name.equals(v, ignoreCase = true) } ?: DEFAULT
        }

        fun isKnown(value: String?): Boolean {
            val v = value?.trim().orEmpty()
            return entries.any { it.wireId.equals(v, ignoreCase = true) || it.name.equals(v, ignoreCase = true) }
        }
    }
}

/** Accent used by a person's Reveal (contact ring, edge trace, Connect). See [com.kipat.probe.ui.theme.RevealPalette]. */
enum class RevealTheme(val wireId: String, val label: String) {
    // Wire IDs are stable (stored on cards and links); labels describe the accent colour.
    NIGHT("night", "Cobalt"),
    AURORA("aurora", "Teal"),
    EMBER("ember", "Amber"),
    MONO("mono", "Graphite");

    companion object {
        fun fromWireId(value: String?): RevealTheme =
            entries.firstOrNull { it.wireId.equals(value?.trim(), ignoreCase = true) } ?: NIGHT
    }
}

/** Rendering budget chosen per device/accessibility setting. */
enum class RevealQuality {
    /** Full particle field and layered glow. */
    HIGH,
    /** Weak/low-RAM devices: few particles, single glow layer. */
    LOW,
    /** System animations disabled or user asked for reduced motion: fades only. */
    REDUCED_MOTION,
}

enum class ParticleMotion { NONE, CONVERGE, BLOOM, ORBIT, GRID, CONSTELLATION }

enum class SurfaceEntrance { RISE, FADE, TRACE, LIQUID }

/**
 * Parameters for the shared Reveal engine. Templates differ by configuration, not by
 * separate animation implementations.
 */
data class RevealConfig(
    val template: RevealTemplateId,
    val timeline: RevealTimeline,
    val rippleRings: Int,
    val particleCount: Int,
    val particleMotion: ParticleMotion,
    val glowStrength: Float,
    val drip: Boolean,
    val surfaceEntrance: SurfaceEntrance,
    /** Strength (0 = off) of the line that traces the card outline from the contact point. */
    val perimeterTrace: Float = 0f,
    /** Ripple radius as a fraction of the screen; small = a contact ring, large = template ripples. */
    val rippleScale: Float = 0.6f,
) {
    companion object {
        const val MAX_PARTICLES = 56
        const val LOW_QUALITY_PARTICLES = 12

        fun forTemplate(template: RevealTemplateId, quality: RevealQuality = RevealQuality.HIGH): RevealConfig {
            if (quality == RevealQuality.REDUCED_MOTION) {
                return RevealConfig(template, RevealTimeline.REDUCED, 0, 0, ParticleMotion.NONE, 0.25f, false, SurfaceEntrance.FADE)
            }
            val base = when (template) {
                // Calm signature: one contact ring + a precise edge trace. No particles, glow or drip.
                RevealTemplateId.SIGNATURE_DRIP -> RevealConfig(template, RevealTimeline.SIGNATURE, 1, 0, ParticleMotion.NONE, 0f, false, SurfaceEntrance.RISE, perimeterTrace = 1f, rippleScale = 0.12f)
                RevealTemplateId.PARTICLE_BLOOM -> RevealConfig(template, RevealTimeline.SIGNATURE, 1, MAX_PARTICLES, ParticleMotion.BLOOM, 0.85f, false, SurfaceEntrance.RISE)
                RevealTemplateId.MINIMAL_FADE -> RevealConfig(template, RevealTimeline.MINIMAL, 0, 0, ParticleMotion.NONE, 0.35f, false, SurfaceEntrance.FADE)
                RevealTemplateId.RIPPLE -> RevealConfig(template, RevealTimeline.SIGNATURE, 3, 0, ParticleMotion.NONE, 0.7f, false, SurfaceEntrance.RISE)
                RevealTemplateId.ORBIT -> RevealConfig(template, RevealTimeline.SIGNATURE, 1, 24, ParticleMotion.ORBIT, 0.8f, false, SurfaceEntrance.RISE)
                RevealTemplateId.SOFT_GLOW -> RevealConfig(template, RevealTimeline.SIGNATURE, 0, 0, ParticleMotion.NONE, 1f, false, SurfaceEntrance.FADE)
                RevealTemplateId.LIQUID_LOGO -> RevealConfig(template, RevealTimeline.SIGNATURE, 1, 0, ParticleMotion.NONE, 0.8f, true, SurfaceEntrance.LIQUID)
                RevealTemplateId.LINE_TRACE -> RevealConfig(template, RevealTimeline.SIGNATURE, 0, 0, ParticleMotion.NONE, 0.5f, false, SurfaceEntrance.TRACE, perimeterTrace = 0.9f)
                RevealTemplateId.PIXEL_BUILD -> RevealConfig(template, RevealTimeline.SIGNATURE, 0, 36, ParticleMotion.GRID, 0.5f, false, SurfaceEntrance.FADE)
                RevealTemplateId.CONSTELLATION -> RevealConfig(template, RevealTimeline.SIGNATURE, 0, 18, ParticleMotion.CONSTELLATION, 0.6f, false, SurfaceEntrance.RISE)
            }
            return if (quality == RevealQuality.LOW) {
                base.copy(
                    particleCount = base.particleCount.coerceAtMost(LOW_QUALITY_PARTICLES),
                    rippleRings = base.rippleRings.coerceAtMost(1),
                    glowStrength = base.glowStrength * 0.7f,
                )
            } else {
                base
            }
        }
    }
}
