package com.kipat.probe

import com.kipat.probe.reveal.IdentityLayout
import com.kipat.probe.reveal.MaskOrigin
import com.kipat.probe.reveal.RevealChoreography
import com.kipat.probe.reveal.RevealPayload
import com.kipat.probe.reveal.RevealPayloadCodec
import com.kipat.probe.reveal.RevealPresentation
import com.kipat.probe.reveal.RevealQuality
import com.kipat.probe.reveal.RevealSource
import com.kipat.probe.reveal.RevealTemplateId
import com.kipat.probe.reveal.RevealTheme
import com.kipat.probe.reveal.SurfaceEntry
import com.kipat.probe.reveal.SurfaceMetrics
import com.kipat.probe.reveal.SurfaceTreatment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The identity surface's state logic (design/reveal-surface/). Visual taste is judged from renders. */
class RevealPresentationTest {

    @Test
    fun surfacesArePairedPerTemplateAndTheDefaultUsesTheDefaultSurface() {
        val p = RevealTemplateId.entries.associateWith { RevealPresentation.of(it, RevealTheme.NIGHT) }
        assertEquals(RevealPresentation.DEFAULT_LAYOUT, p.getValue(RevealTemplateId.DEFAULT).layout)
        assertEquals(IdentityLayout.COMPACT to SurfaceTreatment.TONAL, p.getValue(RevealTemplateId.CONTACT_RIPPLE).let { it.layout to it.treatment })
        assertEquals(IdentityLayout.PROFILE_HEADER to SurfaceTreatment.PAPER, p.getValue(RevealTemplateId.PRECISION_ORBIT).let { it.layout to it.treatment })
        assertEquals(IdentityLayout.PROFILE_HEADER to SurfaceTreatment.STRUCTURAL, p.getValue(RevealTemplateId.LINE_ASSEMBLY).let { it.layout to it.treatment })
        assertEquals(IdentityLayout.EDITORIAL to SurfaceTreatment.PAPER, p.getValue(RevealTemplateId.APERTURE).let { it.layout to it.treatment })
        assertEquals(IdentityLayout.COMPACT to SurfaceTreatment.FLOATING, p.getValue(RevealTemplateId.SPATIAL_CARD).let { it.layout to it.treatment })
        assertEquals(IdentityLayout.COMPACT to SurfaceTreatment.PAPER, p.getValue(RevealTemplateId.MINIMAL_FADE).let { it.layout to it.treatment })
        // The accent never changes the surface.
        RevealTheme.entries.forEach { t ->
            assertEquals(p.getValue(RevealTemplateId.APERTURE).copy(theme = t), RevealPresentation.of(RevealTemplateId.APERTURE, t))
        }
    }

    @Test
    fun layoutsStayWithinTheSurfaceSpec() {
        IdentityLayout.entries.forEach {
            assertTrue("avatar 56–72 dp", it.avatarDp in 56f..72f)
            assertTrue("name 20–24 sp", it.nameSp in 20f..24f)
        }
        assertEquals(24f, SurfaceMetrics.paddingDp(371f))
        assertEquals(20f, SurfaceMetrics.paddingDp(280f)) // 320 dp phone − 40 dp margins
        assertTrue(SurfaceMetrics.MAX_WIDTH_DP in 340f..380f)
        assertEquals(24f + 36f, SurfaceMetrics.avatarCenterDp(IdentityLayout.EDITORIAL, 371f))
        assertEquals(20f + 28f, SurfaceMetrics.avatarCenterDp(IdentityLayout.COMPACT, 280f))
    }

    @Test
    fun surfacesEmergeFromTheMotionInsteadOfWaitingEmpty() {
        RevealTemplateId.entries.forEach { t ->
            val c = RevealChoreography.forTemplate(t)
            assertEquals("mask iff circular reveal", c.surfaceEntry == SurfaceEntry.CIRCULAR_REVEAL, c.mask != null)
            // When does the surface beyond the avatar start to show? For anchor masks, when the mask grows.
            val surfaceShows = c.mask?.takeIf { it.origin == MaskOrigin.AVATAR }?.startMs ?: c.timeline.surfaceStart
            val gap = c.timeline.nameStart - surfaceShows
            assertTrue("$t: name follows its surface within 200 ms (was $gap)", gap <= 200)
            val actionsGap = c.timeline.ctaStart - surfaceShows
            assertTrue("$t: actions follow their surface within 400 ms (was $actionsGap)", actionsGap <= 400)
        }
        assertEquals(MaskOrigin.AVATAR, RevealChoreography.forTemplate(RevealTemplateId.PRECISION_ORBIT).mask!!.origin)
        assertEquals(MaskOrigin.CONTACT_POINT, RevealChoreography.forTemplate(RevealTemplateId.CONTACT_RIPPLE).mask!!.origin)
    }

    @Test
    fun reducedMotionNeverMasks() {
        RevealTemplateId.entries.forEach { assertNull(RevealChoreography.forTemplate(it, RevealQuality.REDUCED_MOTION).mask) }
        assertNotNull(RevealChoreography.forTemplate(RevealTemplateId.APERTURE, RevealQuality.LOW).mask)
    }

    @Test
    fun organisationIdentityRoundTripsAndAnythingElseIsAPerson() {
        val org = RevealPayload.unresolved("KPT-ORG-1", RevealSource.NFC_TAG).copy(displayName = "Globe Labs", isOrganization = true, connectable = true)
        val decoded = RevealPayloadCodec.decode { RevealPayloadCodec.encode(org)[it] }
        assertTrue(decoded.payload.isOrganization)
        assertTrue(decoded.isClean)
        val person = RevealPayloadCodec.decode { RevealPayloadCodec.encode(org.copy(isOrganization = false))[it] }
        assertFalse(person.payload.isOrganization)
        val junk = RevealPayloadCodec.encode(org).toMutableMap().apply { put(RevealPayloadCodec.KEY_KIND, "warp") }
        assertFalse(RevealPayloadCodec.decode { junk[it] }.payload.isOrganization)
    }
}
