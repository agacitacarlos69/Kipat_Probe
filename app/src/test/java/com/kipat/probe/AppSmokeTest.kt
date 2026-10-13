package com.kipat.probe

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.nfc.NfcAdapter
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
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
            compose.onNodeWithContentDescription("Dismiss Reveal").performClick()
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
            // Step through the choreography with a manual clock: ripple → particles → card → content.
            listOf(150L, 350L, 700L, 1_050L, 1_600L).fold(0L) { prev, at ->
                compose.mainClock.advanceTimeBy(at - prev)
                screenshot("reveal-signature-${at}ms")
                at
            }
            compose.mainClock.advanceTimeBy(2_000)
            compose.onNodeWithText("Vash Puno").assertExists()
            compose.onNodeWithText("BS Computer Science").assertExists()
            compose.onNodeWithContentDescription("Authenticity: Verified · demo", substring = true).assertExists()
            screenshot("reveal-signature-settled")
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
    fun tabsNavigateAndKeepWorking() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("Preview Reveal").assertExists()
            screenshot("home")
            compose.onNodeWithText("Connections", useUnmergedTree = true).performClick()
            compose.onNodeWithText("Search name, role or organization").assertExists()
            screenshot("connections")
            compose.onNodeWithText("Alyssa Ramos").performClick()
            compose.onNodeWithText("Replay Reveal").assertExists()
            screenshot("connection-sheet")
            compose.onNodeWithText("Remove Connection").performClick()
            compose.onNodeWithText("Touch", useUnmergedTree = true).performClick()
            compose.onNodeWithText("Prototype Card Tools".uppercase()).assertExists()
            compose.onNodeWithText("No NFC hardware").assertExists() // Robolectric has no NFC adapter
            screenshot("touch")
            compose.onNodeWithText("Profile", useUnmergedTree = true).performClick()
            compose.onNodeWithText("Identity type".uppercase()).assertExists()
            screenshot("profile")
            compose.onNodeWithText("Home", useUnmergedTree = true).performClick()
            compose.onNodeWithText("Preview Reveal").assertExists()
        }
    }

    private fun screenshot(name: String) {
        if (System.getProperty("kipat.screenshots") != "true") return
        val dir = File(System.getProperty("user.dir"), "build/outputs/kipat-screenshots").apply { mkdirs() }
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
