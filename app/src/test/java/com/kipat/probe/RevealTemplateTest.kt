package com.kipat.probe

import com.kipat.probe.reveal.ParticleMotion
import com.kipat.probe.reveal.RevealConfig
import com.kipat.probe.reveal.RevealEngine
import com.kipat.probe.reveal.RevealQuality
import com.kipat.probe.reveal.RevealTemplateId
import com.kipat.probe.reveal.RevealTimeline
import com.kipat.probe.reveal.SurfaceEntrance
import com.kipat.probe.reveal.TemplateSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RevealTemplateTest {

    @Test
    fun catalogHasTenTemplatesWithUniqueWireIds() {
        assertEquals(10, RevealTemplateId.entries.size)
        assertEquals(10, RevealTemplateId.entries.map { it.wireId }.toSet().size)
    }

    @Test
    fun signatureBloomAndMinimalFadeAreFullyImplemented() {
        val full = RevealTemplateId.entries.filter { it.support == TemplateSupport.FULL }.toSet()
        assertEquals(setOf(RevealTemplateId.SIGNATURE_DRIP, RevealTemplateId.PARTICLE_BLOOM, RevealTemplateId.MINIMAL_FADE), full)
    }

    @Test
    fun resolvesWireIdsCaseInsensitivelyAndByEnumName() {
        assertEquals(RevealTemplateId.PARTICLE_BLOOM, RevealTemplateId.fromWireId("particle-bloom"))
        assertEquals(RevealTemplateId.PARTICLE_BLOOM, RevealTemplateId.fromWireId("PARTICLE-BLOOM"))
        assertEquals(RevealTemplateId.MINIMAL_FADE, RevealTemplateId.fromWireId("MINIMAL_FADE"))
        assertEquals(RevealTemplateId.CONSTELLATION, RevealTemplateId.fromWireId(" constellation "))
    }

    @Test
    fun unknownOrMissingTemplateFallsBackToSignature() {
        assertEquals(RevealTemplateId.SIGNATURE_DRIP, RevealTemplateId.fromWireId(null))
        assertEquals(RevealTemplateId.SIGNATURE_DRIP, RevealTemplateId.fromWireId(""))
        assertEquals(RevealTemplateId.SIGNATURE_DRIP, RevealTemplateId.fromWireId("hologram-3000"))
        assertFalse(RevealTemplateId.isKnown("hologram-3000"))
    }

    @Test
    fun everyTemplateProducesAConfig() {
        RevealTemplateId.entries.forEach { t ->
            RevealQuality.entries.forEach { q ->
                val c = RevealConfig.forTemplate(t, q)
                assertEquals(t, c.template)
                assertTrue(c.particleCount <= RevealConfig.MAX_PARTICLES)
            }
        }
    }

    @Test
    fun lowQualityCapsParticlesAndRings() {
        val c = RevealConfig.forTemplate(RevealTemplateId.PARTICLE_BLOOM, RevealQuality.LOW)
        assertTrue(c.particleCount <= RevealConfig.LOW_QUALITY_PARTICLES)
        assertTrue(c.rippleRings <= 1)
    }

    @Test
    fun reducedMotionUsesFadeOnlyWithNoParticles() {
        RevealTemplateId.entries.forEach { t ->
            val c = RevealConfig.forTemplate(t, RevealQuality.REDUCED_MOTION)
            assertEquals(0, c.particleCount)
            assertEquals(0, c.rippleRings)
            assertEquals(ParticleMotion.NONE, c.particleMotion)
            assertEquals(SurfaceEntrance.FADE, c.surfaceEntrance)
            assertEquals(RevealTimeline.REDUCED, c.timeline)
            assertEquals(0f, c.perimeterTrace, 0f)
        }
    }

    @Test
    fun signatureTracesTheCardOutline() {
        assertTrue(RevealConfig.forTemplate(RevealTemplateId.SIGNATURE_DRIP).perimeterTrace > 0f)
        assertTrue(RevealConfig.forTemplate(RevealTemplateId.LINE_TRACE).perimeterTrace > 0f)
        assertEquals(0f, RevealConfig.forTemplate(RevealTemplateId.MINIMAL_FADE).perimeterTrace, 0f)
    }

    @Test
    fun signatureTimelineMatchesV2Targets() {
        // design/higgsfield/critique-v2.md: identity readable < 1 s, settled ≈ 1.55 s.
        val t = RevealTimeline.SIGNATURE
        assertEquals(700, t.avatarStart)
        assertEquals(850, t.nameStart)
        assertEquals(950, t.roleStart)
        assertEquals(1100, t.verifyStart)
        assertEquals(1250, t.ctaStart)
        assertEquals(1550, t.totalDuration)
    }

    @Test
    fun signatureHasNoDecorativeEffects() {
        val c = RevealConfig.forTemplate(RevealTemplateId.SIGNATURE_DRIP)
        assertEquals(0, c.particleCount)
        assertEquals(ParticleMotion.NONE, c.particleMotion)
        assertFalse(c.drip)
        assertEquals(0f, c.glowStrength, 0f)
        assertEquals(1, c.rippleRings)
        assertTrue("contact ring stays small", c.rippleScale <= 0.2f)
        assertTrue(c.perimeterTrace > 0f)
    }

    @Test
    fun frameProgressesMonotonicallyAndSettles() {
        val t = RevealTimeline.SIGNATURE
        assertEquals(0f, RevealEngine.frameAt(0, t).surface, 0f)
        assertEquals(0f, RevealEngine.frameAt(849, t).name, 0f)
        assertTrue(RevealEngine.frameAt(950, t).name > 0f)
        assertFalse(RevealEngine.frameAt(1500, t).isComplete)
        val end = RevealEngine.frameAt(t.totalDuration.toLong(), t)
        assertTrue(end.isComplete)
        assertEquals(1f, end.surface, 0f)
        assertEquals(1f, end.verify, 0f)
        var last = -1f
        for (ms in 0..t.totalDuration step 50) {
            val s = RevealEngine.frameAt(ms.toLong(), t).surface
            assertTrue(s >= last)
            last = s
        }
    }

    @Test
    fun reducedTimelineIsReadableAlmostImmediately() {
        val f = RevealEngine.frameAt(RevealTimeline.REDUCED.totalDuration.toLong(), RevealTimeline.REDUCED)
        assertTrue(f.isComplete)
        assertTrue(RevealTimeline.REDUCED.totalDuration <= 250)
    }

    @Test
    fun easingsHitEndpoints() {
        assertEquals(0f, RevealEngine.easeOutCubic(0f), 1e-6f)
        assertEquals(1f, RevealEngine.easeOutCubic(1f), 1e-6f)
        assertEquals(1f, RevealEngine.easeInOutCubic(1f), 1e-6f)
        assertEquals(1f, RevealEngine.easeOutBack(1f), 1e-6f)
        assertEquals(0f, RevealEngine.linear(-10, 0, 100), 0f)
        assertEquals(1f, RevealEngine.linear(500, 0, 0), 0f)
    }
}
