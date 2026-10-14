package com.kipat.probe

import com.kipat.probe.nfc.profile.NfcProfileFormat
import com.kipat.probe.nfc.profile.NfcProfileLimits
import com.kipat.probe.nfc.profile.NfcProfileMode
import com.kipat.probe.nfc.profile.NfcProfileParse
import com.kipat.probe.nfc.profile.NfcProfilePayload
import com.kipat.probe.nfc.profile.NfcProfileType
import com.kipat.probe.reveal.RevealTemplateId
import com.kipat.probe.reveal.RevealTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The self-contained profile format: JSON, version handling and validation. Robolectric for org.json. */
@RunWith(RobolectricTestRunner::class)
class NfcProfileFormatTest {

    private fun parse(json: String) = NfcProfileFormat.parse(json.toByteArray(Charsets.UTF_8))
    private fun valid(json: String) = parse(json) as? NfcProfileParse.Valid ?: error("expected valid: ${parse(json)}")

    @Test
    fun sampleRoundTripsExactly() {
        val bytes = NfcProfileFormat.toBytes(NfcProfilePayload.SAMPLE)
        val parsed = NfcProfileFormat.parse(bytes) as NfcProfileParse.Valid
        assertEquals(NfcProfilePayload.SAMPLE, parsed.profile)
        assertTrue(parsed.warnings.isEmpty())
        assertEquals(bytes.size, parsed.payloadBytes)
    }

    @Test
    fun jsonIsCompactWithStableKeyOrder() {
        val json = NfcProfileFormat.toJson(NfcProfilePayload.SAMPLE)
        assertEquals(
            """{"v":1,"id":"test-vash-puno","type":"person","name":"Vash Puno","headline":"BS Computer Science",""" +
                """"organization":"Polytechnic University of the Philippines","role":"Developer",""" +
                """"bio":"Computer Science student and developer.","accent":"cobalt","reveal":"precision_orbit"}""",
            json,
        )
        assertFalse(json.contains('\n'))
        assertFalse("no invented links or email in the sample", json.contains("http") || json.contains("@"))
    }

    @Test
    fun minimalProfileWorks() {
        val p = valid("""{"v":1,"name":"Vash Puno"}""").profile
        assertEquals("Vash Puno", p.displayName)
        assertEquals(NfcProfileType.PERSON, p.type)
        assertNull(p.headline)
        assertNull(p.accent)
        assertNull(p.reveal)
    }

    @Test
    fun utf8NamesSurviveAndAreCountedInBytes() {
        val p = NfcProfilePayload(displayName = "José Dela Cruz", organization = "Université de Montréal — 東京")
        val bytes = NfcProfileFormat.toBytes(p)
        assertTrue("non-ASCII stays UTF-8, not \\u escapes", String(bytes, Charsets.UTF_8).contains("José"))
        assertEquals(NfcProfileFormat.toJson(p).toByteArray(Charsets.UTF_8).size, bytes.size)
        assertTrue(bytes.size > NfcProfileFormat.toJson(p).length)
        assertEquals(p, (NfcProfileFormat.parse(bytes) as NfcProfileParse.Valid).profile)
    }

    @Test
    fun escapingRoundTrips() {
        val p = NfcProfilePayload(displayName = "A \"quoted\" \\ name", bio = "Line one\nline two\ttab </script>")
        assertEquals(p, (NfcProfileFormat.parse(NfcProfileFormat.toBytes(p)) as NfcProfileParse.Valid).profile)
    }

    @Test
    fun unknownVersionIsNeverReinterpreted() {
        val r = parse("""{"v":2,"name":"Vash Puno"}""")
        assertTrue(r is NfcProfileParse.UnsupportedVersion)
        assertEquals(2, (r as NfcProfileParse.UnsupportedVersion).version)
    }

