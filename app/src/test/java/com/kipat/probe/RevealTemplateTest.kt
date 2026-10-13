package com.kipat.probe

import com.kipat.probe.reveal.EntryPrimitive
import com.kipat.probe.reveal.KipatEasing
import com.kipat.probe.reveal.NameEntry
import com.kipat.probe.reveal.ReducedMotion
import com.kipat.probe.reveal.RevealChoreography
import com.kipat.probe.reveal.RevealEngine
import com.kipat.probe.reveal.RevealQuality
import com.kipat.probe.reveal.RevealStyleDraft
import com.kipat.probe.reveal.RevealTemplateId
import com.kipat.probe.reveal.RevealTheme
import com.kipat.probe.reveal.RevealTimeline
import com.kipat.probe.reveal.SurfaceEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RevealTemplateTest {

    // ---- Resolution ----

    @Test
    fun catalogIsTheSixTemplateFamilyWithShortUniqueWireIds() {
        assertEquals(
            listOf("Contact Ripple", "Precision Orbit", "Line Assembly", "Aperture", "Spatial Card", "Minimal Fade"),
            RevealTemplateId.entries.map { it.label },
        )
        assertEquals(6, RevealTemplateId.entries.map { it.wireId }.toSet().size)
        // Wire IDs go on NTAG213 tags next to the card URI; keep them short.
        assertTrue(RevealTemplateId.entries.all { it.wireId.length <= 12 })
    }

    @Test
    fun resolvesWireIdsCaseInsensitivelyAndByEnumName() {
        assertEquals(RevealTemplateId.PRECISION_ORBIT, RevealTemplateId.fromWireId("orbit"))
        assertEquals(RevealTemplateId.PRECISION_ORBIT, RevealTemplateId.fromWireId("ORBIT"))
        assertEquals(RevealTemplateId.LINE_ASSEMBLY, RevealTemplateId.fromWireId("LINE_ASSEMBLY"))
        assertEquals(RevealTemplateId.SPATIAL_CARD, RevealTemplateId.fromWireId(" spatial "))
        RevealTemplateId.entries.forEach { assertEquals(it, RevealTemplateId.fromWireId(it.wireId)) }
    }

    @Test
    fun legacyIdsOnWrittenCardsMapToTheClosestTemplate() {
        assertEquals(RevealTemplateId.DEFAULT, RevealTemplateId.fromWireId("drip"))
        assertEquals(RevealTemplateId.LINE_ASSEMBLY, RevealTemplateId.fromWireId("line-trace"))
        assertEquals(RevealTemplateId.DEFAULT, RevealTemplateId.fromWireId("particle-bloom"))
        assertEquals(RevealTemplateId.DEFAULT, RevealTemplateId.fromWireId("CONSTELLATION"))
        assertTrue(RevealTemplateId.isKnown("soft-glow"))
    }

    @Test
    fun invalidOrMissingTemplateFallsBackToDefault() {
        assertEquals(RevealTemplateId.DEFAULT, RevealTemplateId.fromWireId(null))
        assertEquals(RevealTemplateId.DEFAULT, RevealTemplateId.fromWireId(""))
        assertEquals(RevealTemplateId.DEFAULT, RevealTemplateId.fromWireId("hologram-3000"))
        assertFalse(RevealTemplateId.isKnown("hologram-3000"))
        assertFalse(RevealTemplateId.isKnown(null))
    }

    @Test
    fun defaultIsNotTheMostComplexTemplateAndFallbackIsTheSimplest() {
        assertEquals(RevealTemplateId.CONTACT_RIPPLE, RevealTemplateId.DEFAULT)
        assertEquals(RevealTemplateId.MINIMAL_FADE, RevealTemplateId.LOW_POWER)
        assertEquals(EntryPrimitive.NONE, RevealChoreography.forTemplate(RevealTemplateId.LOW_POWER).primitive)
    }

    // ---- Choreography ----

    @Test
    fun eachTemplateHasExactlyOneDistinctPrimaryMotion() {
        val full = RevealTemplateId.entries.map { RevealChoreography.forTemplate(it) }
        assertEquals("one primitive per template, none shared", 6, full.map { it.primitive }.toSet().size)
        full.forEach { assertEquals(it.template, it.template) ; assertFalse(it.reduced) }
        assertEquals(SurfaceEntry.CIRCULAR_REVEAL, RevealChoreography.forTemplate(RevealTemplateId.CONTACT_RIPPLE).surfaceEntry)
        assertEquals(SurfaceEntry.TILT, RevealChoreography.forTemplate(RevealTemplateId.SPATIAL_CARD).surfaceEntry)
        assertEquals(NameEntry.WIPE, RevealChoreography.forTemplate(RevealTemplateId.LINE_ASSEMBLY).nameEntry)
    }

    @Test
    fun timelinesMatchTheTemplateDocs() {
        // design/reveal-templates/*.md — settled times.
        val settled = RevealTemplateId.entries.associateWith { RevealChoreography.forTemplate(it).timeline.totalDuration }
        assertEquals(1220, settled[RevealTemplateId.CONTACT_RIPPLE])
        assertEquals(1400, settled[RevealTemplateId.PRECISION_ORBIT])
        assertEquals(1400, settled[RevealTemplateId.LINE_ASSEMBLY])
        assertEquals(1240, settled[RevealTemplateId.APERTURE])
        assertEquals(1200, settled[RevealTemplateId.SPATIAL_CARD])
        assertEquals(620, settled[RevealTemplateId.MINIMAL_FADE])
        // Every template settles faster than the retired Signature (1550 ms).
        assertTrue(settled.values.all { it < 1550 })
    }

    @Test
    fun identityIsReadableBeforeTrustAndTrustBeforeActions() {
        RevealTemplateId.entries.forEach { t ->
            val tl = RevealChoreography.forTemplate(t).timeline
            assertTrue("$t name ≤ 850 ms", tl.nameStart <= 850)
            assertTrue("$t order", tl.nameStart <= tl.roleStart && tl.roleStart <= tl.verifyStart && tl.verifyStart <= tl.ctaStart)
        }
    }

    @Test
    fun lowQualityKeepsTheChoreographyButDropsTheAnimatedShadow() {
        RevealTemplateId.entries.forEach { t ->
            val high = RevealChoreography.forTemplate(t, RevealQuality.HIGH)
            val low = RevealChoreography.forTemplate(t, RevealQuality.LOW)
            assertEquals(high.copy(animatedShadow = false), low)
        }
    }

    @Test
    fun reducedMotionMapsEachTemplateToItsDocumentedEquivalent() {
        val expected = mapOf(
            RevealTemplateId.CONTACT_RIPPLE to ReducedMotion.CONTACT_ACKNOWLEDGE,
            RevealTemplateId.PRECISION_ORBIT to ReducedMotion.STATIC_RING,
            RevealTemplateId.LINE_ASSEMBLY to ReducedMotion.PERIMETER_FADE,
            RevealTemplateId.APERTURE to ReducedMotion.CROSSFADE,
            RevealTemplateId.SPATIAL_CARD to ReducedMotion.SCALE_FADE,
            RevealTemplateId.MINIMAL_FADE to ReducedMotion.CROSSFADE,
        )
        expected.forEach { (t, rm) ->
            val c = RevealChoreography.forTemplate(t, RevealQuality.REDUCED_MOTION)
            assertEquals(rm, c.reducedMotion)
            assertTrue(c.reduced)
            assertEquals(RevealTimeline.REDUCED, c.timeline)
            assertEquals(NameEntry.FADE, c.nameEntry)
            assertNotEquals("nothing tilts under reduced motion", SurfaceEntry.TILT, c.surfaceEntry)
            assertNotEquals("nothing masks under reduced motion", SurfaceEntry.CIRCULAR_REVEAL, c.surfaceEntry)
        }
    }

    @Test
    fun reducedMotionIsReadableImmediately() {
        val tl = RevealTimeline.REDUCED
        val first = RevealEngine.frameAt(1, tl)
        assertTrue(first.name > 0f && first.verify > 0f)
        assertTrue(RevealEngine.frameAt(tl.totalDuration.toLong(), tl).isComplete)
        assertTrue(tl.totalDuration <= 250)
    }

    @Test
    fun framesProgressMonotonicallyAndSettleExactly() {
        RevealTemplateId.entries.forEach { t ->
            val tl = RevealChoreography.forTemplate(t).timeline
            assertEquals(0f, RevealEngine.frameAt(0, tl).name, 0f)
            assertFalse(RevealEngine.frameAt(tl.totalDuration - 50L, tl).isComplete)
            val end = RevealEngine.frameAt(tl.totalDuration.toLong(), tl)
            assertTrue(end.isComplete)
            assertEquals(1f, end.surface, 0f)
            var last = -1f
            for (ms in 0..tl.totalDuration step 10) {
                val f = RevealEngine.frameAt(ms.toLong(), tl)
                assertTrue("$t surface never goes back", f.surface >= last - 1e-5f)
                assertTrue(f.surface in 0f..1f && f.cta in 0f..1f)
                last = f.surface
            }
        }
    }

    // ---- Motion system ----

    @Test
    fun easingsHitEndpointsAndNeverOvershoot() {
        val curves = listOf<(Float) -> Float>(KipatEasing.Fast::ease, KipatEasing.Standard::ease, KipatEasing.Exit::ease, KipatEasing::emphasized)
        curves.forEach { ease ->
            assertEquals(0f, ease(0f), 1e-4f)
            assertEquals(1f, ease(1f), 1e-4f)
            var last = 0f
            for (i in 0..100) {
                val v = ease(i / 100f)
                assertTrue("no overshoot", v in -1e-4f..1.0001f)
                assertTrue("monotonic", v >= last - 1e-4f)
                last = v
            }
        }
        // M3 emphasized passes through its join point (0.166666, 0.4).
        assertEquals(0.4f, KipatEasing.emphasized(0.166666f), 1e-3f)
        assertEquals(0f, RevealEngine.linear(-10, 0, 100), 0f)
        assertEquals(1f, RevealEngine.linear(500, 0, 0), 0f)
    }

    @Test
    fun settleSpringSettlesOnceWithoutVisibleBounce() {
        var peak = 0f
        for (ms in 0..1500 step 5) peak = maxOf(peak, KipatEasing.spring(ms.toLong()))
        assertTrue("overshoot < 0.5%", peak < 1.005f)
        assertEquals(1f, KipatEasing.spring(700), 0.005f)
        assertEquals(0f, KipatEasing.spring(0), 0f)
    }

    // ---- Selection ----

    @Test
    fun selectingATemplateDoesNotApplyIt() {
        val draft = RevealStyleDraft(RevealTemplateId.CONTACT_RIPPLE, RevealTheme.NIGHT)
        assertFalse(draft.hasChanges)
        val picked = draft.select(RevealTemplateId.APERTURE).select(RevealTheme.MONO)
        assertTrue(picked.hasChanges)
        assertEquals(RevealTemplateId.CONTACT_RIPPLE, picked.appliedTemplate)
        assertEquals(RevealTheme.NIGHT, picked.appliedTheme)
        val applied = picked.apply()
        assertEquals(RevealTemplateId.APERTURE, applied.appliedTemplate)
        assertEquals(RevealTheme.MONO, applied.appliedTheme)
        assertFalse(applied.hasChanges)
        // Picking the applied one again is not a change.
        assertFalse(applied.select(RevealTemplateId.APERTURE).hasChanges)
    }
}
