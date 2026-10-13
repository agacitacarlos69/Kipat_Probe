package com.kipat.probe.ui.screens.developer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kipat.probe.data.KipatRepository
import com.kipat.probe.nfc.KipatTagUri
import com.kipat.probe.nfc.NfcAvailability
import com.kipat.probe.nfc.NfcWriteController
import com.kipat.probe.nfc.TagWriteState
import com.kipat.probe.nfc.label
import com.kipat.probe.ui.components.ActionRow
import com.kipat.probe.ui.components.KipatDivider
import com.kipat.probe.ui.components.KipatPrimaryButton
import com.kipat.probe.ui.components.KipatSecondaryButton
import com.kipat.probe.ui.components.KipatSymbols
import com.kipat.probe.ui.components.KipatTopBar
import com.kipat.probe.ui.components.SectionHeading
import com.kipat.probe.ui.components.relativeTime
import com.kipat.probe.ui.theme.Kipat
import com.kipat.probe.ui.theme.KipatSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Prototype-only tools moved out of the consumer UI: the NTAG writer (foreground dispatch armed
 * only while this screen is open), NFC diagnostics, debug Reveal triggers, Reveal timings and the
 * original probe's log. Monospace is allowed here because these are raw values.
 */
@Composable
fun DeveloperToolsScreen(writer: NfcWriteController, onBack: () -> Unit) {
    val context = LocalContext.current
    val identity by KipatRepository.identity.collectAsStateWithLifecycle()
    val writeState by writer.state.collectAsStateWithLifecycle()
    val lastResult by writer.lastResult.collectAsStateWithLifecycle()
    val timings by KipatRepository.revealTimings.collectAsStateWithLifecycle()
    var nfc by remember { mutableStateOf(writer.availability()) }
    var includeHints by rememberSaveable { mutableStateOf(true) }

    LifecycleResumeEffect(Unit) {
        nfc = writer.availability()
        onPauseOrDispose { }
    }
    // Writing is only armed while this screen is open.
    DisposableEffect(Unit) { onDispose { writer.cancel() } }

    val tagUri = remember(identity, includeHints) {
        if (includeHints) KipatTagUri.build(identity.cardId, identity.displayName, identity.revealTemplate) else KipatTagUri.build(identity.cardId)
    }
    val probeLog by produceState<String?>(null) {
        value = withContext(Dispatchers.IO) {
            runCatching { File(context.filesDir, "probe-log.txt").takeIf { it.exists() && it.length() > 0 }?.readText()?.takeLast(4_000) }.getOrNull()
        }
    }
    val mono = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
    val armed = writeState is TagWriteState.Armed || writeState is TagWriteState.Writing

    Scaffold(
        containerColor = Kipat.colors.background,
        topBar = { KipatTopBar("Developer tools", navigationIcon = KipatSymbols.ArrowBack, onNavigate = onBack) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = KipatSpacing.s32),
        ) {
            Text(
                "For prototype cards only. Tags written here are unsigned; production cards will use NTAG 424 DNA.",
                style = MaterialTheme.typography.bodyMedium,
                color = Kipat.colors.textSecondary,
                modifier = Modifier.padding(horizontal = KipatSpacing.gutter),
            )

            SectionHeading("Card writer")
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = KipatSpacing.rowTwoLine)
                    .clickable(role = Role.Switch) { includeHints = !includeHints }
                    .padding(horizontal = KipatSpacing.gutter),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Include name on tag", style = MaterialTheme.typography.bodyLarge, color = Kipat.colors.textPrimary)
                    Text("Other phones show it as unverified", style = MaterialTheme.typography.bodyMedium, color = Kipat.colors.textSecondary)
                }
                Switch(checked = includeHints, onCheckedChange = null, colors = SwitchDefaults.colors(checkedTrackColor = Kipat.colors.accent))
            }
            SelectionContainer {
                Text(tagUri, style = mono, color = Kipat.colors.textSecondary, modifier = Modifier.padding(horizontal = KipatSpacing.gutter, vertical = KipatSpacing.s8))
            }
            Text(
                "Status: ${writeState.label}",
                style = MaterialTheme.typography.bodyMedium,
                color = Kipat.colors.textPrimary,
                modifier = Modifier
                    .padding(horizontal = KipatSpacing.gutter, vertical = KipatSpacing.s4)
                    .semantics { contentDescription = "Write status: ${writeState.label}" },
            )
            lastResult?.let {
                Text("Last result: ${it.label}", style = MaterialTheme.typography.bodySmall, color = Kipat.colors.textSecondary, modifier = Modifier.padding(horizontal = KipatSpacing.gutter))
            }
            if (armed) {
                KipatSecondaryButton("Cancel write", onClick = writer::cancel, modifier = Modifier.fillMaxWidth().padding(KipatSpacing.gutter))
            } else {
                KipatPrimaryButton(
                    "Write to tag",
                    icon = KipatSymbols.Nfc,
                    onClick = { nfc = writer.availability(); writer.arm(tagUri) },
                    modifier = Modifier.fillMaxWidth().padding(KipatSpacing.gutter),
                )
            }

            SectionHeading("NFC")
            ActionRow("NFC hardware", value = if (nfc == NfcAvailability.UNSUPPORTED) "Not present" else "Present")
            KipatDivider()
            ActionRow("NFC", value = when (nfc) {
                NfcAvailability.ENABLED -> "On"
                NfcAvailability.DISABLED -> "Off"
                NfcAvailability.UNSUPPORTED -> "Unavailable"
            }, chevron = nfc != NfcAvailability.UNSUPPORTED, onClick = if (nfc != NfcAvailability.UNSUPPORTED) ({ openNfcSettings(context) }) else null)

            SectionHeading("Test the Reveal")
            ActionRow("Unknown card", supporting = "kipatprobe://tap/TEST123", onClick = { openDeepLink(context, "kipatprobe://tap/TEST123") })
            KipatDivider()
            ActionRow("Your card", supporting = "kipatprobe://tap/demo", onClick = { openDeepLink(context, "kipatprobe://tap/demo") })
            KipatDivider()
            ActionRow("Name on tag, Particle Bloom", supporting = "Unverified card", onClick = { openDeepLink(context, "kipatprobe://tap/KPT-X1?n=Sam%20Reyes&s=Designer&t=particle-bloom") })
            KipatDivider()
            ActionRow("Revoked card, Minimal", supporting = "Card revoked", onClick = { openDeepLink(context, "kipatprobe://tap/KPT-DEMO-0107?a=revoked&t=minimal-fade") })

            SectionHeading("Reveal timings on this device")
            if (timings.isEmpty()) {
                Text("No Reveals measured yet.", style = MaterialTheme.typography.bodyMedium, color = Kipat.colors.textSecondary, modifier = Modifier.padding(horizontal = KipatSpacing.gutter))
            }
            timings.forEach { t ->
                Text(
                    "${t.source.wireId}: launch→create ${t.launchToCreateMs?.let { "${it}ms" } ?: "n/a"}, first frame +${t.createToFirstFrameMs ?: "?"}ms, complete +${t.createToCompleteMs ?: "?"}ms, ${relativeTime(t.atMillis)}",
                    style = mono,
                    color = Kipat.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = KipatSpacing.gutter, vertical = KipatSpacing.s4),
                )
            }

            probeLog?.let { log ->
                SectionHeading("Legacy probe log")
                SelectionContainer { Text(log, style = mono, color = Kipat.colors.textSecondary, modifier = Modifier.padding(horizontal = KipatSpacing.gutter)) }
            }
        }
    }
}

private fun openNfcSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
    } catch (e: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
    }
}

private fun openDeepLink(context: Context, uri: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, uri.toUri()).setPackage(context.packageName))
}
