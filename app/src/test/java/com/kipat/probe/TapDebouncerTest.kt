package com.kipat.probe

import com.kipat.probe.reveal.RevealSource
import com.kipat.probe.reveal.TapDebouncer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Preflight TRIGGER-1: a double-fired tag *or link* is one tap (docs/device-validation/preflight-issues.md). */
class TapDebouncerTest {

    @Test
    fun repeatedExternalTapOfTheSameCardIsOneTap() {
        val d = TapDebouncer()
        d.accept("KPT-1", 1_000)
        assertTrue(d.isDuplicate("KPT-1", RevealSource.NFC_TAG, 1_300))
        // Previously only NFC was debounced; a double-fired link restarted the Reveal and recorded twice.
        assertTrue(d.isDuplicate("KPT-1", RevealSource.DEEP_LINK, 1_300))
    }

    @Test
    fun differentCardsLateRepeatsAndInAppLaunchesAlwaysRun() {
        val d = TapDebouncer()
        d.accept("KPT-1", 1_000)
        assertFalse(d.isDuplicate("KPT-2", RevealSource.NFC_TAG, 1_100))
        assertFalse(d.isDuplicate("KPT-1", RevealSource.NFC_TAG, 1_000 + TapDebouncer.DEFAULT_WINDOW_MS))
        assertFalse(d.isDuplicate("KPT-1", RevealSource.PREVIEW, 1_100))
        assertFalse(d.isDuplicate("KPT-1", RevealSource.REPLAY, 1_100))
        assertFalse("unresolved cards have no identity to compare", d.isDuplicate(null, RevealSource.NFC_TAG, 1_100))
    }
}
