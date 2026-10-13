package com.kipat.probe

import com.kipat.probe.data.RevealResolver
import com.kipat.probe.data.SampleData
import com.kipat.probe.data.toRevealPayload
import com.kipat.probe.model.AuthenticityState
import com.kipat.probe.nfc.NfcIntentParser
import com.kipat.probe.reveal.RevealPayload
import com.kipat.probe.reveal.RevealPayloadCodec
import com.kipat.probe.reveal.RevealSource
import com.kipat.probe.reveal.RevealTemplateId
import com.kipat.probe.reveal.RevealTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RevealPayloadTest {

    private val now = 1_700_000_000_000L
    private val connections = SampleData.connections(now)

    @Test
    fun encodeDecodeRoundTrip() {
        val p = SampleData.identity.toRevealPayload(RevealSource.PREVIEW, isDemo = true, authenticity = AuthenticityState.AUTHENTIC)
        val map = RevealPayloadCodec.encode(p)
        val decoded = RevealPayloadCodec.decode { map[it] }
        assertTrue(decoded.isClean)
        assertEquals(p, decoded.payload)
    }

    @Test
    fun missingNameFallsBackSafely() {
        val decoded = RevealPayloadCodec.decode { null }
        assertEquals(RevealPayload.FALLBACK_NAME, decoded.payload.displayName)
        assertEquals(AuthenticityState.UNKNOWN, decoded.payload.authenticity)
        assertEquals(RevealTemplateId.DEFAULT, decoded.payload.template)
        assertFalse(decoded.isClean)
    }

    @Test
    fun malformedValuesAreReplacedAndReported() {
        val bad = mapOf(
            RevealPayloadCodec.KEY_NAME to "  \u0000Eve\n  ",
            RevealPayloadCodec.KEY_TEMPLATE to "warp-drive",
            RevealPayloadCodec.KEY_AUTH to "totally-legit",
            RevealPayloadCodec.KEY_CARD_ID to "../../etc",
            RevealPayloadCodec.KEY_AVATAR to "file:///sdcard/secret.png",
            RevealPayloadCodec.KEY_THEME to "neon",
            RevealPayloadCodec.KEY_DEMO to "maybe",
        )
        val d = RevealPayloadCodec.decode { bad[it] }
        assertEquals("Eve", d.payload.displayName)
        assertEquals(RevealTemplateId.DEFAULT, d.payload.template)
        assertEquals(AuthenticityState.UNKNOWN, d.payload.authenticity)
        assertNull(d.payload.cardId)
        assertNull(d.payload.avatarUri)
        assertEquals(RevealTheme.NIGHT, d.payload.theme)
        assertFalse(d.payload.isDemo)
        assertEquals(4, d.issues.size)
    }

    @Test
    fun longTextIsClamped() {
        val d = RevealPayloadCodec.decode { if (it == RevealPayloadCodec.KEY_NAME) "x".repeat(500) else null }
        assertTrue(d.payload.displayName.length <= RevealPayload.MAX_NAME)
    }

    @Test
    fun ownCardResolvesToOwnIdentityAsDemo() {
        val t = NfcIntentParser.parse("https://kipat.app/c/${SampleData.DEMO_CARD_ID}")!!
        val p = RevealResolver.resolve(t, SampleData.identity, ownCardIsDemo = true, connections = connections)
        assertEquals("Vash Puno", p.displayName)
        assertEquals(RevealSource.NFC_TAG, p.source)
        assertTrue(p.isDemo)
    }

    @Test
    fun knownConnectionResolvesLocally() {
        val t = NfcIntentParser.parse("https://kipat.app/c/KPT-DEMO-0102")!!
        val p = RevealResolver.resolve(t, SampleData.identity, true, connections)
        assertEquals("Alyssa Ramos", p.displayName)
        assertEquals(RevealTemplateId.PRECISION_ORBIT, p.template)
    }

    @Test
    fun tagHintsAreAlwaysUnverified() {
        val t = NfcIntentParser.parse("https://kipat.app/c/KPT-NEW-9?n=Sam%20Reyes&t=minimal-fade")!!
        val p = RevealResolver.resolve(t, SampleData.identity, true, connections)
        assertEquals("Sam Reyes", p.displayName)
        assertEquals(AuthenticityState.UNVERIFIED, p.authenticity)
        assertEquals(RevealTemplateId.MINIMAL_FADE, p.template)
        assertFalse(p.isDemo)
    }

    @Test
    fun unknownCardWithoutHintsShowsUnresolvedShell() {
        val t = NfcIntentParser.parse("kipatprobe://tap/TEST123")!!
        val p = RevealResolver.resolve(t, SampleData.identity, true, connections)
        assertEquals(RevealPayload.FALLBACK_NAME, p.displayName)
        assertEquals("TEST123", p.cardId)
        assertEquals(AuthenticityState.UNKNOWN, p.authenticity)
        assertEquals(RevealSource.DEEP_LINK, p.source)
    }

    @Test
    fun debugAuthenticityOverrideIsMarkedDemo() {
        val t = NfcIntentParser.parse("kipatprobe://tap/TEST?n=QA&a=authentic")!!
        val p = RevealResolver.resolve(t, SampleData.identity, true, connections)
        assertEquals(AuthenticityState.AUTHENTIC, p.authenticity)
        assertTrue("debug-forced verification must be labelled demo", p.isDemo)
    }

    @Test
    fun connectOnlyOfferedForRealTaps() {
        fun named(source: RevealSource) = RevealPayload.unresolved("X", source).copy(displayName = "Sam Reyes", connectable = true)
        assertTrue(named(RevealSource.NFC_TAG).canConnect)
        assertTrue(named(RevealSource.DEEP_LINK).canConnect)
        assertFalse(named(RevealSource.PREVIEW).canConnect)
        assertFalse(named(RevealSource.REPLAY).canConnect)
        // An unresolved card can't become a connection.
        assertFalse(RevealPayload.unresolved("X", RevealSource.NFC_TAG).canConnect)
        assertEquals("Done", RevealPayload.unresolved("X", RevealSource.NFC_TAG).primaryCta)
    }
}
