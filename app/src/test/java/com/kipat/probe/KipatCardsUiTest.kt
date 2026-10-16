package com.kipat.probe

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.kipat.probe.cards.KipatCardId
import com.kipat.probe.cards.KipatCards
import com.kipat.probe.cards.LinkedCardStatus
import com.kipat.probe.cards.LinkedKipatCard
import com.kipat.probe.data.KipatIdentityStore
import com.kipat.probe.data.KipatRepository
import com.kipat.probe.data.SampleData
import com.kipat.probe.ui.screens.developer.isOutOfDate
import com.kipat.probe.nfc.peer.KipatPeerProfile
import com.kipat.probe.reveal.RevealTemplateId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Profile → Kipat cards, card detail and the add-card flow, plus the identity-wide copy. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-notnight-xxhdpi")
class KipatCardsUiTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun reset() {
        listOf("kipat_identity.json", "kipat_cards.json").forEach { File(context.filesDir, it).delete() }
        KipatIdentityStore.resetForTest()
        KipatCards.resetForTest()
        KipatRepository.restoreIdentity(SampleData.identity, 0)
    }

    @After
    fun tearDown() = reset()

    private fun openCards() {
        compose.onNodeWithText("Profile", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Kipat cards").performScrollTo().performClick()
    }

    @Test
    fun emptyListThenAddCardExplainsWhatTheCardStores() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("Not linked").assertExists() // Home row: no fake "Active" card
            openCards()
            compose.onNodeWithText("No cards linked yet").assertExists()
            compose.onNodeWithText("Your cards show your current Kipat profile and Reveal. Changes apply everywhere you share this Kipat identity.").assertExists()
            screenshot("cards-empty")
            compose.onNodeWithText("Add Kipat card").performClick()
            // Robolectric has no NFC adapter: the flow says so instead of waiting for a card.
            compose.onNodeWithText("This phone doesn't have NFC").assertExists()
        }
    }

    @Test
    fun cardDetailHasNoPerCardRevealAndCanBeDisabled() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val card = LinkedKipatCard("kc_0123456789abb871", KipatRepository.identity.value.id, "Kipat card", LinkedCardStatus.ACTIVE, 1L, lastSyncedAt = 1L)
            KipatCards.add(card)
            openCards()
            compose.onNodeWithText("••••B871").assertExists()
            screenshot("cards-list")
            compose.onNodeWithText("Kipat card").performClick()
            compose.onNodeWithText("Uses your current Kipat profile").assertExists()
            compose.onNodeWithText(KipatRepository.identity.value.revealTemplate.label).assertExists()
            compose.onNodeWithText("Unverified").assertExists()
            assertEquals("no per-card Reveal choice", 0, compose.onAllNodesWithText("Change Reveal").fetchSemanticsNodes().size)
            screenshot("card-detail")
            compose.onNodeWithText("Disable card").performScrollTo().performClick()
            assertEquals(LinkedCardStatus.DISABLED, KipatCards.find(card.cardId)!!.status)
            compose.onNodeWithText("Enable card").assertExists()
            // The Reveal row leads to the identity-wide Reveal style screen.
            compose.onNodeWithText("Reveal").performScrollTo().performClick()
            compose.onNodeWithText("Used by your phone and linked Kipat cards").assertExists()
        }
    }

    @Test
    fun profileEditSaysChangesApplyEverywhere() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("Profile", useUnmergedTree = true).performClick()
            compose.onNodeWithText("Edit profile").performClick()
            compose.onNodeWithText("Changes apply everywhere you share this Kipat identity").assertExists()
            assertEquals(0, compose.onAllNodesWithText("Updating NFC card", substring = true).fetchSemanticsNodes().size)
        }
    }

    @Test
    fun offlineCopyGoesOutOfDateWhenTheIdentityChanges() {
        val written = KipatPeerProfile.fromIdentity(SampleData.identity)
        assertFalse(isOutOfDate(written, written))
        val changed = KipatPeerProfile.fromIdentity(SampleData.identity.copy(revealTemplate = RevealTemplateId.APERTURE))
        assertTrue(isOutOfDate(written, changed))
        assertTrue(KipatCardId.isValid("kc_0123456789abb871"))
    }

    private fun screenshot(name: String) {
        if (System.getProperty("kipat.screenshots") != "true") return
        val dir = File(System.getProperty("user.dir"), "build/outputs/kipat-screenshots").apply { mkdirs() }
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
