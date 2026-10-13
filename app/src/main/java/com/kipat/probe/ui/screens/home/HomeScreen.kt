package com.kipat.probe.ui.screens.home

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kipat.probe.data.KipatRepository
import com.kipat.probe.data.toRevealPayload
import com.kipat.probe.model.Identity
import com.kipat.probe.model.StatusTone
import com.kipat.probe.model.initialsOf
import com.kipat.probe.reveal.RevealLauncher
import com.kipat.probe.reveal.RevealSource
import com.kipat.probe.ui.components.KipatAvatar
import com.kipat.probe.ui.components.KipatGroup
import com.kipat.probe.ui.components.KipatGroupDivider
import com.kipat.probe.ui.components.KipatIcons
import com.kipat.probe.ui.components.KipatIdentityCard
import com.kipat.probe.ui.components.KipatInfoRow
import com.kipat.probe.ui.components.KipatPrimaryButton
import com.kipat.probe.ui.components.KipatSecondaryButton
import com.kipat.probe.ui.components.KipatSectionHeader
import com.kipat.probe.ui.components.PersonRow
import com.kipat.probe.ui.components.StatusPill
import com.kipat.probe.ui.components.relativeTime
import com.kipat.probe.ui.theme.Kipat
import com.kipat.probe.ui.theme.KipatSpacing
import java.util.Calendar

