package com.kipat.probe

import com.kipat.probe.data.SampleData
import com.kipat.probe.model.AuthenticityState
import com.kipat.probe.model.ConnectionSort
import com.kipat.probe.model.ProfileField
import com.kipat.probe.model.ProfileTemplateId
import com.kipat.probe.model.ProfileValidation
import com.kipat.probe.model.StatusTone
import com.kipat.probe.model.initialsOf
import com.kipat.probe.model.organizeConnections
import com.kipat.probe.model.present
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionsAndAuthenticityTest {

    private val all = SampleData.connections(1_700_000_000_000L)

    @Test
    fun recentSortIsNewestFirstAndPinnedAreSeparated() {
        val s = organizeConnections(all, "", ConnectionSort.RECENT)
        assertTrue(s.pinned.all { it.pinned })
        assertTrue(s.others.none { it.pinned })
        assertEquals(all.size, s.pinned.size + s.others.size)
        assertEquals(s.others.sortedByDescending { it.connectedAtMillis }, s.others)
    }

    @Test
    fun alphabeticalSortIgnoresCase() {
        val list = all.map { if (it.id == "c-2") it.copy(displayName = "aaron lower") else it }
        val s = organizeConnections(list, "", ConnectionSort.ALPHABETICAL)
        assertEquals("aaron lower", s.others.first().displayName)
    }

    @Test
    fun searchMatchesNameRoleAndOrganization() {
        assertEquals(listOf("Alyssa Ramos"), organizeConnections(all, "alyssa", ConnectionSort.RECENT).let { it.pinned + it.others }.map { it.displayName })
        assertEquals(listOf("Daniel Cruz"), organizeConnections(all, "android", ConnectionSort.RECENT).let { it.pinned + it.others }.map { it.displayName })
        assertEquals(1, organizeConnections(all, "DevFest", ConnectionSort.RECENT).others.size)
        assertTrue(organizeConnections(all, "zzz-no-match", ConnectionSort.RECENT).isEmpty)
        assertEquals(all.size, organizeConnections(all, "   ", ConnectionSort.RECENT).let { it.pinned.size + it.others.size })
    }

    @Test
    fun onlyAuthenticShowsCheckAndDemoIsAlwaysLabelled() {
        AuthenticityState.entries.forEach { state ->
            val p = state.present(isDemo = false)
            assertEquals(state == AuthenticityState.AUTHENTIC, p.showsCheck)
        }
        assertTrue(AuthenticityState.AUTHENTIC.present(isDemo = true).label.contains("demo"))
        assertFalse(AuthenticityState.AUTHENTIC.present(isDemo = false).label.contains("demo"))
    }

    @Test
    fun authenticityTonesAreDistinctForKeyStates() {
        assertEquals(StatusTone.POSITIVE, AuthenticityState.AUTHENTIC.present(false).tone)
        assertEquals(StatusTone.WARNING, AuthenticityState.UNVERIFIED.present(false).tone)
        assertEquals(StatusTone.NEGATIVE, AuthenticityState.REVOKED.present(false).tone)
        assertEquals(StatusTone.NEGATIVE, AuthenticityState.ERROR.present(false).tone)
        assertEquals(StatusTone.PENDING, AuthenticityState.CHECKING.present(false).tone)
        assertEquals(StatusTone.NEUTRAL, AuthenticityState.UNKNOWN.present(false).tone)
        assertFalse(AuthenticityState.UNKNOWN.present(false).label.contains("Verified"))
    }

    @Test
    fun authenticityWireIdsFallBackToUnknown() {
        assertEquals(AuthenticityState.REVOKED, AuthenticityState.fromWireId("REVOKED"))
        assertEquals(AuthenticityState.UNKNOWN, AuthenticityState.fromWireId(null))
        assertEquals(AuthenticityState.UNKNOWN, AuthenticityState.fromWireId("yes"))
    }

    @Test
    fun initials() {
        assertEquals("VP", initialsOf("Vash Puno"))
        assertEquals("AC", initialsOf("Ana Maria Cruz"))
        assertEquals("MA", initialsOf("madonna"))
        assertEquals("K", initialsOf("   "))
    }

    @Test
    fun profileValidation() {
        assertNotNull(ProfileValidation.validate(ProfileField.DISPLAY_NAME, " "))
        assertNull(ProfileValidation.validate(ProfileField.DISPLAY_NAME, "Vash"))
        assertNotNull(ProfileValidation.validate(ProfileField.BIO, "<script>alert(1)</script>"))
        assertNotNull(ProfileValidation.validate(ProfileField.WEBSITE, "javascript:alert(1)"))
        assertNotNull(ProfileValidation.validate(ProfileField.WEBSITE, "http://insecure.example"))
        assertNull(ProfileValidation.validate(ProfileField.WEBSITE, "https://kipat.app"))
        assertNotNull(ProfileValidation.validate(ProfileField.PUBLIC_EMAIL, "nope"))
        assertNull(ProfileValidation.validate(ProfileField.PUBLIC_EMAIL, "vash@example.com"))
        assertNull(ProfileValidation.validate(ProfileField.GITHUB, "@vashpuno"))
        assertNull(ProfileValidation.validate(ProfileField.UNIVERSITY, ""))
    }

    @Test
    fun sampleIdentityIsPublishableAndOnlyTemplateFieldsArePublished() {
        val id = SampleData.identity
        assertTrue(ProfileValidation.validate(id.profileTemplate, id.fields).isEmpty())
        val published = id.publishedFields().keys
        assertTrue(published.all { it in ProfileTemplateId.STUDENT_MINIMAL.fields })
        assertFalse(ProfileField.COMPANY in published)
    }
}
