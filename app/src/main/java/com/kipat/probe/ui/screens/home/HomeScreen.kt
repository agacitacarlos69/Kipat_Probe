package com.kipat.probe.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kipat.probe.data.KipatRepository
import com.kipat.probe.data.toRevealPayload
import com.kipat.probe.model.CardState
import com.kipat.probe.model.initialsOf
import com.kipat.probe.reveal.RevealLauncher
import com.kipat.probe.reveal.RevealSource
import com.kipat.probe.ui.components.ActionRow
import com.kipat.probe.ui.screens.share.KipatTapHomeRow
import com.kipat.probe.ui.components.IdentityBlock
import com.kipat.probe.ui.components.KipatDivider
import com.kipat.probe.ui.components.KipatIcons
import com.kipat.probe.ui.components.KipatPrimaryButton
import com.kipat.probe.ui.components.KipatSecondaryButton
import com.kipat.probe.ui.components.KipatSymbols
import com.kipat.probe.ui.components.KipatTextButton
import com.kipat.probe.ui.components.KipatTopBar
import com.kipat.probe.ui.components.PersonRow
import com.kipat.probe.ui.components.SectionHeading
import com.kipat.probe.ui.components.shortTime
import com.kipat.probe.ui.theme.Kipat
import com.kipat.probe.ui.theme.KipatSpacing
import androidx.compose.material3.MaterialTheme

/**
 * Home's one job: show the identity you're about to share, and share it.
 * Identity sits on the page (no card); Share is the only filled button; then the physical
 * card's state and the few people you just met.
 */
@Composable
fun HomeScreen(
    onShare: () -> Unit,
    onOpenConnections: () -> Unit,
    onOpenPerson: (String) -> Unit,
) {
    val context = LocalContext.current
    val identity by KipatRepository.identity.collectAsStateWithLifecycle()
    val card by KipatRepository.card.collectAsStateWithLifecycle()
    val connections by KipatRepository.connections.collectAsStateWithLifecycle()
    val taps by KipatRepository.recentTaps.collectAsStateWithLifecycle()

    // "Recent": unsaved taps (actionable) first, then the latest connections; four rows at most.
    val unsaved = taps.filterNot { it.savedAsConnection }.take(2)
    val latest = connections.sortedByDescending { it.connectedAtMillis }.take(4 - unsaved.size)

    Scaffold(
        containerColor = Kipat.colors.background,
        topBar = {
            KipatTopBar(
                title = "Kipat",
                titleContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(KipatIcons.Mark, contentDescription = null, tint = Kipat.colors.accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(KipatSpacing.s8))
                        Text("Kipat", style = MaterialTheme.typography.titleLarge)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = KipatSpacing.s32),
        ) {
            item {
                IdentityBlock(
                    displayName = identity.displayName,
                    subtitle = identity.subtitle,
                    organization = identity.organization,
                    initials = initialsOf(identity.displayName),
                    authenticity = card.authenticity,
                    isDemo = card.isDemo,
                    modifier = Modifier.padding(start = KipatSpacing.gutter, end = KipatSpacing.gutter, top = KipatSpacing.s16),
                )
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = KipatSpacing.gutter, vertical = KipatSpacing.s24),
                    horizontalArrangement = Arrangement.spacedBy(KipatSpacing.s12),
                ) {
                    KipatPrimaryButton("Share", onClick = onShare, icon = KipatSymbols.Share, modifier = Modifier.weight(1f))
                    KipatSecondaryButton(
                        "Preview",
                        onClick = {
                            RevealLauncher.launch(context, identity.toRevealPayload(RevealSource.PREVIEW, isDemo = card.isDemo, authenticity = card.authenticity))
                        },
                    )
                }
            }
            item { KipatDivider(inset = false) }
            item {
                ActionRow(
                    label = "Kipat Card",
                    supporting = card.cardId,
                    icon = KipatSymbols.Nfc,
                    value = card.state.label,
                    valueColor = if (card.state == CardState.ACTIVE) Kipat.colors.verified else Kipat.colors.warning,
                    onClick = onShare,
                )
            }
            item { KipatDivider(inset = false) }
            item { KipatTapHomeRow(divider = { KipatDivider(inset = false) }) }

            item { SectionHeading("Recent") }
            if (unsaved.isEmpty() && latest.isEmpty()) {
                item {
                    Text(
                        "People you meet with Kipat appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Kipat.colors.textSecondary,
                        modifier = Modifier.padding(horizontal = KipatSpacing.gutter),
                    )
                }
            }
            items(unsaved, key = { "tap-" + it.id }) { tap ->
                PersonRow(
                    name = tap.displayName,
                    detail = "Tapped ${shortTime(tap.atMillis)} ago, not connected",
                    authenticity = tap.authenticity,
                    isDemo = tap.isDemo,
                    // Replays the Reveal they saw (which offers Connect); never connects silently.
                    onClick = { RevealLauncher.launch(context, tap.toRevealPayload()) },
                    trailing = { KipatTextButton("Connect", onClick = { KipatRepository.connect(tap) }) },
                )
            }
            items(latest, key = { "c-" + it.id }) { c ->
                PersonRow(
                    name = c.displayName,
                    detail = listOf(c.subtitle, c.organization).filter { it.isNotBlank() }.joinToString(", "),
                    authenticity = c.authenticity,
                    isDemo = c.isDemo,
                    pinned = c.pinned,
                    trailingText = shortTime(c.connectedAtMillis),
                    onClick = { onOpenPerson(c.id) },
                )
            }
            item {
                Column(Modifier.padding(horizontal = KipatSpacing.s8)) {
                    Spacer(Modifier.height(KipatSpacing.s4))
                    KipatTextButton("See all connections", onClick = onOpenConnections)
                }
            }
        }
    }
}
