package com.kipat.probe.reveal

/**
 * The Reveal template family (design/reveal-templates/). Six interpretations of one identity
 * card: they share the payload, layout and actions and differ only in entrance choreography.
 *
 * [wireId]s are written to cards and links (`t=` in the tag URI), so they are stable and short.
 */
enum class RevealTemplateId(
    val wireId: String,
    val label: String,
    /** One line for the picker: the template's character, not its mechanics. */
    val description: String,
    /** Reserved for billing (see [com.kipat.probe.model.Entitlement]); nothing is gated today. */
    val requiresPlus: Boolean = false,
) {
    CONTACT_RIPPLE("ripple", "Contact Ripple", "Subtle and tactile"),
    PRECISION_ORBIT("orbit", "Precision Orbit", "Measured, geometric"),
    LINE_ASSEMBLY("lines", "Line Assembly", "Architectural precision"),
    APERTURE("aperture", "Aperture", "Focused and technical"),
    SPATIAL_CARD("spatial", "Spatial Card", "Subtle physical depth"),
    MINIMAL_FADE("minimal-fade", "Minimal Fade", "Quiet and immediate");

    companion object {
        /** Selected in design/reveal-templates/final-scorecard.md. */
        val DEFAULT = CONTACT_RIPPLE

        /** Fallback when the device or the person asks for less motion or power. */
        val LOW_POWER = MINIMAL_FADE

        /**
         * IDs from the retired v1/v2 catalog that may still be on written cards. They map to the
         * closest current template, or to the default when nothing is close.
         */
        val LEGACY_ALIASES: Map<String, RevealTemplateId> = mapOf(
            "drip" to DEFAULT,
            "line-trace" to LINE_ASSEMBLY,
            "fade" to MINIMAL_FADE,
            "particle-bloom" to DEFAULT,
            "soft-glow" to DEFAULT,
            "liquid-logo" to DEFAULT,
            "pixel-build" to DEFAULT,
            "constellation" to DEFAULT,
        )

        private fun lookup(value: String?): RevealTemplateId? {
            val v = value?.trim().orEmpty()
            if (v.isEmpty()) return null
            return entries.firstOrNull { it.wireId.equals(v, ignoreCase = true) || it.name.equals(v, ignoreCase = true) }
                ?: LEGACY_ALIASES[v.lowercase()]
        }

        /** Unknown or missing template IDs fall back to [DEFAULT], never crash. */
        fun fromWireId(value: String?): RevealTemplateId = lookup(value) ?: DEFAULT

        /** True for current IDs, enum names and legacy aliases. */
        fun isKnown(value: String?): Boolean = lookup(value) != null
    }
}

/** Accent used by a person's Reveal (completion ring, contact mark, Connect). See [com.kipat.probe.ui.theme.RevealPalette]. */
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
    HIGH,
    /** Low-RAM or ≤ 4 cores: same choreography, no animated shadow. */
    LOW,
    /** System animations off: each template's reduced-motion equivalent, readable immediately. */
    REDUCED_MOTION,
}

/** The one geometric idea a template is built on. Drawn by [com.kipat.probe.reveal.effects.RevealEffects]. */
enum class EntryPrimitive {
    /** Minimal Fade: no primitive at all. */
    NONE,
    /** Contact Ripple: contact dot and two thin rings from the card's top centre (under the card). */
    RIPPLE,
    /** Precision Orbit: two symmetric arcs close into a ring around the avatar (over the card). */
    ORBIT,
    /** Line Assembly: drafting lines set out the card's edges (under the card). */
    LINES,
    /** Aperture: a six-blade flat iris opens on the avatar (over the card). */
    APERTURE,
    /** Spatial Card: the card's edge, visible only while tilted (inside the card's layer). */
    EDGE,
}

/** How the card surface itself arrives. */
enum class SurfaceEntry {
    /** Opacity + small rise. */
    FADE_RISE,
    /** Uncovered inside a circle growing from the contact point. */
    CIRCULAR_REVEAL,
    /** Opacity + 0.99 → 1 scale. */
    FADE_SCALE,
    /** Opacity only. */
    FADE,
    /** Opacity + 6° rotationX settling flat on a spring. */
    TILT,
}

enum class NameEntry {
    FADE,
    /** Left-to-right clip reveal. */
    WIPE,
}

/** Where a circular surface reveal grows from. */
enum class MaskOrigin {
    /** The card's top centre, where the contact ring lands (Contact Ripple). */
    CONTACT_POINT,
    /** The avatar's centre: the surface grows out of the identity anchor (Orbit, Aperture). */
    AVATAR,
}

/**
 * The surface is uncovered inside a growing circle, so it emerges from the motion instead of
 * fading in after it. [startRadiusBeyondAvatarDp] keeps the avatar (and the ring or iris around
 * it) visible before the surface grows.
 */
data class SurfaceMask(
    val origin: MaskOrigin,
    val startMs: Int,
    val durationMs: Int,
    val startRadiusBeyondAvatarDp: Float = 0f,
)

