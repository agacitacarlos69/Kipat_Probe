package com.kipat.probe.ui.screens.touch

import android.animation.ValueAnimator
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kipat.probe.data.KipatRepository
import com.kipat.probe.model.StatusTone
import com.kipat.probe.nfc.KipatTagUri
import com.kipat.probe.nfc.NfcAvailability
import com.kipat.probe.nfc.NfcWriteController
import com.kipat.probe.nfc.TagWriteState
import com.kipat.probe.nfc.label
import com.kipat.probe.ui.components.KipatGroup
import com.kipat.probe.ui.components.KipatGroupDivider
import com.kipat.probe.ui.components.KipatIcons
import com.kipat.probe.ui.components.KipatInfoRow
import com.kipat.probe.ui.components.KipatPrimaryButton
import com.kipat.probe.ui.components.KipatSecondaryButton
import com.kipat.probe.ui.components.KipatSectionHeader
import com.kipat.probe.ui.components.StatusPill
import com.kipat.probe.ui.components.relativeTime
import com.kipat.probe.ui.theme.Kipat
import com.kipat.probe.ui.theme.KipatSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun TouchScreen(writer: NfcWriteController) {
    val context = LocalContext.current
    val identity by KipatRepository.identity.collectAsStateWithLifecycle()
    val writeState by writer.state.collectAsStateWithLifecycle()
    val lastResult by writer.lastResult.collectAsStateWithLifecycle()
    val timings by KipatRepository.revealTimings.collectAsStateWithLifecycle()
    var nfc by remember { mutableStateOf(writer.availability()) }
    var includeHints by rememberSaveable { mutableStateOf(true) }
    var diagnosticsOpen by rememberSaveable { mutableStateOf(false) }

    // NFC can be toggled in system settings while we're backgrounded.
    LifecycleResumeEffect(Unit) {
        nfc = writer.availability()
        onPauseOrDispose { }
    }
    // Writing is armed intentionally from this screen only; leaving it disarms.
    DisposableEffect(Unit) { onDispose { writer.cancel() } }

    val tagUri = remember(identity, includeHints) {
        if (includeHints) KipatTagUri.build(identity.cardId, identity.displayName, identity.revealTemplate) else KipatTagUri.build(identity.cardId)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = KipatSpacing.gutter),
    ) {
        Text(
            "Touch",
            style = MaterialTheme.typography.headlineMedium,
            color = Kipat.colors.textPrimary,
            modifier = Modifier
                .padding(vertical = KipatSpacing.lg)
                .semantics { heading() },
        )

        TapBeacon(active = nfc == NfcAvailability.ENABLED)
        Text(
            if (nfc == NfcAvailability.ENABLED) "Ready to share" else nfc.label,
            style = MaterialTheme.typography.titleLarge,
            color = Kipat.colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            when (nfc) {
                NfcAvailability.ENABLED -> "Hold another supported device nearby"
                NfcAvailability.DISABLED -> "Turn on NFC to share and receive Kipat taps"
                NfcAvailability.UNSUPPORTED -> "This phone can't share by tap, but you can still share your link"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Kipat.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Phone-to-phone sharing is a preview — it arrives with Host Card Emulation. Your Kipat Card works today.",
            style = MaterialTheme.typography.bodySmall,
            color = Kipat.colors.textTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = KipatSpacing.sm, start = KipatSpacing.lg, end = KipatSpacing.lg),
        )
        if (nfc == NfcAvailability.DISABLED) {
            Spacer(Modifier.height(KipatSpacing.md))
            KipatSecondaryButton("Open NFC settings", onClick = { openNfcSettings(context) }, modifier = Modifier.align(Alignment.CenterHorizontally))
        }

        KipatSectionHeader("My Kipat")
        KipatGroup {
            KipatInfoRow("Sharing as", identity.displayName)
            KipatGroupDivider()
            KipatInfoRow("Kipat Card", identity.cardId)
        }

        KipatSectionHeader("Prototype Card Tools")
        KipatGroup {
            Column(Modifier.padding(KipatSpacing.lg)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Developer / prototype", style = MaterialTheme.typography.labelMedium, color = Kipat.colors.warning, modifier = Modifier.weight(1f))
                    StatusPill(label = writeState.shortLabel(), tone = writeState.tone())
                }
                Spacer(Modifier.height(KipatSpacing.sm))
                Text(
                    "Writes a Kipat card link plus an Android Application Record to a blank NTAG 213/215/216. Tags are unsigned — production cards use NTAG 424 DNA (not implemented).",
                    style = MaterialTheme.typography.bodySmall,
                    color = Kipat.colors.textSecondary,
                )
                Spacer(Modifier.height(KipatSpacing.md))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = KipatSpacing.touchTarget)
                        .clickable(role = Role.Switch) { includeHints = !includeHints },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Include name hint", style = MaterialTheme.typography.bodyMedium, color = Kipat.colors.textPrimary)
                        Text("Shown as Unverified on other phones", style = MaterialTheme.typography.bodySmall, color = Kipat.colors.textTertiary)
                    }
                    Switch(checked = includeHints, onCheckedChange = null)
                }
                Spacer(Modifier.height(KipatSpacing.sm))
                SelectionContainer {
                    Text(tagUri, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = Kipat.colors.textSecondary)
                }
                Spacer(Modifier.height(KipatSpacing.md))
                Text("Status: ${writeState.label}", style = MaterialTheme.typography.bodyMedium, color = Kipat.colors.textPrimary, modifier = Modifier.semantics { contentDescription = "Write status: ${writeState.label}" })
                lastResult?.let {
                    Text("Last result: ${it.label}", style = MaterialTheme.typography.bodySmall, color = Kipat.colors.textSecondary)
                }
                Spacer(Modifier.height(KipatSpacing.md))
                val armed = writeState is TagWriteState.Armed || writeState is TagWriteState.Writing
                if (armed) {
                    KipatSecondaryButton("Cancel write", onClick = writer::cancel, modifier = Modifier.fillMaxWidth())
                } else {
                    KipatPrimaryButton(
                        "Arm Tag Write",
                        icon = KipatIcons.Touch,
                        onClick = {
                            nfc = writer.availability()
                            writer.arm(tagUri)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        Spacer(Modifier.height(KipatSpacing.xl))
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = KipatSpacing.touchTarget)
                .clickable(role = Role.Button, onClickLabel = if (diagnosticsOpen) "Hide diagnostics" else "Show diagnostics") { diagnosticsOpen = !diagnosticsOpen },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("DEVELOPER DIAGNOSTICS", style = MaterialTheme.typography.labelSmall, color = Kipat.colors.textTertiary, modifier = Modifier.weight(1f))
            Icon(if (diagnosticsOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = Kipat.colors.textTertiary)
        }
        AnimatedVisibility(diagnosticsOpen) {
            Diagnostics(nfc = nfc, lastResult = lastResult, timings = timings)
        }
        Spacer(Modifier.height(KipatSpacing.xxl))
    }
}

