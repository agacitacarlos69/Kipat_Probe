package com.kipat.probe.reveal

/**
 * How the identity header is composed (design/reveal-surface/surface-directions.md). The data and
 * reading order never change: identity, context, trust, actions.
 */
enum class IdentityLayout(val avatarDp: Float, val nameSp: Float, val nameLineSp: Float) {
    /** A: avatar above a full-width name. The default; degrades best on small screens. */
    COMPACT(56f, 24f, 30f),
    /** B: avatar beside the name; the fastest "who" on a normal phone. */
    PROFILE_HEADER(56f, 22f, 28f),
    /** C: a large anchor with more air; for the template whose motion is about the anchor. */
    EDITORIAL(72f, 24f, 30f),
}

/** The surface's material. Opaque in every case: the Reveal sits over arbitrary apps. */
enum class SurfaceTreatment {
    /** Neutral, thin border, almost no shadow. */
    PAPER,
    /** Softer, slightly warmer material, soft shadow. */
    TONAL,
    /** Crisp drafted edge: 12 dp radius, ≥ 3:1 outline, full-width rule. */
    STRUCTURAL,
    /** Slightly stronger depth; raised graphite in dark. */
    FLOATING,
}

/**
 * What a Reveal looks like: the motion template, plus the surface it settles into and the person's
 * accent. Surfaces are curated per template ([of]) so combinations can't proliferate.
 */
data class RevealPresentation(
    val template: RevealTemplateId,
    val layout: IdentityLayout,
    val treatment: SurfaceTreatment,
    val theme: RevealTheme,
) {
    companion object {
        val DEFAULT_LAYOUT = IdentityLayout.COMPACT

        fun of(template: RevealTemplateId, theme: RevealTheme): RevealPresentation {
            val (layout, treatment) = when (template) {
                RevealTemplateId.CONTACT_RIPPLE -> IdentityLayout.COMPACT to SurfaceTreatment.TONAL
                RevealTemplateId.PRECISION_ORBIT -> IdentityLayout.PROFILE_HEADER to SurfaceTreatment.PAPER
                RevealTemplateId.LINE_ASSEMBLY -> IdentityLayout.PROFILE_HEADER to SurfaceTreatment.STRUCTURAL
                RevealTemplateId.APERTURE -> IdentityLayout.EDITORIAL to SurfaceTreatment.PAPER
                RevealTemplateId.SPATIAL_CARD -> IdentityLayout.COMPACT to SurfaceTreatment.FLOATING
                RevealTemplateId.MINIMAL_FADE -> IdentityLayout.COMPACT to SurfaceTreatment.PAPER
            }
            return RevealPresentation(template, layout, treatment, theme)
        }
    }
}

/** Surface geometry shared by the Compose surface and the Reveal primitives that anchor to it. */
object SurfaceMetrics {
    /** Total horizontal margin is 2 × this; the surface never stretches past [MAX_WIDTH_DP]. */
    const val MARGIN_DP = 20f
    const val MAX_WIDTH_DP = 380f
    /** Below this surface width the padding steps down (320 dp phones). */
    const val COMPACT_WIDTH_DP = 340f

    fun paddingDp(surfaceWidthDp: Float): Float = if (surfaceWidthDp < COMPACT_WIDTH_DP) 20f else 24f

    /** Avatar centre from the surface's top-start corner, in dp. The avatar is top-aligned in every layout. */
    fun avatarCenterDp(layout: IdentityLayout, surfaceWidthDp: Float): Float =
        paddingDp(surfaceWidthDp) + layout.avatarDp / 2f
}
