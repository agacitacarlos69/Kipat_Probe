package com.kipat.probe

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.kipat.probe.data.KipatRepository
import com.kipat.probe.data.toRevealPayload
import com.kipat.probe.model.AuthenticityState
import com.kipat.probe.reveal.RevealChoreography
import com.kipat.probe.reveal.RevealQuality
import com.kipat.probe.reveal.RevealSource
import com.kipat.probe.reveal.RevealStage
import com.kipat.probe.reveal.RevealTemplateId
import com.kipat.probe.ui.theme.KipatTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders every Reveal template at fixed moments in light and dark, and checks the identity is
 * complete once settled. Aesthetics are judged from the PNGs (-Pkipat.screenshots=true), not here.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RevealTemplateGalleryTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(qualifiers = "w411dp-h891dp-notnight-xxhdpi")
    fun everyTemplateSettlesWithFullIdentityInLightTheme() = renderAll("light", RevealQuality.HIGH)

    @Test
    @Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun everyTemplateSettlesWithFullIdentityInDarkTheme() = renderAll("dark", RevealQuality.HIGH)

    @Test
    @Config(qualifiers = "w411dp-h891dp-notnight-xxhdpi")
    fun reducedMotionIsReadableOnItsFirstFrames() = renderAll("reduced", RevealQuality.REDUCED_MOTION, frames = listOf(0L, 60L))

    private fun renderAll(tag: String, quality: RevealQuality, frames: List<Long> = listOf(100L, 250L, 450L, 700L)) {
        var template by androidx.compose.runtime.mutableStateOf(RevealTemplateId.DEFAULT)
        var ms by mutableLongStateOf(0L)
        val identity = KipatRepository.identity.value
        compose.setContent {
            KipatTheme {
                val payload = identity.copy(revealTemplate = template)
                    .toRevealPayload(RevealSource.NFC_TAG, isDemo = true, authenticity = AuthenticityState.AUTHENTIC)
                RevealStage(
                    payload = payload,
                    choreography = RevealChoreography.forTemplate(template, quality),
                    elapsedMs = { ms },
                    connected = false,
                    onConnect = {}, onSaveContact = {}, onOpenProfile = {}, onDismiss = {},
                )
            }
        }
        RevealTemplateId.entries.forEach { t ->
            template = t
            val total = RevealChoreography.forTemplate(t, quality).timeline.totalDuration.toLong()
            frames.forEach { at ->
                ms = at
                compose.waitForIdle()
                screenshot("$tag-${t.wireId}-${at}ms")
            }
            ms = total
            compose.waitForIdle()
            screenshot("$tag-${t.wireId}-settled")
            compose.onNodeWithText(identity.displayName).assertExists()
            compose.onNodeWithContentDescription("Authenticity: Verified card", substring = true).assertExists()
            // Templates switch in sequence here, as on a second tap: actions must follow the new clock.
            compose.onNodeWithText("Connect").assertIsEnabled()
            compose.onNodeWithText("Save contact").assertExists()
        }
    }

    private fun screenshot(name: String) {
        if (System.getProperty("kipat.screenshots") != "true") return
        val dir = File(System.getProperty("user.dir"), "build/outputs/kipat-screenshots/templates").apply { mkdirs() }
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
