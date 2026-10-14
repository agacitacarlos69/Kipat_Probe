package com.kipat.probe

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import com.kipat.probe.nfc.profile.KipatProfileMessage
import com.kipat.probe.nfc.profile.Ndef
import com.kipat.probe.nfc.profile.NdefRecordData
import com.kipat.probe.nfc.profile.NfcProfileDiagnostics
import com.kipat.probe.nfc.profile.NfcProfileFormat
import com.kipat.probe.nfc.profile.NfcProfileMode
import com.kipat.probe.nfc.profile.NfcProfilePayload
import com.kipat.probe.nfc.profile.ProfileReadBack
import com.kipat.probe.nfc.profile.ProfileWritePlan
import com.kipat.probe.nfc.profile.TagSnapshot
import com.kipat.probe.nfc.profile.VerifyResult
import com.kipat.probe.nfc.profile.WriteDecision
import com.kipat.probe.nfc.toData
import com.kipat.probe.nfc.toMessage
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * NDEF layout and exact sizes against Android's own NdefMessage encoder, and the write-flow
 * decisions (capacity, replace confirmation, read-back verification). No NFC hardware involved.
 */
@RunWith(RobolectricTestRunner::class)
class NfcProfileNdefTest {

    private val pkg = "com.kipat.probe"
    private val sample = NfcProfilePayload.SAMPLE
    private val records = KipatProfileMessage.records(sample, NfcProfileMode.FULL, pkg)

    private fun tag(maxSize: Int? = 496, writable: Boolean? = true, records: List<NdefRecordData>? = emptyList(), ndef: Boolean = true, formatable: Boolean = false) =
        TagSnapshot("04:A1:B2:C3:D4:E5:F6", listOf("NfcA", "MifareUltralight", "Ndef"), if (ndef) "org.nfcforum.ndef.type2" else null, maxSize, writable, formatable, records)

    @Test
    fun profileRecordFirstAndAarLast() {
        assertEquals(2, records.size)
        assertEquals(Ndef.TNF_MIME_MEDIA, records.first().tnf)
        assertEquals(NfcProfileFormat.MIME_TYPE, records.first().typeString)
        assertTrue(records.first().isKipatProfile)
        assertEquals(Ndef.AAR_TYPE, records.last().typeString)
        assertEquals(1, KipatProfileMessage.records(sample, NfcProfileMode.FULL, pkg, includeAar = false).size)
    }

    @Test
    fun recordsMatchAndroidFactories() {
        val mime = Ndef.mime(NfcProfileFormat.MIME_TYPE, byteArrayOf(1, 2, 3))
        val platformMime = NdefRecord.createMime(NfcProfileFormat.MIME_TYPE, byteArrayOf(1, 2, 3))
        assertEquals(platformMime.tnf, mime.tnf)
        assertArrayEquals(platformMime.type, mime.type)
        val aar = NdefRecord.createApplicationRecord(pkg)
        assertArrayEquals(aar.type, Ndef.aar(pkg).type)
        assertArrayEquals(aar.payload, Ndef.aar(pkg).payload)
        val uri = NdefRecord.createUri("https://kipat.app/c/KPT-1")
        assertArrayEquals(uri.payload, Ndef.uri("https://kipat.app/c/KPT-1").payload)
        assertEquals("https://kipat.app/c/KPT-1", uri.toData().uriOrNull())
    }

    @Test
    fun computedSizeEqualsAndroidEncodedLength() {
        val profiles = listOf(
            sample,
            sample.compact(),
            NfcProfilePayload(displayName = "Vash Puno"),
            NfcProfilePayload(displayName = "José Dela Cruz", bio = "é".repeat(200)), // > 255-byte payload: long record
        )
        profiles.forEach { p ->
            NfcProfileMode.entries.forEach { mode ->
                listOf(true, false).forEach { aar ->
                    val r = KipatProfileMessage.records(p, mode, pkg, aar)
                    assertEquals("${p.displayName} $mode aar=$aar", r.toMessage().byteArrayLength, Ndef.messageSize(r))
                    assertEquals(r.toMessage().byteArrayLength, KipatProfileMessage.size(p, mode, pkg, aar))
                }
            }
        }
    }

    @Test
    fun platformRoundTripKeepsBytes() {
        val back = NdefMessage(records.toMessage().toByteArray()).records.map { it.toData() }
        assertEquals(VerifyResult.Verified::class, ProfileReadBack.verify(records, sample, NfcProfileMode.FULL, back)::class)
    }

