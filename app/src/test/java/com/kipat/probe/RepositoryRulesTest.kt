package com.kipat.probe

import com.kipat.probe.data.KipatRepository
import com.kipat.probe.data.toRevealPayload
import com.kipat.probe.model.AuthenticityState
import com.kipat.probe.model.RecentTap
import com.kipat.probe.reveal.RevealPayload
import com.kipat.probe.nfc.KipatTagUri
import com.kipat.probe.nfc.NfcIntentParser
import com.kipat.probe.reveal.RevealSource
import com.kipat.probe.reveal.RevealTemplateId
import com.kipat.probe.reveal.RevealTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Product rules that the redesign must keep: a tap is not a connection until the user says so. */
class RepositoryRulesTest {

    @Test
    fun recordingATapDoesNotCreateAConnection() {
        val payload = RevealPayload.unresolved("KPT-RULE-1", RevealSource.NFC_TAG).copy(displayName = "Rule Tester", connectable = true)
        val before = KipatRepository.connections.value.size
        KipatRepository.recordTap(payload)
        assertEquals(before, KipatRepository.connections.value.size)
        assertFalse(KipatRepository.isConnected("KPT-RULE-1"))
        assertTrue(KipatRepository.recentTaps.value.any { it.cardId == "KPT-RULE-1" && !it.savedAsConnection })
    }

    @Test
    fun tappingYourOwnCardIsNotRecordedAsSomeoneYouMet() {
        val own = KipatRepository.identity.value
        val before = KipatRepository.recentTaps.value.size
        KipatRepository.recordTap(RevealPayload.unresolved(own.cardId, RevealSource.NFC_TAG).copy(displayName = own.displayName))
        assertEquals(before, KipatRepository.recentTaps.value.size)
    }

    @Test
    fun connectFromRecentTapCreatesOneConnectionAndMarksTapSaved() {
        val tap = RecentTap("t-rule", "KPT-RULE-2", "Ana Reyes", "Product Designer, Acme", AuthenticityState.UNVERIFIED, 0L, savedAsConnection = false, isDemo = true)
        val c1 = KipatRepository.connect(tap)
        val c2 = KipatRepository.connect(tap)
        assertEquals(c1.id, c2.id)
        assertEquals("Product Designer", c1.subtitle)
        assertEquals("Acme", c1.organization)
        assertTrue(KipatRepository.isConnected("KPT-RULE-2"))
    }

    @Test
    fun replayingAnUnsavedTapStillOffersConnect() {
        val tap = RecentTap("t-rule-3", "KPT-RULE-3", "Jonas Reyes", "BS Architecture, UST", AuthenticityState.UNVERIFIED, 0L, savedAsConnection = false, isDemo = true)
        val p = tap.toRevealPayload()
        assertEquals(RevealSource.NFC_TAG, p.source)
        assertTrue(p.canConnect)
        assertEquals("BS Architecture", p.subtitle)
        assertEquals("UST", p.organization)
    }

    @Test
    fun revealCtasUsePlainVerbs() {
        assertEquals("Connect", RevealPayload.defaultPrimaryCta(RevealSource.NFC_TAG))
        assertEquals("Connect", RevealPayload.defaultPrimaryCta(RevealSource.PREVIEW))
        assertEquals("Connected", RevealPayload.defaultPrimaryCta(RevealSource.REPLAY))
        assertEquals("Save contact", RevealPayload.defaultSecondaryCta(RevealSource.NFC_TAG))
    }

    @Test
    fun appliedRevealStyleIsWhatOthersSeeAndWhatTheTagCarries() {
        val original = KipatRepository.identity.value
        try {
            KipatRepository.publishIdentity(original.copy(revealTemplate = RevealTemplateId.SPATIAL_CARD, revealTheme = RevealTheme.MONO))
            val saved = KipatRepository.identity.value
            assertEquals(RevealTemplateId.SPATIAL_CARD, saved.revealTemplate)
            val payload = saved.toRevealPayload(RevealSource.PREVIEW, isDemo = true, authenticity = AuthenticityState.AUTHENTIC)
            assertEquals(RevealTemplateId.SPATIAL_CARD, payload.template)
            assertEquals(RevealTheme.MONO, payload.theme)
            val uri = KipatTagUri.build(saved.cardId, saved.displayName, saved.revealTemplate)
            assertEquals(RevealTemplateId.SPATIAL_CARD, NfcIntentParser.parse(uri)!!.hints.template)
        } finally {
            KipatRepository.publishIdentity(original)
        }
    }
}
