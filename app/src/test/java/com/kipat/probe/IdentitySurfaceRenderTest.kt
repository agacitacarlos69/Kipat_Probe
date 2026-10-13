package com.kipat.probe

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.unit.dp
import com.kipat.probe.reveal.KipatIdentitySurface
import com.kipat.probe.reveal.RevealPayload
import com.kipat.probe.reveal.RevealPresentation
import com.kipat.probe.reveal.RevealTemplateId
import com.kipat.probe.reveal.RevealTheme
import com.kipat.probe.reveal.SurfaceActions
import com.kipat.probe.ui.preview.SurfaceSamples
import com.kipat.probe.ui.theme.KipatTheme
import com.kipat.probe.ui.theme.RevealPalette
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The finished identity surface without any animation, across hard content and every trust state.
 * Asserts that identity and actions stay on screen; the PNGs are for visual review.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IdentitySurfaceRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private fun render(name: String, payload: RevealPayload, template: RevealTemplateId = RevealTemplateId.DEFAULT, connected: Boolean = false) {
        compose.setContent {
            KipatTheme {
                val palette = RevealPalette.of(RevealTheme.NIGHT, isSystemInDarkTheme())
                Box(Modifier.fillMaxWidth().background(palette.scrim.copy(alpha = palette.scrimPeak)).padding(20.dp)) {
                    KipatIdentitySurface(
                        payload = payload,
                        presentation = RevealPresentation.of(template, RevealTheme.NIGHT),
                        palette = palette,
                        actions = SurfaceActions(connected, { true }, {}, {}, {}, {}),
                        onDismiss = {},
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText(payload.displayName).assertIsDisplayed()
        compose.onNodeWithContentDescription("Close").assertIsDisplayed()
        if (System.getProperty("kipat.screenshots") == "true") {
            val dir = File(System.getProperty("user.dir"), "build/outputs/kipat-screenshots/surface").apply { mkdirs() }
            val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
            File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test @Config(qualifiers = "w411dp-h891dp-notnight-xxhdpi")
    fun verifiedDemo() = render("light-1-verified-demo", SurfaceSamples.demo).also {
        // The demo chip is part of the trust row's single accessible description.
        compose.onNodeWithContentDescription("Authenticity: Verified card. Sample data", substring = true).assertIsDisplayed()
    }

    @Test @Config(qualifiers = "w411dp-h891dp-notnight-xxhdpi")
    fun longNameWrapsAndKeepsActions() = render("light-2-long-name", SurfaceSamples.longName).also { compose.onNodeWithText("Connect").assertIsDisplayed() }

    @Test @Config(qualifiers = "w320dp-h640dp-notnight-xhdpi")
    fun longNameOnSmallPhoneKeepsActions() = render("light-3-long-name-320dp", SurfaceSamples.longName).also { compose.onNodeWithText("Save contact").assertIsDisplayed() }

    @Test @Config(qualifiers = "w411dp-h891dp-notnight-xxhdpi")
    fun noRoleOrOrganisation() = render("light-4-no-context", SurfaceSamples.noContext)

    @Test @Config(qualifiers = "w411dp-h891dp-notnight-xxhdpi")
    fun companyIdentity() = render("light-5-company", SurfaceSamples.company, RevealTemplateId.LINE_ASSEMBLY)

    @Test @Config(qualifiers = "w411dp-h891dp-notnight-xxhdpi")
    fun checking() = render("light-6-checking", SurfaceSamples.checking)

    @Test @Config(qualifiers = "w411dp-h891dp-notnight-xxhdpi")
    fun unverified() = render("light-7-unverified", SurfaceSamples.unverified)

    @Test @Config(qualifiers = "w411dp-h891dp-notnight-xxhdpi")
    fun revokedDisablesConnectAndExplains() = render("light-8-revoked", SurfaceSamples.revoked).also {
        compose.onNodeWithText("Connect").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Authenticity: Card revoked", substring = true).assertIsDisplayed()
    }

    @Test @Config(qualifiers = "w411dp-h891dp-notnight-xxhdpi")
    fun couldNotVerify() = render("light-9-error", SurfaceSamples.error)

    @Test @Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun connectedDark() = render("dark-1-connected-orbit", SurfaceSamples.connected, RevealTemplateId.PRECISION_ORBIT, connected = true).also {
        compose.onNodeWithText("Open profile").assertIsDisplayed()
    }

    @Test @Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun editorialDark() = render("dark-2-editorial", SurfaceSamples.demo, RevealTemplateId.APERTURE)

    @Test @Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun companyDark() = render("dark-3-company", SurfaceSamples.company, RevealTemplateId.LINE_ASSEMBLY)
}
