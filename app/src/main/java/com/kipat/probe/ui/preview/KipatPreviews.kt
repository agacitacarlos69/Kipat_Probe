package com.kipat.probe.ui.preview

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kipat.probe.model.AuthenticityState
import com.kipat.probe.ui.components.ActionRow
import com.kipat.probe.ui.components.IdentityBlock
import com.kipat.probe.ui.components.KipatDivider
import com.kipat.probe.ui.components.KipatPrimaryButton
import com.kipat.probe.ui.components.KipatSecondaryButton
import com.kipat.probe.ui.components.KipatSymbols
import com.kipat.probe.ui.components.PersonRow
import com.kipat.probe.ui.components.RecipientCard
import com.kipat.probe.ui.components.SectionHeading
import com.kipat.probe.ui.theme.Kipat
import com.kipat.probe.ui.theme.KipatSpacing
import com.kipat.probe.ui.theme.KipatTheme

// Design-review previews with realistic content (no repository/ViewModel dependencies).

@Composable
private fun Frame(content: @Composable () -> Unit) {
    KipatTheme {
        Column(Modifier.background(Kipat.colors.background).padding(vertical = KipatSpacing.s16)) { content() }
    }
}

@Preview(name = "Identity — light", widthDp = 360)
@Preview(name = "Identity — dark", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun IdentityPreview() = Frame {
    IdentityBlock(
        displayName = "Vash Puno",
        subtitle = "BS Computer Science",
        organization = "Polytechnic University of the Philippines",
        initials = "VP",
        authenticity = AuthenticityState.AUTHENTIC,
        isDemo = false,
        modifier = Modifier.padding(horizontal = KipatSpacing.gutter),
    )
}

@Preview(name = "Person rows", widthDp = 360)
@Composable
private fun PersonRowsPreview() = Frame {
    SectionHeading("Recent")
    PersonRow("Ana Reyes", "Product Designer, Acme", AuthenticityState.AUTHENTIC, isDemo = false, onClick = {}, trailingText = "1d")
    PersonRow("Bea Villanueva", "Product Manager, Maya", AuthenticityState.UNVERIFIED, isDemo = false, onClick = {}, trailingText = "2d")
    PersonRow("Rafael Lim", "Creator, Studio Lim", AuthenticityState.REVOKED, isDemo = false, onClick = {}, pinned = true, trailingText = "1w")
}

@Preview(name = "Share methods", widthDp = 360)
@Composable
private fun ShareMethodsPreview() = Frame {
    ActionRow("Copy link", icon = KipatSymbols.Copy, onClick = {})
    KipatDivider()
    ActionRow("Kipat Card", icon = KipatSymbols.Nfc, supporting = "Hold your card to the back of their phone", value = "Active", valueColor = Kipat.colors.verified)
    KipatDivider()
    ActionRow("Share with another Kipat", icon = KipatSymbols.Contactless, supporting = "Touch phones to swap profiles", chevron = true)
}

@Preview(name = "Profile section", widthDp = 360)
@Composable
private fun ProfileSectionPreview() = Frame {
    ActionRow("Reveal style", value = "Signature", chevron = true, onClick = {})
    KipatDivider()
    SectionHeading("Details")
    ActionRow("GitHub", value = "vashpuno")
}

@Preview(name = "Reveal card — settled, light", widthDp = 360)
@Preview(name = "Reveal card — settled, dark", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RevealCardPreview() = Frame {
    RecipientCard(Modifier.padding(horizontal = KipatSpacing.s24)) {
        Column {
            IdentityBlock(
                displayName = "Vash Puno",
                subtitle = "Developer",
                organization = "Polytechnic University of the Philippines",
                initials = "VP",
                authenticity = AuthenticityState.AUTHENTIC,
                isDemo = false,
                avatarSize = 56.dp,
            )
            Spacer(Modifier.height(KipatSpacing.s24))
            KipatPrimaryButton("Connect", icon = KipatSymbols.PersonAdd, onClick = {})
            Spacer(Modifier.height(KipatSpacing.s12))
            KipatSecondaryButton("Save contact", onClick = {})
        }
    }
}
