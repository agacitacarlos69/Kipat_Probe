package com.kipat.probe.ui.preview

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kipat.probe.model.AuthenticityState
import com.kipat.probe.reveal.KipatIdentitySurface
import com.kipat.probe.reveal.RevealPayload
import com.kipat.probe.reveal.RevealPresentation
import com.kipat.probe.reveal.RevealSource
import com.kipat.probe.reveal.RevealTemplateId
import com.kipat.probe.reveal.RevealTheme
import com.kipat.probe.reveal.SurfaceActions
import com.kipat.probe.ui.theme.KipatTheme
import com.kipat.probe.ui.theme.RevealPalette

/** Sample identities for surface previews and the surface render test. */
object SurfaceSamples {
    private val base = RevealPayload.unresolved("KPT-DEMO-0001", RevealSource.NFC_TAG).copy(
        displayName = "Vash Puno",
        subtitle = "BS Computer Science",
        organization = "Polytechnic University of the Philippines",
        initials = "VP",
        authenticity = AuthenticityState.AUTHENTIC,
        connectable = true,
        primaryCta = "Connect",
    )
    val verified = base
    val demo = base.copy(isDemo = true)
    val longName = base.copy(
        displayName = "Alexandra Marie Villanueva-Santiago",
        initials = "AV",
        subtitle = "Senior Product Designer, Platform and Design Systems",
        organization = "Polytechnic University of the Philippines",
    )
    val noContext = base.copy(displayName = "Kim Aquino", initials = "KA", subtitle = null, organization = null, authenticity = AuthenticityState.UNVERIFIED)
    val company = base.copy(
        displayName = "Globe Labs",
        initials = "GL",
        subtitle = "Product and engineering studio",
        organization = "Taguig, Philippines",
        isOrganization = true,
    )
    val checking = base.copy(authenticity = AuthenticityState.CHECKING)
    val unverified = base.copy(displayName = "Jonas Reyes", initials = "JR", subtitle = "BS Architecture", organization = "UST", authenticity = AuthenticityState.UNVERIFIED)
    val revoked = base.copy(displayName = "Rafael Lim", initials = "RL", subtitle = "Creator", organization = "Studio Lim", authenticity = AuthenticityState.REVOKED)
    val error = base.copy(authenticity = AuthenticityState.ERROR)
    val connected = base.copy(source = RevealSource.REPLAY)
}

private val NoActions = SurfaceActions(false, { true }, {}, {}, {}, {})

@Composable
private fun Surface(payload: RevealPayload, template: RevealTemplateId = RevealTemplateId.DEFAULT, connected: Boolean = false) = KipatTheme {
    val dark = isSystemInDarkTheme()
    val palette = RevealPalette.of(RevealTheme.NIGHT, dark)
    Column(Modifier.background(palette.scrim.copy(alpha = palette.scrimPeak)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        KipatIdentitySurface(
            payload = payload,
            presentation = RevealPresentation.of(template, RevealTheme.NIGHT),
            palette = palette,
            actions = NoActions.copy(connected = connected),
            onDismiss = {},
        )
    }
}

@Preview(name = "Compact / light", widthDp = 380)
@Preview(name = "Compact / dark", widthDp = 380, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CompactPreview() = Surface(SurfaceSamples.demo, RevealTemplateId.CONTACT_RIPPLE)

@Preview(name = "Profile header / light", widthDp = 380)
@Preview(name = "Profile header / dark", widthDp = 380, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileHeaderPreview() = Surface(SurfaceSamples.verified, RevealTemplateId.PRECISION_ORBIT)

@Preview(name = "Editorial / light", widthDp = 380)
@Preview(name = "Editorial / dark", widthDp = 380, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EditorialPreview() = Surface(SurfaceSamples.verified, RevealTemplateId.APERTURE)

@Preview(name = "Long name", widthDp = 380)
@Composable
private fun LongNamePreview() = Surface(SurfaceSamples.longName)

@Preview(name = "Long name / 320 dp phone", widthDp = 320)
@Composable
private fun LongNameSmallPreview() = Surface(SurfaceSamples.longName)

@Preview(name = "No avatar, no role or organisation", widthDp = 380)
@Composable
private fun NoContextPreview() = Surface(SurfaceSamples.noContext)

@Preview(name = "Company identity", widthDp = 380)
@Composable
private fun CompanyPreview() = Surface(SurfaceSamples.company, RevealTemplateId.LINE_ASSEMBLY)

@Preview(name = "Verified", widthDp = 380)
@Composable
private fun VerifiedPreview() = Surface(SurfaceSamples.verified)

@Preview(name = "Checking", widthDp = 380)
@Composable
private fun CheckingPreview() = Surface(SurfaceSamples.checking)

@Preview(name = "Unverified", widthDp = 380)
@Composable
private fun UnverifiedPreview() = Surface(SurfaceSamples.unverified)

@Preview(name = "Revoked", widthDp = 380)
@Composable
private fun RevokedPreview() = Surface(SurfaceSamples.revoked)

@Preview(name = "Couldn't verify", widthDp = 380)
@Composable
private fun ErrorPreview() = Surface(SurfaceSamples.error)

@Preview(name = "Connected", widthDp = 380, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ConnectedPreview() = Surface(SurfaceSamples.connected, connected = true)