/** What each template becomes when motion is reduced. Every variant settles in 200 ms. */
enum class ReducedMotion(val description: String) {
    CROSSFADE("Crossfade"),
    CONTACT_ACKNOWLEDGE("Contact dot fades in and out once while the card crossfades"),
    STATIC_RING("Settled ring fades in with the card"),
    PERIMETER_FADE("All four lines fade out as the card fades in"),
    SCALE_FADE("Scale 0.98 to 1 with opacity"),
}

/**
 * A template's complete choreography: one primary motion ([primitive]), how the surface resolves,
 * how the name enters, and the timeline. Templates differ only here; the renderer is shared.
 */
data class RevealChoreography(
    val template: RevealTemplateId,
    val timeline: RevealTimeline,
    val primitive: EntryPrimitive,
    val surfaceEntry: SurfaceEntry,
    val nameEntry: NameEntry,
    val reducedMotion: ReducedMotion,
    /** True when this is the reduced-motion variant ([reducedMotion] is what plays). */
    val reduced: Boolean = false,
    /** Light-mode card shadow grows during the entrance (skipped on low-power devices). */
    val animatedShadow: Boolean = true,
    /** Set when [surfaceEntry] is [SurfaceEntry.CIRCULAR_REVEAL]. */
    val mask: SurfaceMask? = null,
) {
    companion object {
        fun forTemplate(template: RevealTemplateId, quality: RevealQuality = RevealQuality.HIGH): RevealChoreography {
            val full = when (template) {
                RevealTemplateId.CONTACT_RIPPLE -> RevealChoreography(
                    template, RevealTimeline.CONTACT_RIPPLE, EntryPrimitive.RIPPLE, SurfaceEntry.CIRCULAR_REVEAL, NameEntry.FADE, ReducedMotion.CONTACT_ACKNOWLEDGE,
                    mask = SurfaceMask(MaskOrigin.CONTACT_POINT, startMs = 40, durationMs = 720),
                )
                // The ring settles around the avatar, then the surface grows out of the ring.
                RevealTemplateId.PRECISION_ORBIT -> RevealChoreography(
                    template, RevealTimeline.PRECISION_ORBIT, EntryPrimitive.ORBIT, SurfaceEntry.CIRCULAR_REVEAL, NameEntry.WIPE, ReducedMotion.STATIC_RING,
                    mask = SurfaceMask(MaskOrigin.AVATAR, startMs = 560, durationMs = 440, startRadiusBeyondAvatarDp = 14f),
                )
                RevealTemplateId.LINE_ASSEMBLY -> RevealChoreography(template, RevealTimeline.LINE_ASSEMBLY, EntryPrimitive.LINES, SurfaceEntry.FADE, NameEntry.WIPE, ReducedMotion.PERIMETER_FADE)
                // Only the avatar disc exists under the iris; once it opens, the surface unfolds from it.
                RevealTemplateId.APERTURE -> RevealChoreography(
                    template, RevealTimeline.APERTURE, EntryPrimitive.APERTURE, SurfaceEntry.CIRCULAR_REVEAL, NameEntry.FADE, ReducedMotion.CROSSFADE,
                    mask = SurfaceMask(MaskOrigin.AVATAR, startMs = 520, durationMs = 380, startRadiusBeyondAvatarDp = 8f),
                )
                RevealTemplateId.SPATIAL_CARD -> RevealChoreography(template, RevealTimeline.SPATIAL_CARD, EntryPrimitive.EDGE, SurfaceEntry.TILT, NameEntry.FADE, ReducedMotion.SCALE_FADE)
                RevealTemplateId.MINIMAL_FADE -> RevealChoreography(template, RevealTimeline.MINIMAL_FADE, EntryPrimitive.NONE, SurfaceEntry.FADE_RISE, NameEntry.FADE, ReducedMotion.CROSSFADE)
            }
            return when (quality) {
                RevealQuality.HIGH -> full
                RevealQuality.LOW -> full.copy(animatedShadow = false)
                RevealQuality.REDUCED_MOTION -> full.reducedVariant()
            }
        }

        private fun RevealChoreography.reducedVariant(): RevealChoreography {
            // Primitives that survive reduced motion do so statically; nothing moves or rotates.
            val primitive = when (reducedMotion) {
                ReducedMotion.CONTACT_ACKNOWLEDGE -> EntryPrimitive.RIPPLE
                ReducedMotion.STATIC_RING -> EntryPrimitive.ORBIT
                ReducedMotion.PERIMETER_FADE -> EntryPrimitive.LINES
                ReducedMotion.CROSSFADE, ReducedMotion.SCALE_FADE -> EntryPrimitive.NONE
            }
            val surface = if (reducedMotion == ReducedMotion.SCALE_FADE) SurfaceEntry.FADE_SCALE else SurfaceEntry.FADE
            return copy(
                timeline = RevealTimeline.REDUCED,
                primitive = primitive,
                surfaceEntry = surface,
                nameEntry = NameEntry.FADE,
                reduced = true,
                animatedShadow = false,
                mask = null,
            )
        }
    }
}