@Composable
private fun Diagnostics(
    nfc: NfcAvailability,
    lastResult: TagWriteState?,
    timings: List<com.kipat.probe.data.RevealTiming>,
) {
    val context = LocalContext.current
    val probeLog by produceState<String?>(null) {
        // Preserved from the original probe: show its on-device log if one exists.
        value = withContext(Dispatchers.IO) {
            runCatching { File(context.filesDir, "probe-log.txt").takeIf { it.exists() && it.length() > 0 }?.readText()?.takeLast(4_000) }.getOrNull()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(KipatSpacing.md)) {
        KipatGroup {
            KipatInfoRow("NFC hardware", if (nfc == NfcAvailability.UNSUPPORTED) "Not present" else "Present")
            KipatGroupDivider()
            KipatInfoRow("NFC enabled", if (nfc == NfcAvailability.ENABLED) "Yes" else "No", onClick = { openNfcSettings(context) })
            KipatGroupDivider()
            KipatInfoRow("Last tag write", lastResult?.label ?: "None this session")
        }
        Text("Debug Reveal triggers (same path as adb deep link)", style = MaterialTheme.typography.bodySmall, color = Kipat.colors.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(KipatSpacing.sm)) {
            KipatSecondaryButton("TEST123", onClick = { openDeepLink(context, "kipatprobe://tap/TEST123") }, modifier = Modifier.weight(1f))
            KipatSecondaryButton("My card", onClick = { openDeepLink(context, "kipatprobe://tap/demo") }, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(KipatSpacing.sm)) {
            KipatSecondaryButton("Bloom · hint", onClick = { openDeepLink(context, "kipatprobe://tap/KPT-X1?n=Sam%20Reyes&s=Designer&t=particle-bloom") }, modifier = Modifier.weight(1f))
            KipatSecondaryButton("Revoked", onClick = { openDeepLink(context, "kipatprobe://tap/KPT-DEMO-0107?a=revoked&t=minimal-fade") }, modifier = Modifier.weight(1f))
        }
        KipatGroup {
            Text(
                "Reveal timings (this device, this session)",
                style = MaterialTheme.typography.labelMedium,
                color = Kipat.colors.textSecondary,
                modifier = Modifier.padding(start = KipatSpacing.lg, top = KipatSpacing.md, end = KipatSpacing.lg),
            )
            if (timings.isEmpty()) {
                Text("No Reveals measured yet.", style = MaterialTheme.typography.bodySmall, color = Kipat.colors.textTertiary, modifier = Modifier.padding(KipatSpacing.lg))
            }
            timings.forEach { t ->
                Text(
                    "${t.source.wireId}: launch→create ${t.launchToCreateMs?.let { "${it}ms" } ?: "n/a"} · first frame +${t.createToFirstFrameMs ?: "?"}ms · complete +${t.createToCompleteMs ?: "?"}ms · ${relativeTime(t.atMillis)}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = Kipat.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = KipatSpacing.lg, vertical = KipatSpacing.xs),
                )
            }
            Spacer(Modifier.height(KipatSpacing.sm))
        }
        probeLog?.let { log ->
            KipatGroup {
                Text("Legacy probe log", style = MaterialTheme.typography.labelMedium, color = Kipat.colors.textSecondary, modifier = Modifier.padding(start = KipatSpacing.lg, top = KipatSpacing.md))
                SelectionContainer {
                    Text(log, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = Kipat.colors.textSecondary, modifier = Modifier.padding(KipatSpacing.lg))
                }
            }
        }
    }
}

/** Concentric "ready" rings. Static when system animations are off. */
@Composable
private fun TapBeacon(active: Boolean) {
    val accent = Kipat.colors.accent
    val secondary = Kipat.colors.accentSecondary
    val animate = active && ValueAnimator.areAnimatorsEnabled()
    val phase = if (animate) {
        val t = rememberInfiniteTransition(label = "beacon")
        t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    } else {
        remember { mutableStateOf(0.35f) }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(220.dp)
            .semantics { contentDescription = if (active) "Ready to share" else "NFC unavailable" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(220.dp)) {
            val c = Offset(size.width / 2, size.height / 2)
            val maxR = size.minDimension / 2
            drawCircle(
                Brush.radialGradient(listOf(accent.copy(alpha = if (active) 0.22f else 0.08f), Color.Transparent), center = c, radius = maxR),
                radius = maxR,
                center = c,
            )
            for (i in 0 until 3) {
                val p = (phase.value + i / 3f) % 1f
                val r = maxR * (0.3f + 0.7f * p)
                drawCircle((if (i == 1) secondary else accent).copy(alpha = (1f - p) * if (active) 0.5f else 0.15f), r, c, style = Stroke(width = 1.5.dp.toPx()))
            }
            drawCircle(accent.copy(alpha = if (active) 0.9f else 0.3f), 10.dp.toPx(), c)
        }
    }
}

private fun TagWriteState.shortLabel(): String = when (this) {
    TagWriteState.Idle -> "Waiting"
    is TagWriteState.Armed -> "Armed"
    is TagWriteState.Writing -> "Writing"
    is TagWriteState.Success -> "Success"
    is TagWriteState.Failed -> "Error"
}

private fun TagWriteState.tone(): StatusTone = when (this) {
    TagWriteState.Idle -> StatusTone.NEUTRAL
    is TagWriteState.Armed, is TagWriteState.Writing -> StatusTone.PENDING
    is TagWriteState.Success -> StatusTone.POSITIVE
    is TagWriteState.Failed -> StatusTone.NEGATIVE
}

private fun openNfcSettings(context: android.content.Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
    } catch (e: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
    }
}

private fun openDeepLink(context: android.content.Context, uri: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, uri.toUri()).setPackage(context.packageName))
}
