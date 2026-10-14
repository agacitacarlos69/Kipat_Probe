package com.kipat.probe

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.kipat.probe.data.KipatRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Scenarios from the real-device validation plan that can be reproduced off-device. Each test
 * pins an issue found before hardware was available (docs/device-validation/preflight-issues.md).
 * They do not replace the on-device checks.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-notnight-xxhdpi")
class RevealPreflightTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun reveal(uri: String) = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setClass(context, TapExperienceActivity::class.java)

    /**
     * A11Y-1 / A11Y-2: TalkBack sees the identity as separate, ordered nodes — not merged into a
     * full-screen "Dismiss" button — and doesn't read the monogram initials or any Canvas primitive.
     */
    @Test
    fun talkBackSeesIdentityInReadingOrder() {
        val uri = "kipatprobe://tap/KPT-A11Y-1?n=Sam%20Reyes&s=Designer&o=Globe%20Labs&t=orbit"
        compose.mainClock.autoAdvance = false
        ActivityScenario.launch<TapExperienceActivity>(reveal(uri)).use {
            compose.mainClock.advanceTimeBy(3_000)
            val announced = mutableListOf<Pair<Float, String>>()
            fun walk(n: SemanticsNode) {
                val c = n.config
                val text = c.getOrNull(SemanticsProperties.Text)?.joinToString()
                val cd = c.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
                val label = listOfNotNull(cd, text).joinToString(" | ")
                if (label.isNotEmpty()) announced += n.boundsInRoot.top to label
                assertFalse("no node merges the identity into a dismiss action: $label", label.contains("Sam Reyes") && (label.contains("Connect") || c.getOrNull(androidx.compose.ui.semantics.SemanticsActions.OnClick) != null))
                n.children.forEach { walk(it) }
            }
            walk(compose.onRoot().fetchSemanticsNode())
            val labels = announced.sortedBy { it.first }.map { it.second }
            assertFalse("monogram initials are not announced: $labels", labels.any { it == "SR" || it.startsWith("SR,") })
            fun at(part: String) = labels.indexOfFirst { it.contains(part) }.also { assertTrue("$part announced in $labels", it >= 0) }
            val order = listOf(at("Close"), at("Sam Reyes"), at("Designer"), at("Globe Labs"), at("Authenticity:"), at("Connect"), at("Save contact"))
            assertEquals("reading order close → name → role → org → trust → actions: $labels", order.sorted(), order)
        }
    }

    /**
     * FONT-1: at 2.0× on a 320 × 640 dp phone the actions are below the fold but reachable by
     * scrolling the surface (checked geometrically: driving the scroll under a paused test clock
     * doesn't terminate in Robolectric). FONT-2: the trust words keep a usable width.
     */
    @Test
    @Config(qualifiers = "w320dp-h640dp-notnight-xhdpi", fontScale = 2.0f)
    fun actionsReachableAndTrustReadableAtLargestFontOnSmallPhone() {
        val uri = "kipatprobe://tap/KPT-FONT-1?n=Alexandra%20Marie%20Villanueva-Santiago&s=Senior%20Product%20Designer&o=Polytechnic%20University%20of%20the%20Philippines&t=aperture&a=revoked"
        compose.mainClock.autoAdvance = false
        ActivityScenario.launch<TapExperienceActivity>(reveal(uri)).use {
            compose.mainClock.advanceTimeBy(3_000)
            compose.onNodeWithContentDescription("Close").assertIsDisplayed()
            var scroll: SemanticsNode? = null
            fun find(n: SemanticsNode) {
                if (n.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) != null) scroll = n
                n.children.forEach { find(it) }
            }
            find(compose.onRoot(useUnmergedTree = true).fetchSemanticsNode())
            val container = checkNotNull(scroll) { "the surface scrolls when content exceeds the window" }
            val reach = container.boundsInRoot.bottom + container.config[SemanticsProperties.VerticalScrollAxisRange].maxValue()
            listOf("Connect", "Save contact").forEach { label ->
                val node = compose.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode()
                val bottom = node.positionInRoot.y + node.size.height
                assertTrue("$label (bottom $bottom) is reachable within $reach", bottom <= reach + 1f)
            }
            // Before the fix the "Sample data" chip kept its width and squeezed "Card revoked" into
            // three lines; now the chip wraps and the words fit on one line.
            val trust = compose.onNodeWithText("Card revoked", useUnmergedTree = true).fetchSemanticsNode()
            val chip = compose.onNodeWithText("Sample data", useUnmergedTree = true).fetchSemanticsNode()
            assertTrue("chip wrapped below the trust words", chip.positionInRoot.y >= trust.positionInRoot.y + trust.size.height - 1f)
            assertTrue("trust words on one line (height ${trust.size.height} vs chip line ${chip.size.height})", trust.size.height < 2 * chip.size.height)
        }
    }
}