    @Test
    fun sampleNeedsMoreThanAnNtag213() {
        val size = KipatProfileMessage.size(sample, NfcProfileMode.FULL, pkg)
        assertEquals("documented in docs/nfc-self-contained-profile.md", 341, size)
        assertTrue("sample is $size bytes", size > 137 && size <= 496)
        assertEquals(112, KipatProfileMessage.size(NfcProfilePayload(displayName = "Vash Puno"), NfcProfileMode.FULL, pkg))
    }

    @Test
    fun tooLargeIsRefusedWithFieldCosts() {
        val size = KipatProfileMessage.size(sample, NfcProfileMode.FULL, pkg)
        val d = ProfileWritePlan.decide(tag(maxSize = 137), size, confirmedReplace = true) { KipatProfileMessage.fieldCosts(sample, NfcProfileMode.FULL) }
        d as WriteDecision.TooLarge
        assertEquals(size, d.required)
        assertEquals(137, d.available)
        assertEquals("Organization", d.fieldCosts.first().first)
        assertEquals(d.fieldCosts.sortedByDescending { it.second }, d.fieldCosts)
        assertTrue(d.fieldCosts.any { it.first == "Bio" })
        // Exactly full fits.
        assertEquals(WriteDecision.Proceed, ProfileWritePlan.decide(tag(maxSize = size), size, false) { emptyList() })
    }

    @Test
    fun writeDecisions() {
        val existing = listOf(Ndef.uri("https://example.com"), Ndef.aar("com.other.app"))
        assertEquals(WriteDecision.ReadOnly, ProfileWritePlan.decide(tag(writable = false), 100, true) { emptyList() })
        assertEquals(WriteDecision.NotNdef, ProfileWritePlan.decide(tag(ndef = false), 100, false) { emptyList() })
        assertEquals(WriteDecision.Proceed, ProfileWritePlan.decide(tag(ndef = false, formatable = true), 100, false) { emptyList() })
        assertEquals(WriteDecision.Proceed, ProfileWritePlan.decide(tag(records = emptyList()), 100, false) { emptyList() })
        assertEquals(WriteDecision.Proceed, ProfileWritePlan.decide(tag(records = listOf(NdefRecordData(Ndef.TNF_EMPTY, ByteArray(0), payload = ByteArray(0)))), 100, false) { emptyList() })
        val confirm = ProfileWritePlan.decide(tag(records = existing), 100, false) { emptyList() } as WriteDecision.ConfirmReplace
        assertEquals(listOf("Link: https://example.com", "Opens app com.other.app"), confirm.existing)
        assertEquals(WriteDecision.Proceed, ProfileWritePlan.decide(tag(records = existing), 100, true) { emptyList() })
        assertTrue(ProfileWritePlan.decide(tag(records = null), 100, false) { emptyList() } is WriteDecision.ConfirmReplace)
    }

    @Test
    fun readBackMustMatchExactly() {
        assertTrue(ProfileReadBack.verify(records, sample, NfcProfileMode.FULL, records) is VerifyResult.Verified)
        assertTrue(ProfileReadBack.verify(records, sample, NfcProfileMode.FULL, null) is VerifyResult.Mismatch)
        assertTrue(ProfileReadBack.verify(records, sample, NfcProfileMode.FULL, records.take(1)) is VerifyResult.Mismatch)
        val other = KipatProfileMessage.records(sample.copy(displayName = "Someone Else"), NfcProfileMode.FULL, pkg)
        assertTrue(ProfileReadBack.verify(records, sample, NfcProfileMode.FULL, other) is VerifyResult.Mismatch)
        val truncated = listOf(Ndef.mime(NfcProfileFormat.MIME_TYPE, records[0].payload.copyOf(20)), records[1])
        assertTrue(ProfileReadBack.verify(records, sample, NfcProfileMode.FULL, truncated) is VerifyResult.Mismatch)
    }

    @Test
    fun diagnosticReportDescribesTagAndProfile() {
        val report = NfcProfileDiagnostics.report(tag(records = records), "Kipat test", 36, "Robolectric")
        listOf("04:A1:B2:C3:D4:E5:F6", "496 bytes", "Kipat profile: valid, v1", "name: Vash Puno", "unverified").forEach {
            assertTrue("$it in\n$report", report.contains(it))
        }
        val invalid = NfcProfileDiagnostics.report(tag(records = listOf(Ndef.mime(NfcProfileFormat.MIME_TYPE, "{oops".toByteArray()))), "Kipat test", 36, "Robolectric")
        assertTrue(invalid, invalid.contains("Kipat profile: invalid"))
        assertTrue(NfcProfileDiagnostics.report(tag(records = null), "k", 26, "d").contains("unreadable"))
    }
}
