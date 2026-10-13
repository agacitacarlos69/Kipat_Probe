package com.kipat.probe

import com.kipat.probe.model.AuthenticityState
import com.kipat.probe.nfc.KipatTagUri
import com.kipat.probe.nfc.NfcIntentParser
import com.kipat.probe.nfc.TapChannel
import com.kipat.probe.reveal.RevealTemplateId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NfcIntentParserTest {

    @Test
    fun parsesDebugDeepLink() {
        val t = NfcIntentParser.parse("kipatprobe://tap/TEST123")!!
        assertEquals(TapChannel.DEBUG_DEEP_LINK, t.channel)
        assertEquals("TEST123", t.cardId)
        assertTrue(!t.hints.hasIdentity)
    }

    @Test
    fun debugLinkCarriesHintsAndAuthenticityOverride() {
        val t = NfcIntentParser.parse("kipatprobe://tap/KPT-1?n=Sam%20Reyes&s=Designer&t=particle-bloom&a=revoked")!!
        assertEquals("Sam Reyes", t.hints.name)
        assertEquals("Designer", t.hints.subtitle)
        assertEquals(RevealTemplateId.PARTICLE_BLOOM, t.hints.template)
        assertEquals(AuthenticityState.REVOKED, t.hints.debugAuthenticity)
    }

    @Test
    fun parsesCardUriAndAliases() {
        listOf("https://kipat.app/c/KPT-DEMO-0001", "https://kipat.app/tap/KPT-DEMO-0001", "https://www.kipat.app/p/KPT-DEMO-0001").forEach {
            val t = NfcIntentParser.parse(it)!!
            assertEquals(TapChannel.NFC_TAG, t.channel)
            assertEquals("KPT-DEMO-0001", t.cardId)
        }
    }

    @Test
    fun cardUriNeverHonoursAuthenticityOverride() {
        val t = NfcIntentParser.parse("https://kipat.app/c/KPT-1?n=Mallory&a=authentic")!!
        assertEquals("Mallory", t.hints.name)
        assertNull(t.hints.debugAuthenticity)
    }

    @Test
    fun legacyProbeTagIsRecognised() {
        val t = NfcIntentParser.parse("https://probe.kipat.com")!!
        assertEquals(TapChannel.LEGACY_PROBE_TAG, t.channel)
        assertEquals(NfcIntentParser.LEGACY_PROBE_CARD_ID, t.cardId)
    }

    @Test
    fun rejectsForeignOrMalformedUris() {
        assertNull(NfcIntentParser.parse(null))
        assertNull(NfcIntentParser.parse(""))
        assertNull(NfcIntentParser.parse("https://evil.example/c/KPT-1"))
        assertNull(NfcIntentParser.parse("http://kipat.app/c/KPT-1")) // cards are https only
        assertNull(NfcIntentParser.parse("not a uri at all %%%"))
        assertNull(NfcIntentParser.parse("kipatprobe://other/TEST"))
    }

    @Test
    fun invalidCardIdBecomesNullButStillParses() {
        val t = NfcIntentParser.parse("https://kipat.app/c/<script>")
        // "<" is not even a legal URI character, so this is rejected outright.
        assertNull(t)
        val noId = NfcIntentParser.parse("https://kipat.app/")!!
        assertNull(noId.cardId)
        val tooLong = NfcIntentParser.parse("kipatprobe://tap/" + "A".repeat(80))!!
        assertNull(tooLong.cardId)
    }

    @Test
    fun hintsAreSanitized() {
        val t = NfcIntentParser.parse("kipatprobe://tap/X?n=%20%20Ana%0A%0ACruz%20%20")!!
        assertEquals("Ana Cruz", t.hints.name)
    }

    @Test
    fun tagUriRoundTripsThroughParser() {
        val uri = KipatTagUri.build("KPT-DEMO-0001", "Vash Puno", RevealTemplateId.SIGNATURE_DRIP)
        assertEquals("https://kipat.app/c/KPT-DEMO-0001?n=Vash+Puno&t=drip", uri)
        val t = NfcIntentParser.parse(uri)!!
        assertEquals("KPT-DEMO-0001", t.cardId)
        assertEquals("Vash Puno", t.hints.name)
        assertEquals(RevealTemplateId.SIGNATURE_DRIP, t.hints.template)
    }

    @Test
    fun tagUriClampsNameHintToFitSmallTags() {
        val uri = KipatTagUri.build("KPT-1", "A very long display name that would not fit")
        val name = NfcIntentParser.parse(uri)!!.hints.name
        assertNotNull(name)
        assertTrue(name!!.length <= KipatTagUri.MAX_NAME_HINT)
        // NTAG213 holds 144 bytes; URI + AAR must stay comfortably under that.
        assertTrue(uri.length < 90)
    }

    @Test(expected = IllegalArgumentException::class)
    fun tagUriRejectsInvalidIds() {
        KipatTagUri.build("bad id with spaces")
    }
}
