package com.kipat.probe

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.nfc.NfcAdapter
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.kipat.probe.data.KipatRepository
import com.kipat.probe.reveal.RevealTemplateId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * JVM smoke tests (Robolectric). They prove manifest routing, cold launch without crashing, the
 * Reveal rendering dynamic content, dismissal and tab navigation. They are NOT a substitute for
 * the adb/physical-device checks documented in docs/reveal.md — no real NFC stack or window
 * compositor is involved, so backdrop translucency cannot be judged here.
 *
 * Set -Dkipat.screenshots=true to also write PNGs to app/build/outputs/kipat-screenshots/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
class AppSmokeTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun debugDeepLinkResolvesToRevealActivity() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("kipatprobe://tap/TEST123")).addCategory(Intent.CATEGORY_BROWSABLE)
        val matches = context.packageManager.queryIntentActivities(intent, 0).map { it.activityInfo.name }
        assertEquals(listOf(TapExperienceActivity::class.java.name), matches)
    }

    @Test
    fun cardAndLegacyProbeTagsResolveToRevealActivity() {
        listOf("https://kipat.app/c/KPT-DEMO-0001", "https://probe.kipat.com").forEach { uri ->
            val intent = Intent(NfcAdapter.ACTION_NDEF_DISCOVERED, Uri.parse(uri))
            val matches = context.packageManager.queryIntentActivities(intent, 0).map { it.activityInfo.name }
            assertEquals(uri, listOf(TapExperienceActivity::class.java.name), matches)
        }
    }

    @Test
    fun deepLinkColdLaunchShowsRevealAndDismisses() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("kipatprobe://tap/TEST123"))
            .setClass(context, TapExperienceActivity::class.java)
        ActivityScenario.launch<TapExperienceActivity>(intent).use { scenario ->
            compose.mainClock.advanceTimeBy(2_500)
            compose.onNodeWithText("Kipat card").assertExists()
            compose.onNodeWithText("Card TEST123").assertExists()
            compose.onNodeWithContentDescription("Authenticity: Not verified", substring = true).assertExists()
            screenshot("reveal-unresolved")
            var activity: TapExperienceActivity? = null
            scenario.onActivity { activity = it }
            compose.onNodeWithText("Done").assertExists() // unresolved card: nothing to connect or save
            compose.onNodeWithText("Connect").assertDoesNotExist()
            compose.onNodeWithContentDescription("Close").performClick()
            compose.waitForIdle()
            assertTrue("dismiss must finish the Reveal", activity!!.isFinishing)
        }
    }

    @Test
    fun ownCardRevealUsesDynamicIdentityAndLabelsDemo() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("kipatprobe://tap/demo"))
            .setClass(context, TapExperienceActivity::class.java)
        compose.mainClock.autoAdvance = false
        ActivityScenario.launch<TapExperienceActivity>(intent).use {
            // Step through the default choreography with a manual clock.
            listOf(120L, 400L, 600L, 900L, 1_150L).fold(0L) { prev, at ->
                compose.mainClock.advanceTimeBy(at - prev)
                screenshot("reveal-default-${at}ms")
                at
            }
            compose.mainClock.advanceTimeBy(2_000)
            compose.onNodeWithText("Vash Puno").assertExists()
            compose.onNodeWithText("BS Computer Science").assertExists()
            compose.onNodeWithContentDescription("Authenticity: Verified card. Sample data", substring = true).assertExists()
            compose.onNodeWithText("Connect").assertExists()
            compose.onNodeWithText("Save contact").assertExists()
            screenshot("reveal-default-settled")
        }
    }

    @Test
    fun malformedLaunchDoesNotCrash() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("kipatprobe://tap/"))
            .setClass(context, TapExperienceActivity::class.java)
            .putExtra("kipat.reveal.name", "Spoofed Person") // no nonce → must be ignored
            .putExtra("kipat.reveal.auth", "authentic")
        ActivityScenario.launch<TapExperienceActivity>(intent).use {
            compose.mainClock.advanceTimeBy(2_500)
            compose.onNodeWithText("Kipat card").assertExists()
            compose.onNodeWithText("Spoofed Person").assertDoesNotExist()
        }
    }

    @Test
    fun threeTabsShareAndProfileFlowsWork() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // Home: identity + Share as the one primary action; no Touch tab any more.
            compose.onNodeWithText("Vash Puno").assertExists()
            compose.onNodeWithText("Touch", useUnmergedTree = true).assertDoesNotExist()
            screenshot("home")
            compose.onNodeWithText("Share").performClick()
            compose.onNodeWithText("Copy link").assertExists()
            compose.onNodeWithText("Scan to open Vash's Kipat").assertExists()
            compose.mainClock.advanceTimeBy(500)
            screenshot("share")
            compose.onNodeWithContentDescription("Back").performClick()

            compose.onNodeWithText("Connections", useUnmergedTree = true).performClick()
            compose.onNodeWithText("Search people").assertExists()
            screenshot("connections")
            compose.onNodeWithText("Alyssa Ramos").performClick()
            compose.onNodeWithText("Save contact").assertExists()
            compose.onNodeWithText("Replay Reveal").assertExists()
            screenshot("person")
            compose.onNodeWithContentDescription("Back").performClick()

            compose.onNodeWithText("Profile", useUnmergedTree = true).performClick()
            compose.onNodeWithText("How others see you").assertExists()
            screenshot("profile")
            compose.onNodeWithText("Edit profile").performClick()
            compose.onNodeWithText("Profile type").assertExists()
            screenshot("profile-edit")
            compose.onNodeWithContentDescription("Close").performClick() // no changes → closes without asking
            compose.onNodeWithText("Developer tools").performClick()
            compose.onNodeWithText("Write to tag").assertExists()
            compose.onNodeWithText("Not present").assertExists() // Robolectric has no NFC adapter
            screenshot("developer")
            compose.onNodeWithContentDescription("Back").performClick()

            compose.onNodeWithText("Home", useUnmergedTree = true).performClick()
            compose.onNodeWithText("Vash Puno").assertExists()
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-notnight-xxhdpi")
    fun revealStylePickerPreviewsBeforeApplying() {
        val original = KipatRepository.identity.value
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                compose.onNodeWithText("Profile", useUnmergedTree = true).performClick()
                compose.onNodeWithText("Reveal style").performClick()
                compose.onNodeWithText("Precision Orbit").assertExists()
                compose.onNodeWithText("Minimal Fade").assertExists()
                compose.onNodeWithText("Applied").assertIsNotEnabled()
                compose.onNodeWithText("Aperture").performClick()
                // Selecting is not applying: others still see the old style.
                assertEquals(original.revealTemplate, KipatRepository.identity.value.revealTemplate)
                screenshot("light-reveal-style")
                compose.onNodeWithText("Apply").assertIsEnabled().performClick()
                assertEquals(RevealTemplateId.APERTURE, KipatRepository.identity.value.revealTemplate)
                compose.onNodeWithText("Applied").assertIsNotEnabled()
            }
        } finally {
            KipatRepository.publishIdentity(original)
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-notnight-xxhdpi", fontScale = 1.3f)
    fun revealStylePickerWorksAtLargeFontScale() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("Profile", useUnmergedTree = true).performClick()
            compose.onNodeWithText("Reveal style").performClick()
            compose.onNodeWithText("Architectural precision").assertExists()
            compose.onNodeWithText("Preview").assertExists()
            screenshot("light-reveal-style-font130")
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-notnight-xxhdpi")
    fun lightThemeRenders() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("Vash Puno").assertExists()
            screenshot("light-home")
            compose.onNodeWithText("Connections", useUnmergedTree = true).performClick()
            screenshot("light-connections")
            compose.onNodeWithText("Profile", useUnmergedTree = true).performClick()
            screenshot("light-profile")
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("kipatprobe://tap/demo")).setClass(context, TapExperienceActivity::class.java)
        compose.mainClock.autoAdvance = false
        ActivityScenario.launch<TapExperienceActivity>(intent).use {
            listOf(120L, 400L, 600L, 900L, 1_150L).fold(0L) { prev, at ->
                compose.mainClock.advanceTimeBy(at - prev)
                screenshot("light-reveal-${at}ms")
                at
            }
            compose.mainClock.advanceTimeBy(1_000)
            screenshot("light-reveal-settled")
        }
    }

    private fun screenshot(name: String) {
        if (System.getProperty("kipat.screenshots") != "true") return
        val dir = File(System.getProperty("user.dir"), "build/outputs/kipat-screenshots").apply { mkdirs() }
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