@Composable
fun HomeScreen(
    onOpenConnections: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenTouch: () -> Unit,
    onOpenConnection: (String) -> Unit,
) {
    val context = LocalContext.current
    val identity by KipatRepository.identity.collectAsStateWithLifecycle()
    val card by KipatRepository.card.collectAsStateWithLifecycle()
    val entitlement by KipatRepository.entitlement.collectAsStateWithLifecycle()
    val connections by KipatRepository.connections.collectAsStateWithLifecycle()
    val taps by KipatRepository.recentTaps.collectAsStateWithLifecycle()
    val recent = connections.sortedByDescending { it.connectedAtMillis }.take(8)

    fun previewReveal() = RevealLauncher.launch(
        context,
        identity.toRevealPayload(RevealSource.PREVIEW, isDemo = card.isDemo, authenticity = card.authenticity),
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(homeBackdrop()),
        contentPadding = PaddingValues(bottom = KipatSpacing.xxl),
    ) {
        item {
            HomeTopBar(identity, onOpenProfile)
        }
        item {
            Text(
                text = "${greeting()}, ${identity.displayName.substringBefore(' ')}",
                style = MaterialTheme.typography.headlineMedium,
                color = Kipat.colors.textPrimary,
                modifier = Modifier
                    .padding(horizontal = KipatSpacing.gutter)
                    .padding(top = KipatSpacing.sm, bottom = KipatSpacing.xl)
                    .semantics { heading() },
            )
        }
        item {
            KipatIdentityCard(
                displayName = identity.displayName,
                subtitle = identity.subtitle,
                organization = identity.organization,
                initials = initialsOf(identity.displayName),
                authenticity = card.authenticity,
                isDemo = card.isDemo,
                eyebrow = "Active identity",
                modifier = Modifier.padding(horizontal = KipatSpacing.gutter),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(KipatSpacing.sm)) {
                    KipatPrimaryButton(
                        text = "Preview Reveal",
                        icon = Icons.Filled.PlayArrow,
                        onClick = ::previewReveal,
                        modifier = Modifier.weight(1f),
                    )
                    KipatSecondaryButton(
                        text = "Edit",
                        onClick = onOpenProfile,
                    )
                }
            }
        }

        item { KipatSectionHeader("Your Kipat", Modifier.padding(horizontal = KipatSpacing.gutter)) }
        item {
            KipatGroup(Modifier.padding(horizontal = KipatSpacing.gutter)) {
                KipatInfoRow(
                    label = "Kipat Card",
                    value = card.cardId,
                    leading = { Icon(KipatIcons.Mark, contentDescription = null, tint = Kipat.colors.accent, modifier = Modifier.size(22.dp)) },
                    trailing = {
                        StatusPill(
                            label = if (card.isDemo) "${card.state.label} · demo" else card.state.label,
                            tone = StatusTone.POSITIVE.takeIf { card.state == com.kipat.probe.model.CardState.ACTIVE } ?: StatusTone.WARNING,
                        )
                    },
                    onClick = onOpenTouch,
                )
                KipatGroupDivider()
                KipatInfoRow(
                    label = "Current Reveal",
                    value = identity.revealTemplate.label,
                    trailing = { Chevron() },
                    onClick = onOpenProfile,
                )
                KipatGroupDivider()
                KipatInfoRow(
                    label = "Profile template",
                    value = identity.profileTemplate.label,
                    trailing = { Chevron() },
                    onClick = onOpenProfile,
                )
                KipatGroupDivider()
                KipatInfoRow(
                    label = "Plan",
                    value = entitlement.label,
                    trailing = { Text("Plus coming soon", style = MaterialTheme.typography.bodySmall, color = Kipat.colors.textTertiary) },
                )
            }
        }

        item {
            KipatSectionHeader("Recent connections", Modifier.padding(horizontal = KipatSpacing.gutter), action = "See all", onAction = onOpenConnections)
        }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = KipatSpacing.gutter),
                horizontalArrangement = Arrangement.spacedBy(KipatSpacing.lg),
            ) {
                items(recent, key = { it.id }) { c ->
                    Column(
                        modifier = Modifier
                            .width(72.dp)
                            .clip(MaterialTheme.shapes.small)
                            .clickable(role = Role.Button, onClickLabel = "Open ${c.displayName}") { onOpenConnection(c.id) }
                            .padding(vertical = KipatSpacing.xs),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        KipatAvatar(initialsOf(c.displayName), size = 56.dp)
                        Spacer(Modifier.height(KipatSpacing.sm))
                        Text(
                            c.displayName.substringBefore(' '),
                            style = MaterialTheme.typography.labelMedium,
                            color = Kipat.colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        item { KipatSectionHeader("Recent taps", Modifier.padding(horizontal = KipatSpacing.gutter)) }
        item {
            KipatGroup(Modifier.padding(horizontal = KipatSpacing.gutter)) {
                if (taps.isEmpty()) {
                    Text(
                        "No taps yet. Hold a Kipat Card near your phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Kipat.colors.textSecondary,
                        modifier = Modifier.padding(KipatSpacing.lg),
                    )
                }
                taps.take(4).forEachIndexed { i, tap ->
                    if (i > 0) KipatGroupDivider()
                    PersonRow(
                        name = tap.displayName,
                        detail = (if (tap.savedAsConnection) "Saved · " else "Not saved · ") + tap.subtitle,
                        authenticity = tap.authenticity,
                        isDemo = tap.isDemo,
                        trailingText = relativeTime(tap.atMillis),
                        onClick = {
                            val known = connections.firstOrNull { it.cardId == tap.cardId }
                            if (known != null) onOpenConnection(known.id) else onOpenConnections()
                        },
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(KipatSpacing.xl))
            KipatSecondaryButton(
                text = "Share My Kipat",
                icon = Icons.Filled.Share,
                onClick = {
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, "${identity.displayName} on Kipat — https://kipat.app/c/${identity.cardId}")
                    context.startActivity(Intent.createChooser(send, "Share My Kipat"))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = KipatSpacing.gutter),
            )
        }
    }
}

@Composable
private fun HomeTopBar(identity: Identity, onOpenProfile: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = KipatSpacing.gutter, vertical = KipatSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(KipatIcons.Mark, contentDescription = null, tint = Kipat.colors.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(KipatSpacing.sm))
        Text("Kipat", style = MaterialTheme.typography.titleLarge, color = Kipat.colors.textPrimary, modifier = Modifier.weight(1f))
        Box(
            Modifier
                .size(KipatSpacing.touchTarget)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = "Open profile", onClick = onOpenProfile),
            contentAlignment = Alignment.Center,
        ) {
            KipatAvatar(initialsOf(identity.displayName), size = 36.dp)
        }
    }
}

@Composable
private fun Chevron() {
    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Kipat.colors.textTertiary)
}

@Composable
private fun homeBackdrop(): Brush {
    val c = Kipat.colors
    val base = MaterialTheme.colorScheme.background
    return Brush.verticalGradient(
        0f to c.accentSecondary.copy(alpha = if (c.isDark) 0.10f else 0.06f),
        0.35f to base,
        1f to base,
    )
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}
