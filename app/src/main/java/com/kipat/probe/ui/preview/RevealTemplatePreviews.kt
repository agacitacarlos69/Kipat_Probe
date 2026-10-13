package com.kipat.probe.ui.preview

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.kipat.probe.model.AuthenticityState
import com.kipat.probe.reveal.RevealChoreography
import com.kipat.probe.reveal.RevealPayload
import com.kipat.probe.reveal.RevealSource
import com.kipat.probe.reveal.RevealStage
import com.kipat.probe.reveal.RevealTemplateGlyph
import com.kipat.probe.reveal.RevealTemplateId
import com.kipat.probe.reveal.effects.RevealEffects
import com.kipat.probe.ui.theme.KipatTheme

// One light and one dark preview per Reveal template, frozen at the template's key frame
// (the moment it is recognisable by; see RevealEffects.keyframeMs). Static: no clock runs.

private val SamplePayload = RevealPayload.unresolved("KPT-DEMO-0001", RevealSource.NFC_TAG).copy(
    displayName = "Vash Puno",
    subtitle = "BS Computer Science",
    organization = "Polytechnic University of the Philippines",
    initials = "VP",
    authenticity = AuthenticityState.AUTHENTIC,
    connectable = true,
    primaryCta = "Connect",
)

@Composable
private fun TemplateFrame(template: RevealTemplateId, atMs: Long = RevealEffects.keyframeMs(template)) = KipatTheme {
    RevealStage(
        payload = SamplePayload.copy(template = template),
        choreography = RevealChoreography.forTemplate(template),
        elapsedMs = { atMs },
        connected = false,
        onConnect = {}, onSaveContact = {}, onOpenProfile = {}, onDismiss = {},
    )
}

@Preview(name = "Contact Ripple — light", widthDp = 360, heightDp = 640)
@Preview(name = "Contact Ripple — dark", widthDp = 360, heightDp = 640, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ContactRipplePreview() = TemplateFrame(RevealTemplateId.CONTACT_RIPPLE)

@Preview(name = "Precision Orbit — light", widthDp = 360, heightDp = 640)
@Preview(name = "Precision Orbit — dark", widthDp = 360, heightDp = 640, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PrecisionOrbitPreview() = TemplateFrame(RevealTemplateId.PRECISION_ORBIT)

@Preview(name = "Line Assembly — light", widthDp = 360, heightDp = 640)
@Preview(name = "Line Assembly — dark", widthDp = 360, heightDp = 640, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LineAssemblyPreview() = TemplateFrame(RevealTemplateId.LINE_ASSEMBLY)

@Preview(name = "Aperture — light", widthDp = 360, heightDp = 640)
@Preview(name = "Aperture — dark", widthDp = 360, heightDp = 640, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AperturePreview() = TemplateFrame(RevealTemplateId.APERTURE)

@Preview(name = "Spatial Card — light", widthDp = 360, heightDp = 640)
@Preview(name = "Spatial Card — dark", widthDp = 360, heightDp = 640, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SpatialCardPreview() = TemplateFrame(RevealTemplateId.SPATIAL_CARD, atMs = 120)

@Preview(name = "Minimal Fade — light", widthDp = 360, heightDp = 640)
@Preview(name = "Minimal Fade — dark", widthDp = 360, heightDp = 640, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MinimalFadePreview() = TemplateFrame(RevealTemplateId.MINIMAL_FADE)

@Preview(name = "Settled card (all templates) — light", widthDp = 360, heightDp = 640)
@Preview(name = "Settled card (all templates) — dark", widthDp = 360, heightDp = 640, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettledPreview() = TemplateFrame(RevealTemplateId.DEFAULT, atMs = 5_000)

@Preview(name = "Picker glyphs — light", widthDp = 120)
@Preview(name = "Picker glyphs — dark", widthDp = 120, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GlyphsPreview() = KipatTheme {
    Column { RevealTemplateId.entries.forEach { RevealTemplateGlyph(it) } }
}