    @Test
    fun malformedAndHostilePayloadsAreInvalidNotCrashes() {
        listOf(
            """{"v":1,"name":"Vash""",
            """[1,2,3]""",
            """not json""",
            "",
            """{"name":"No version"}""",
            """{"v":"1","name":"String version"}""",
            """{"v":1}""",
            """{"v":1,"name":"   "}""",
            """{"v":1,"name":42}""",
            """{"v":1,"name":"${"x".repeat(NfcProfileLimits.NAME + 1)}"}""",
        ).forEach { assertTrue(it, parse(it) is NfcProfileParse.Invalid) }
        assertTrue(NfcProfileFormat.parse(byteArrayOf(0x7B, 0xC3.toByte(), 0x28, 0x7D)) is NfcProfileParse.Invalid)
        assertTrue(NfcProfileFormat.parse(ByteArray(NfcProfileLimits.MAX_PAYLOAD_BYTES + 1) { ' '.code.toByte() }) is NfcProfileParse.Invalid)
        val reason = (parse("""{"v":1,"name":"Secret Person","bio":"unterminated""") as NfcProfileParse.Invalid).reason
        assertFalse("the reason never echoes the payload: $reason", reason.contains("Secret"))
    }

    @Test
    fun badOptionalFieldsAreDroppedWithWarnings() {
        val r = valid(
            """{"v":1,"name":"Vash Puno","headline":"${"h".repeat(NfcProfileLimits.HEADLINE + 1)}",""" +
                """"avatarUrl":"intent://scan/#Intent;scheme=zxing;end","links":{"website":"javascript:alert(1)",""" +
                """"github":"https://github.com/example","linkedin":"file:///sdcard/x"},"publicEmail":"not-an-email",""" +
                """"accent":"magenta","reveal":"fireworks","type":"robot","bio":7,"unknown":"ignored"}""",
        )
        val p = r.profile
        assertNull(p.headline)
        assertNull(p.avatarUrl)
        assertNull(p.website)
        assertNull(p.linkedin)
        assertNull(p.publicEmail)
        assertNull(p.accent)
        assertNull(p.reveal)
        assertNull(p.bio)
        assertEquals(NfcProfileType.PERSON, p.type)
        assertEquals("https://github.com/example", p.github)
        val fields = r.warnings.map { it.field }.toSet()
        listOf("headline", "avatarUrl", "website", "linkedin", "publicEmail", "accent", "reveal", "type", "bio").forEach {
            assertTrue("$it warned in $fields", it in fields)
        }
    }

    @Test
    fun organizationAccentAndTemplateParse() {
        val p = valid("""{"v":1,"type":"organization","name":"Kipat Labs","accent":"teal","reveal":"aperture"}""").profile
        assertEquals(NfcProfileType.ORGANIZATION, p.type)
        assertEquals(RevealTheme.AURORA, p.accent)
        assertEquals(RevealTemplateId.APERTURE, p.reveal)
        assertEquals(RevealTheme.EMBER, valid("""{"v":1,"name":"A","accent":"amber"}""").profile.accent)
    }

    @Test
    fun everyTemplateAndAccentRoundTrips() {
        RevealTemplateId.entries.forEach { t ->
            listOf(RevealTheme.NIGHT, RevealTheme.AURORA, RevealTheme.EMBER).forEach { a ->
                val p = NfcProfilePayload(displayName = "A", accent = a, reveal = t)
                assertEquals(p, (NfcProfileFormat.parse(NfcProfileFormat.toBytes(p)) as NfcProfileParse.Valid).profile)
            }
        }
    }

    @Test
    fun compactModeDropsLongFields() {
        val full = NfcProfilePayload.SAMPLE.copy(website = "https://example.com", publicEmail = "a@example.com")
        val compact = (NfcProfileFormat.parse(NfcProfileFormat.toBytes(full, NfcProfileMode.COMPACT)) as NfcProfileParse.Valid).profile
        assertEquals(full.compact(), compact)
        assertNull(compact.bio)
        assertNull(compact.website)
        assertEquals("Developer", compact.role)
    }
}
