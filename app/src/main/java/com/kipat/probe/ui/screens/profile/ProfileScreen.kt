package com.kipat.probe.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kipat.probe.data.KipatRepository
import com.kipat.probe.data.toRevealPayload
import com.kipat.probe.model.ProfileField
import com.kipat.probe.model.initialsOf
import com.kipat.probe.reveal.RevealLauncher
import com.kipat.probe.reveal.RevealSource
import com.kipat.probe.ui.components.ActionRow
import com.kipat.probe.ui.components.IdentityBlock
import com.kipat.probe.ui.components.KipatDivider
import com.kipat.probe.ui.components.KipatPrimaryButton
import com.kipat.probe.ui.components.KipatSecondaryButton
import com.kipat.probe.ui.components.KipatSymbols
import com.kipat.probe.ui.components.KipatTopBar
import com.kipat.probe.ui.components.RecipientCard
import com.kipat.probe.ui.components.SectionHeading
import com.kipat.probe.ui.theme.Kipat
import com.kipat.probe.ui.theme.KipatSpacing

/**
 * Profile = view. The card is exactly what a recipient sees (no editing chrome on it).
 * Editing happens on its own screen.
 */
@Composable
fun ProfileScreen(
    onEdit: () -> Unit,
    onRevealStyle: () -> Unit,
    onShare: () -> Unit,
    onDeveloperTools: () -> Unit,
) {
    val context = LocalContext.current
    val identity by KipatRepository.identity.collectAsStateWithLifecycle()
    val card by KipatRepository.card.collectAsStateWithLifecycle()
    val details = identity.publishedFields().filterKeys { it !in setOf(ProfileField.DISPLAY_NAME, ProfileField.PROGRAM, ProfileField.UNIVERSITY, ProfileField.ROLE, ProfileField.COMPANY) }

    Scaffold(
        containerColor = Kipat.colors.background,
        topBar = { KipatTopBar("Profile") },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = KipatSpacing.s32),
        ) {
            item {
                Text(
                    "How others see you",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Kipat.colors.textSecondary,
                    modifier = Modifier.padding(start = KipatSpacing.gutter, end = KipatSpacing.gutter, top = KipatSpacing.s8, bottom = KipatSpacing.s12),
                )
                RecipientCard(Modifier.padding(horizontal = KipatSpacing.gutter)) {
                    IdentityBlock(
                        displayName = identity.displayName,
                        subtitle = identity.subtitle,
                        organization = identity.organization,
                        initials = initialsOf(identity.displayName),
                        authenticity = card.authenticity,
                        isDemo = card.isDemo,
                        avatarSize = 56.dp,
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = KipatSpacing.gutter, vertical = KipatSpacing.s24),
                    horizontalArrangement = Arrangement.spacedBy(KipatSpacing.s12),
                ) {
                    KipatPrimaryButton("Edit profile", icon = KipatSymbols.Edit, onClick = onEdit, modifier = Modifier.weight(1f))
                    KipatSecondaryButton(
                        "Preview",
                        onClick = {
                            RevealLauncher.launch(context, identity.toRevealPayload(RevealSource.PREVIEW, isDemo = card.isDemo, authenticity = card.authenticity))
                        },
                    )
                }
                KipatDivider(inset = false)
            }
            item {
                ActionRow("Reveal style", value = identity.revealTemplate.label, chevron = true, onClick = onRevealStyle)
                KipatDivider()
                ActionRow("Share", value = "QR, link, card", chevron = true, onClick = onShare)
            }
            if (details.isNotEmpty()) {
                item { SectionHeading("Details") }
                items(details.entries.toList(), key = { it.key.name }) { (field, value) ->
                    ActionRow(field.label, value = value)
                }
            }
            item {
                SectionHeading("Prototype")
                ActionRow(
                    "Developer tools",
                    icon = KipatSymbols.DeveloperMode,
                    supporting = "Card writer and diagnostics",
                    chevron = true,
                    onClick = onDeveloperTools,
                )
            }
        }
    }
}
