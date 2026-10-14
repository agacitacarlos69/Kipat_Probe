package com.kipat.probe

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.kipat.probe.nfc.profile.NfcProfileMode
import com.kipat.probe.nfc.profile.NfcProfilePayload
import com.kipat.probe.ui.screens.developer.NFC_PROFILE_PRIVACY_NOTE
import com.kipat.probe.ui.screens.developer.NfcProfileEditorScreen
import com.kipat.probe.ui.screens.developer.NfcProfileLab
import com.kipat.probe.ui.screens.developer.NfcProfileTestScreen
import com.kipat.probe.ui.theme.KipatTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Developer tools → NFC full profile test, off-device (Robolectric has no NFC adapter). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-notnight-xxhdpi")
class NfcProfileScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun reset() {
        NfcProfileLab.draft.value = null
        NfcProfileLab.mode.value = NfcProfileMode.FULL
        NfcProfileLab.includeAar.value = true
    }

    @Test
    fun loadSampleShowsSizesAndWarnsAboutPrivacy() {
        compose.setContent { KipatTheme { NfcProfileTestScreen(onBack = {}, onEdit = {}, onCreate = {}) } }
        compose.onNodeWithText(NFC_PROFILE_PRIVACY_NOTE).assertIsDisplayed()
        compose.onNodeWithText("Write profile card").assertIsNotEnabled()
        compose.onNodeWithText("Load sample profile").performClick()
        assertEquals(NfcProfilePayload.SAMPLE, NfcProfileLab.draft.value)
        compose.onNodeWithText("Write profile card").assertIsEnabled()
        screenshot("nfc-profile-tool-sample")
        compose.onNodeWithText("NTAG213").performScrollTo()
        compose.onAllNodes(hasText("Too large")).onFirst().assertIsDisplayed()
        compose.onNodeWithText("NTAG215").performScrollTo().assertIsDisplayed()
        screenshot("nfc-profile-tool-size")
    }

    @Test
    fun readingWithoutNfcHardwareSaysSo() {
        compose.setContent { KipatTheme { NfcProfileTestScreen(onBack = {}, onEdit = {}, onCreate = {}) } }
        compose.onNodeWithText("Read profile card").performClick()
        compose.onNodeWithText("This device has no NFC hardware", ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun editorRequiresANameAndSaves() {
        var done = false
        compose.setContent { KipatTheme { NfcProfileEditorScreen(startBlank = true, onDone = { done = true }) } }
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("José Dela Cruz")
        screenshot("nfc-profile-editor")
        compose.onNodeWithText("Save").assertIsEnabled().performClick()
        assertEquals(true, done)
        assertEquals("José Dela Cruz", NfcProfileLab.draft.value?.displayName)
    }

    private fun screenshot(name: String) {
        if (System.getProperty("kipat.screenshots") != "true") return
        val dir = java.io.File(System.getProperty("user.dir"), "build/outputs/kipat-screenshots").apply { mkdirs() }
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        java.io.File(dir, "$name.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
