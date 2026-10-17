package com.kipat.probe

import android.content.Context
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.kipat.probe.data.ConnectionRules
import com.kipat.probe.data.KipatLocalStore
import com.kipat.probe.data.KipatRepository
import com.kipat.probe.data.NfcProfileReveal.toRevealPayload
import com.kipat.probe.data.db.ConnectionEntity
import com.kipat.probe.data.db.KipatDatabase
import com.kipat.probe.model.Connection
import com.kipat.probe.model.ConnectionSource
import com.kipat.probe.nfc.peer.KipatPeerSessions
import com.kipat.probe.nfc.peer.KipatTapLauncher
import com.kipat.probe.nfc.profile.NfcProfilePayload
import com.kipat.probe.reveal.RevealPayload
import com.kipat.probe.reveal.RevealSource
import com.kipat.probe.reveal.RevealTemplateId
import com.kipat.probe.reveal.RevealTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Connections are the "Remember" step of the Kipat loop: they must survive the process. Each
 * "recreate" closes the database, wipes the in-memory repository and loads again from disk,
 * which is what a new process does.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-notnight-xxhdpi")
class ConnectionPersistenceTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    private val ana = NfcProfilePayload(
        id = "KPT-ANA-0001", displayName = "Ana Reyes", headline = "Product Designer", organization = "Acme",
        reveal = RevealTemplateId.PRECISION_ORBIT, accent = RevealTheme.entries.last(),
    )

    /** The same person through her phone (Kipat Tap) and through her linked card. */
    private fun viaPhone(p: NfcProfilePayload = ana): RevealPayload = KipatTapLauncher.payloadFor(p)
    private fun viaCard(p: NfcProfilePayload = ana): RevealPayload = p.toRevealPayload(RevealSource.NFC_TAG, testProfile = false)

    private fun real(): List<Connection> = KipatRepository.connections.value.filterNot { it.isDemo }
    private fun stored(identity: String = ana.id!!): Connection? = real().firstOrNull { it.cardId == identity }

    private fun recreate() {
        KipatLocalStore.resetForTest()                                   // flush pending writes, close
        KipatRepository.restoreLocal(emptyList(), emptyList(), includeSamples = false)  // nothing in memory
        KipatLocalStore.init(context)                                    // new process: load from disk
    }

    @Before
    fun reset() {
        KipatLocalStore.resetForTest()
        context.deleteDatabase(KipatDatabase.NAME)
        KipatLocalStore.init(context)
        KipatPeerSessions.attach(context)
    }

    @After
    fun tearDown() {
        KipatLocalStore.resetForTest()
        context.deleteDatabase(KipatDatabase.NAME)
    }

    // ------------------------------------------------------------------ create, survive, dedupe

    @Test
    fun connectIsStoredAndSurvivesProcessRecreation() {
        val c = KipatRepository.connect(viaPhone())
        recreate()
        val back = stored()!!
        assertEquals(c.id, back.id)
        assertEquals(c.connectedAtMillis, back.connectedAtMillis)
        assertEquals("Ana Reyes", back.displayName)
        assertEquals("Product Designer", back.subtitle)
        assertEquals("Acme", back.organization)
        assertEquals(RevealTemplateId.PRECISION_ORBIT, back.revealTemplate)
        assertEquals(ana.accent, back.revealTheme)
        assertEquals(ConnectionSource.PHONE_TAP, back.source)
        assertFalse(back.isDemo)
        assertTrue(KipatRepository.isConnected(ana.id))
    }

    @Test
    fun phoneTapAndLinkedCardResolveToTheSameConnection() {
        val first = KipatRepository.connect(viaPhone())
        val second = KipatRepository.connect(viaCard())
        assertEquals(first.id, second.id)
        assertEquals(1, real().count { it.cardId == ana.id })
        recreate()
        assertEquals("one row on disk too", 1, real().count { it.cardId == ana.id })
        assertEquals("the first source is kept", ConnectionSource.PHONE_TAP, stored()!!.source)
    }

    @Test
    fun sourceIsRetainedForCardConnections() {
        KipatRepository.connect(viaCard())
        recreate()
        assertEquals(ConnectionSource.NFC_CARD, stored()!!.source)
    }

    // ------------------------------------------------------------------ encounters refresh, never create

    @Test
    fun secondEncounterUpdatesLastSeenAndKeepsTheRelationship() {
        val c = KipatRepository.connect(viaPhone())
        KipatRepository.togglePin(c.id)
        Thread.sleep(5)
        val moved = ana.copy(organization = "Kipat", headline = "Design Lead", reveal = RevealTemplateId.APERTURE)
        KipatRepository.recordTap(viaPhone(moved))
        recreate()
        val back = stored()!!
        assertTrue("lastSeenAt moved", back.lastSeenAtMillis!! > c.connectedAtMillis)
        assertEquals("public snapshot refreshed", "Kipat", back.organization)
        assertEquals("Design Lead", back.subtitle)
        assertEquals(RevealTemplateId.APERTURE, back.revealTemplate)
        assertEquals("connectedAt never changes", c.connectedAtMillis, back.connectedAtMillis)
        assertEquals(c.id, back.id)
        assertTrue("pin kept", back.pinned)
        assertEquals(ConnectionSource.PHONE_TAP, back.source)
    }

    @Test
    fun newerRevisionRefreshesOlderRevisionDoesNot() {
        val c = ConnectionRules.newConnection("c-1", viaCard(), at = 1_000, revision = 5)
        val newer = ConnectionRules.refreshed(c, viaCard(ana.copy(organization = "New")), at = 2_000, live = true, revision = 6)
        assertEquals("New", newer.organization)
        assertEquals(6L, newer.profileRevision)
        val older = ConnectionRules.refreshed(newer, viaCard(ana.copy(organization = "Stale")), at = 3_000, live = true, revision = 4)
        assertEquals("an older revision never overwrites", "New", older.organization)
        assertEquals(3_000L, older.lastSeenAtMillis)
        assertEquals(1_000L, older.connectedAtMillis)
    }

    @Test
    fun staticSelfContainedCardDoesNotOverwriteALiveSnapshot() {
        KipatRepository.connect(viaPhone())
        val staleCopy = ana.copy(organization = "Old Company").toRevealPayload(RevealSource.NFC_TAG) // testProfile = true
        KipatRepository.recordTap(staleCopy, live = true)
        assertEquals("Acme", stored()!!.organization)
    }

    // ------------------------------------------------------------------ pin, remove, undo

    @Test
    fun pinAndUnpinPersist() {
        val c = KipatRepository.connect(viaPhone())
        KipatRepository.togglePin(c.id)
        recreate()
        assertTrue(stored()!!.pinned)
        KipatRepository.togglePin(c.id)
        recreate()
        assertFalse(stored()!!.pinned)
    }

    @Test
    fun removalPersistsAndAnEncounterNeverReconnects() {
        val c = KipatRepository.connect(viaPhone())
        KipatRepository.removeConnection(c.id)
        recreate()
        assertNull(stored())
        // Meeting her again puts her in Recent, not back in Connections.
        KipatRepository.recordTap(viaCard())
        recreate()
        assertNull("no silent reconnect", stored())
        val tap = KipatRepository.recentTaps.value.first { it.cardId == ana.id }
        assertFalse(tap.savedAsConnection)
        assertEquals(ConnectionSource.NFC_CARD, tap.source)
    }

    @Test
    fun undoOfRemovalRestoresTheSameRow() {
        val c = KipatRepository.connect(viaPhone())
        KipatRepository.togglePin(c.id)
        val removed = KipatRepository.removeConnection(c.id)!!
        KipatRepository.restoreConnection(removed)
        recreate()
        val back = stored()!!
        assertEquals(c.id, back.id)
        assertEquals(c.connectedAtMillis, back.connectedAtMillis)
        assertTrue(back.pinned)
    }

    @Test
    fun recentTapsPersistSeparatelyAndAreCapped() {
        repeat(25) { i -> KipatRepository.recordTap(viaPhone(ana.copy(id = "KPT-P-$i", displayName = "Person $i"))) }
        recreate()
        val taps = KipatRepository.recentTaps.value.filterNot { it.isDemo }
        assertEquals(KipatLocalStore.MAX_TAPS, taps.size)
        assertEquals("newest first", "Person 24", taps.first().displayName)
        assertTrue("a tap alone never creates a Connection", real().none { it.cardId.startsWith("KPT-P-") })
    }

    // ------------------------------------------------------------------ demo data and bad data

    @Test
    fun sampleDataIsNeverPersisted() {
        val demo = KipatRepository.connections.value.firstOrNull { it.isDemo }
        demo?.let { KipatRepository.togglePin(it.id) }
        KipatLocalStore.resetForTest()
        val db = Room.databaseBuilder(context, KipatDatabase::class.java, KipatDatabase.NAME).allowMainThreadQueries().build()
        assertTrue("no demo rows on disk", db.connections().all().isEmpty())
        db.close()
        // A release start (no samples) is empty until the user connects.
        KipatRepository.restoreLocal(emptyList(), emptyList(), includeSamples = false)
        assertTrue(KipatRepository.connections.value.isEmpty())
    }

    @Test
    fun malformedProfilesCannotCorruptTheStore() {
        // Hostile or broken text from a tap is cleaned or refused before it is written.
        val evil = ana.copy(id = "KPT-EVIL-1", displayName = "Eve\u0000\u0007 " + "x".repeat(400), organization = "Org\nwith\tcontrols")
        KipatRepository.connect(viaPhone(evil))
        KipatRepository.connect(RevealPayload.unresolved("bad id with spaces", RevealSource.NFC_TAG).copy(displayName = "No id"))
        recreate()
        val e = stored("KPT-EVIL-1")!!
        assertFalse(e.displayName.any { it.code < 0x20 })
        assertTrue(e.displayName.length <= RevealPayload.MAX_NAME)
        assertFalse(e.organization.contains('\n'))
        assertTrue("no usable public id: never stored", real().none { it.displayName == "No id" })

        // Rows damaged on disk load with safe fallbacks, or are skipped; they never crash startup.
        KipatLocalStore.resetForTest()
        val db = Room.databaseBuilder(context, KipatDatabase::class.java, KipatDatabase.NAME).allowMainThreadQueries().build()
        db.connections().upsert(ConnectionEntity("c-x", "KPT-X-1", "Xavier", "", null, "", "javascript:alert(1)", -3, "holo_vortex", "neon", "hacked", 1, null, "TELEPATHY", false))
        db.connections().upsert(ConnectionEntity("c-y", "../../etc/passwd", "Bad", "", null, "", null, null, "aperture", "cobalt", "unverified", 1, null, "QR", false))
        db.close()
        KipatRepository.restoreLocal(emptyList(), emptyList(), includeSamples = false)
        KipatLocalStore.init(context)
        val x = stored("KPT-X-1")!!
        assertEquals(RevealTemplateId.DEFAULT, x.revealTemplate)
        assertEquals(ConnectionSource.LINK, x.source)
        assertNull("only https avatars", x.avatarUrl)
        assertTrue("invalid identity row skipped", real().none { it.displayName == "Bad" })
    }

    // ------------------------------------------------------------------ the real Reveal path

    @Test
    fun connectOnTheRevealPersistsAcrossRestart() {
        KipatTapLauncher.deliverAsResponder(context, ana, ByteArray(16), ByteArray(16) { 1 }, 120)
        val intent = shadowOf(context as android.app.Application).nextStartedActivity
        compose.mainClock.autoAdvance = false
        ActivityScenario.launch<TapExperienceActivity>(intent).use {
            compose.mainClock.advanceTimeBy(3_000)
            compose.onNodeWithText("Connect").performClick()
            compose.mainClock.advanceTimeBy(500)
        }
        recreate()
        val back = stored()
        assertNotNull("Connect on the Reveal survives a restart", back)
        assertEquals(ConnectionSource.PHONE_TAP, back!!.source)
    }
}
