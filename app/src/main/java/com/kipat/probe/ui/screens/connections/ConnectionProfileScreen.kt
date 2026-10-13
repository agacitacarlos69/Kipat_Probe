package com.kipat.probe.ui.screens.connections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kipat.probe.data.KipatRepository
import com.kipat.probe.data.toRevealPayload
import com.kipat.probe.model.initialsOf
import com.kipat.probe.reveal.RevealLauncher
import com.kipat.probe.ui.components.KipatGroup
import com.kipat.probe.ui.components.KipatGroupDivider
import com.kipat.probe.ui.components.KipatIdentityCard
import com.kipat.probe.ui.components.KipatInfoRow
import com.kipat.probe.ui.components.KipatPrimaryButton
import com.kipat.probe.ui.components.KipatSectionHeader
import com.kipat.probe.ui.components.relativeTime
import com.kipat.probe.ui.theme.Kipat
import com.kipat.probe.ui.theme.KipatSpacing

/** Read-only view of a connection's published Kipat profile (local/demo data for now). */
@Composable
fun ConnectionProfileScreen(connectionId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val connections by KipatRepository.connections.collectAsStateWithLifecycle()
    val c = connections.firstOrNull { it.id == connectionId }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(Modifier.padding(horizontal = KipatSpacing.xs, vertical = KipatSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Profile", style = MaterialTheme.typography.titleLarge, color = Kipat.colors.textPrimary)
        }
        if (c == null) {
            Text(
                "This connection was removed.",
                style = MaterialTheme.typography.bodyLarge,
                color = Kipat.colors.textSecondary,
                modifier = Modifier.padding(KipatSpacing.gutter),
            )
            return@Column
        }
        Spacer(Modifier.height(KipatSpacing.sm))
        KipatIdentityCard(
            displayName = c.displayName,
            subtitle = c.subtitle,
            organization = c.organization,
            initials = initialsOf(c.displayName),
            authenticity = c.authenticity,
            isDemo = c.isDemo,
            eyebrow = "Connection",
            modifier = Modifier.padding(horizontal = KipatSpacing.gutter),
        ) {
            KipatPrimaryButton(
                text = "Replay Reveal",
                icon = Icons.Filled.PlayArrow,
                onClick = { RevealLauncher.launch(context, c.toRevealPayload()) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        KipatSectionHeader("Details", Modifier.padding(horizontal = KipatSpacing.gutter))
        KipatGroup(Modifier.padding(horizontal = KipatSpacing.gutter)) {
            KipatInfoRow("Kipat Card", c.cardId)
            KipatGroupDivider()
            KipatInfoRow("Connected via", c.source.label)
            KipatGroupDivider()
            KipatInfoRow("Connected", relativeTime(c.connectedAtMillis))
            KipatGroupDivider()
            KipatInfoRow("Reveal", c.revealTemplate.label)
        }
        if (c.isDemo) {
            Text(
                "Sample connection — profile details are local demo data.",
                style = MaterialTheme.typography.bodySmall,
                color = Kipat.colors.textTertiary,
                modifier = Modifier.padding(KipatSpacing.gutter),
            )
        }
        Spacer(Modifier.height(KipatSpacing.xxl))
    }
}
