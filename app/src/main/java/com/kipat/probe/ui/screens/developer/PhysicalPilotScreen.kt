package com.kipat.probe.ui.screens.developer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kipat.probe.pilot.HceRoutingCheck
import com.kipat.probe.pilot.PilotCheck
import com.kipat.probe.pilot.Tri
import com.kipat.probe.pilot.PilotReport
import com.kipat.probe.pilot.PilotStage
import com.kipat.probe.pilot.PilotStore
import com.kipat.probe.pilot.StageResult
import com.kipat.probe.ui.components.ActionRow
import com.kipat.probe.ui.components.KipatDivider
import com.kipat.probe.ui.components.KipatPrimaryButton
import com.kipat.probe.ui.components.KipatSecondaryButton
import com.kipat.probe.ui.components.KipatSymbols
import com.kipat.probe.ui.components.KipatTextButton
import com.kipat.probe.ui.components.KipatTopBar
import com.kipat.probe.ui.components.SectionHeading
import com.kipat.probe.ui.theme.Kipat
import com.kipat.probe.ui.theme.KipatSpacing
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * Developer tools → Physical pilot. Everything a tester needs to run the two-phone and card pilot
 * with only the phones: which build this is, whether NFC and Kipat Tap are ready, what the last Kipat
 * Tap reached stage by stage (measured), a checklist for what only a person can see, and a
 * content-free diagnostic to export or copy. Not a consumer feature.
 */
@Composable
fun PhysicalPilotScreen(onBack: () -> Unit, onCardTester: () -> Unit, onKipatCards: () -> Unit, onControlledTest: () -> Unit = {}) {
    val context = LocalContext.current
    val version by PilotStore.version.collectAsStateWithLifecycle()
    // Re-read on resume too: NFC may have been switched on in Settings meanwhile.
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumeTick++
        onPauseOrDispose { }
    }
    val state = remember(version, resumeTick) { PilotStore.state(context) }
    val mono = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
    val last = state.last

    Scaffold(
        containerColor = Kipat.colors.background,
        topBar = { KipatTopBar("Physical pilot", navigationIcon = KipatSymbols.ArrowBack, onNavigate = onBack) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = KipatSpacing.s32),
        ) {
            Text(
                "For the two-phone and card pilot. Install the same APK on both phones and check the commit below matches before testing.",
                style = MaterialTheme.typography.bodyMedium,
                color = Kipat.colors.textSecondary,
                modifier = Modifier.padding(horizontal = KipatSpacing.gutter, vertical = KipatSpacing.s8),
            )
            Column(Modifier.padding(KipatSpacing.gutter), verticalArrangement = Arrangement.spacedBy(KipatSpacing.s8)) {
                KipatPrimaryButton("Export diagnostic", icon = KipatSymbols.Share, onClick = { exportDiagnostic(context) }, modifier = Modifier.fillMaxWidth())
                KipatSecondaryButton("Copy diagnostic", icon = KipatSymbols.Copy, onClick = { copyDiagnostic(context) }, modifier = Modifier.fillMaxWidth())
            }

            SectionHeading("Build")
            ActionRow(state.build.title, value = "build ${state.build.buildNumber}")
            KipatDivider()
            ActionRow("Commit", value = state.build.shortSha)
            KipatDivider()
            ActionRow("Built", value = state.build.buildTime)
            KipatDivider()
            ActionRow("Build type", value = state.build.buildType)

            SectionHeading("Device")
            ActionRow("Phone", value = "${state.device.manufacturer} ${state.device.model}")
            KipatDivider()
            ActionRow("Android", value = "${state.device.androidVersion} (API ${state.device.api})")

            SectionHeading("NFC")
            ActionRow("Hardware", value = yesNo(state.device.hasNfc))
            KipatDivider()
            ActionRow("Enabled", value = yesNo(state.device.nfcEnabled), valueColor = if (state.device.nfcEnabled) Kipat.colors.verified else Kipat.colors.warning)
            KipatDivider()
            ActionRow("HCE supported", value = yesNo(state.device.hasHce))
            KipatDivider()
            val ready = state.device.kipatTap == "READY"
            ActionRow("Kipat Tap", value = if (ready) "Ready" else "Unavailable: ${state.device.kipatTap.lowercase().replace('_', ' ')}", valueColor = if (ready) Kipat.colors.verified else Kipat.colors.warning)

            state.profile?.let { p ->
                SectionHeading("Profile")
                ActionRow("Profile ID", value = p.pseudonym)
                KipatDivider()
                ActionRow("Name", value = p.name)
                KipatDivider()
                ActionRow("Reveal", value = p.reveal)
                KipatDivider()
                ActionRow("Accent", value = p.accent)
            }

            state.routing?.let { r ->
                SectionHeading("HCE routing")
                ActionRow("Service", value = r.service)
                KipatDivider()
                ActionRow("Service enabled", value = r.serviceEnabled.name, valueColor = triColor(r.serviceEnabled))
                KipatDivider()
                ActionRow("NDEF v2 AID", supporting = HceRoutingCheck.NDEF_V2, value = r.ndefV2.name, valueColor = triColor(r.ndefV2))
                KipatDivider()
                ActionRow("NDEF v1 AID", supporting = HceRoutingCheck.NDEF_V1, value = r.ndefV1.name, valueColor = triColor(r.ndefV1))
                KipatDivider()
                ActionRow("Kipat AID", supporting = HceRoutingCheck.KIPAT, value = r.kipatAid.name, valueColor = triColor(r.kipatAid))
                KipatDivider()
                ActionRow("Category", value = r.category)
                KipatDivider()
                ActionRow("Device unlock required", value = if (r.unlockRequired) "Yes" else "No")
                KipatDivider()
                ActionRow("Background restricted", value = r.backgroundRestricted.name, valueColor = triColor(r.backgroundRestricted, good = Tri.NO))
                KipatDivider()
                ActionRow("Battery unrestricted", value = r.batteryUnrestricted.name)
                KipatDivider()
                ActionRow("Standby bucket", value = r.standbyBucket)
                if (r.ndefV2 == Tri.NO) Note("Android will not send the other phone's NDEF check to Kipat here: another app or the system claims the AID, or Kipat is switched off under NFC settings. The other phone then shows an empty tag.")
                if (HceRoutingCheck.isXiaomiFamily()) {
                    Note("Xiaomi, Redmi and POCO: Kipat must be allowed to start in the background, or this phone can't answer a tap while Kipat is closed. In App settings turn on Autostart and set Battery saver to No restrictions. If NFC settings offer a Secure element position, choose HCE wallet.")
                    ActionRow("Open Kipat app settings", chevron = true, onClick = { openAppSettings(context) })
                }
            }

            SectionHeading("Decision gate")
            Note("Two separate questions. Controlled: does the Kipat exchange work when one phone is an explicit reader? Ambient: does Android ever hand the other phone's NDEF SELECT to Kipat while Kipat is closed?")

            SectionHeading("Controlled mutual NFC test")
            ActionRow(
                "Controlled mutual NFC test",
                supporting = "Phone A: Act as Kipat presenter. Phone B: Act as Kipat reader. Touch once. Then swap roles.",
                icon = KipatSymbols.Contactless,
                chevron = true,
                onClick = onControlledTest,
            )
            listOf("As reader" to state.controlledReader, "As presenter" to state.controlledPresenter).forEach { (label, c) ->
                KipatDivider()
                if (c == null) {
                    ActionRow(label, value = "Not run")
                } else {
                    ActionRow(
                        label,
                        supporting = (c.failureCode ?: "No failure") + (c.correlation?.let { "  $it" }.orEmpty()),
                        value = if (c.mutual) "Mutual PASS" else "FAIL",
                        valueColor = if (c.mutual) Kipat.colors.verified else Kipat.colors.revoked,
                    )
                    PilotReport.CONTROLLED_STAGES.forEach { s ->
                        val r = c.result(s)
                        ActionRow(
                            "  ${s.label}",
                            value = r.name.replace('_', ' ') + (c.elapsed(s)?.let { "  ${it} ms" }.orEmpty()),
                            valueColor = when (r) {
                                StageResult.PASS -> Kipat.colors.verified
                                StageResult.FAIL -> Kipat.colors.revoked
                                else -> Kipat.colors.textSecondary
                            },
                        )
                    }
                    ActionRow("  This phone received the other profile", value = yesNo(c.profileReceived))
                    ActionRow("  Other phone confirmed ours", value = yesNo(c.profileSent))
                }
            }

            state.ambient?.let { e ->
                SectionHeading("Ambient test evidence")
                ActionRow("Kipat HCE service invoked", value = yesNo(e.hceServiceCreated), valueColor = if (e.hceServiceCreated) Kipat.colors.verified else Kipat.colors.revoked)
                KipatDivider()
                ActionRow("NDEF SELECT reached Kipat", value = yesNo(e.ndefSelectReceived), valueColor = if (e.ndefSelectReceived) Kipat.colors.verified else Kipat.colors.revoked)
                KipatDivider()
                ActionRow("CC read", value = yesNo(e.ccRead))
                KipatDivider()
                ActionRow("NDEF read", value = yesNo(e.ndefRead))
                KipatDivider()
                ActionRow("Bootstrap served", value = yesNo(e.bootstrapServed))
                KipatDivider()
                ActionRow("Kipat dispatched on this phone", value = yesNo(e.kipatDispatchedHere))
                if (!e.hceServiceCreated && !e.kipatDispatchedHere) {
                    Note("No ambient Kipat code ran on this phone since results were cleared. If an ambient touch showed Android's Empty tag, Kipat never received the NDEF SELECT: changing the Type 4 bytes cannot fix that.")
                }
            }

            SectionHeading("Last Kipat Tap")
            if (last == null) {
                Note("No Kipat Tap recorded yet. Touch the two phones together, then come back here.")
            } else {
                ActionRow("Role", value = last.role.label)
                KipatDivider()
                ActionRow(
                    "Mutual exchange on this phone",
                    value = if (last.mutual) "Complete" else "Not complete",
                    valueColor = if (last.mutual) Kipat.colors.verified else Kipat.colors.revoked,
                )
                last.correlation?.let {
                    KipatDivider()
                    ActionRow("Exchange", value = it)
                }
                PilotStage.entries.groupBy { it.group }.forEach { (group, stages) ->
                    SectionHeading(group)
                    stages.forEachIndexed { i, s ->
                        if (i > 0) KipatDivider()
                        val r = last.result(s)
                        ActionRow(
                            s.label,
                            value = r.name.replace('_', ' ') + (last.elapsed(s)?.let { "  ${it} ms" }.orEmpty()),
                            valueColor = when (r) {
                                StageResult.PASS -> Kipat.colors.verified
                                StageResult.FAIL -> Kipat.colors.revoked
                                else -> Kipat.colors.textSecondary
                            },
                        )
                    }
                }
                last.failureCode?.let {
                    KipatDivider()
                    ActionRow("Failure", value = it, valueColor = Kipat.colors.revoked)
                }
                last.durationMs?.let {
                    KipatDivider()
                    ActionRow("Elapsed", value = "$it ms")
                }
            }

            SectionHeading("Last physical test")
            val peer = state.peer
            if (peer == null || last == null) {
                Note("Appears after a Kipat Tap delivers the other phone's profile.")
            } else {
                ActionRow("Peer", value = peer.name)
                KipatDivider()
                ActionRow("Transport", value = "Ambient Kipat Tap")
                KipatDivider()
                ActionRow("Profile received", value = yesNo(last.profileReceived))
                KipatDivider()
                ActionRow("Profile sent", value = yesNo(last.profileSent))
                KipatDivider()
                ActionRow("Reveal", value = peer.reveal)
                KipatDivider()
                ActionRow("Mutual exchange", value = if (last.mutual) "Complete" else "Not complete")
                KipatDivider()
                ActionRow("Returned to previous app", value = "Check by eye")
                KipatDivider()
                ActionRow(
                    "Connection",
                    supporting = if (state.connectionStored == true) "Stored on this phone. Restart Kipat and check it is still in Connections." else "A tap never connects. Press Connect on the Reveal to test this.",
                    value = if (state.connectionStored == true) "Connected by you" else "Not automatic",
                )
            }

            SectionHeading("Checklist (you confirm these)")
            PilotCheck.entries.forEach { check ->
                val checked = state.checklist[check] == true
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = KipatSpacing.rowOneLine)
                        .clickable(role = Role.Checkbox) { PilotStore.setCheck(context, check, !checked) }
                        .padding(horizontal = KipatSpacing.gutter),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(check.label, style = MaterialTheme.typography.bodyLarge, color = Kipat.colors.textPrimary, modifier = Modifier.weight(1f))
                    Checkbox(checked = checked, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = Kipat.colors.accent))
                }
            }
            Note("Stored on this phone only, and included in the exported diagnostic. The app can't see these itself, so the pilot is never marked passed automatically.")

            SectionHeading("How to test two phones")
            Steps(
                "Both phones first" to listOf("Install the same Kipat Pilot APK.", "Check both show the same commit above.", "HCE routing above: NDEF v2 AID and Kipat AID should say YES.", "Xiaomi, Redmi, POCO: allow Autostart for Kipat (Open Kipat app settings).", "Clear results."),
                "Phone 1" to listOf("Profile Kyle Jimenez, Reveal Precision Orbit, accent Cobalt.", "Turn on Kipat Tap.", "Leave Kipat and open Chrome."),
                "Phone 2" to listOf("Profile Randy Orton, Reveal Contact Ripple, accent Amber.", "Turn on Kipat Tap.", "Leave Kipat and open Settings."),
                "Then" to listOf("Unlock both phones, NFC on.", "Hold the backs together and keep them together until the Reveals appear.", "Expected: phone 1 shows Randy, phone 2 shows Kyle. Nobody presses anything.", "Android's own New tag or Empty tag screen is a FAIL.", "Come back here on each phone and export the diagnostic."),
            )

            SectionHeading("Card test")
            state.card?.let { c ->
                ActionRow("Last card ${c.action}", value = c.result.lowercase().replace('_', ' '))
                KipatDivider()
                ActionRow("Technology", value = c.technologies.ifEmpty { "Unknown" })
                KipatDivider()
                ActionRow("NDEF", value = c.ndefType ?: "Not NDEF")
                KipatDivider()
                ActionRow("Capacity", value = c.capacityBytes?.let { "$it bytes" } ?: "Unknown")
                KipatDivider()
                ActionRow("Writable", value = c.writable?.let(::yesNo) ?: "Unknown")
                KipatDivider()
                ActionRow("Checked", value = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(c.atWallMs)))
                KipatDivider()
            }
            ActionRow("Self-contained test card", supporting = "Detect, write, read and verify read-back", icon = KipatSymbols.Contactless, chevron = true, onClick = onCardTester)
            KipatDivider()
            ActionRow("Linked card test", supporting = "Link a card, change your Reveal, tap again", icon = KipatSymbols.IdCard, chevron = true, onClick = onKipatCards)
            Steps(
                "Linked card (local prototype)" to listOf("Kipat cards, then Add Kipat card, then hold the card.", "Leave Kipat and tap the card: your current Reveal.", "Change your Reveal style. Do not rewrite the card. Tap again: the new Reveal."),
                "Self-contained card (static)" to listOf("Write your profile with Precision Orbit.", "Change your Reveal to Aperture.", "Tap the card: still Precision Orbit, because the card holds a copy."),
            )

            SectionHeading("Events (last ${state.events.size})")
            Text(
                state.events.takeLast(12).joinToString("\n") { e -> "${e.elapsedMs?.let { "+${it}ms " }.orEmpty()}${e.name} ${e.result} ${e.detail}".trim() }.ifEmpty { "None yet." },
                style = mono,
                color = Kipat.colors.textSecondary,
                modifier = Modifier.padding(horizontal = KipatSpacing.gutter, vertical = KipatSpacing.s8),
            )
            KipatTextButton("Clear results", color = Kipat.colors.revoked, onClick = { PilotStore.reset(context) }, modifier = Modifier.padding(horizontal = KipatSpacing.s8))
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Kipat.colors.textSecondary, modifier = Modifier.padding(horizontal = KipatSpacing.gutter, vertical = KipatSpacing.s8))
}

@Composable
private fun Steps(vararg groups: Pair<String, List<String>>) {
    Column(Modifier.padding(horizontal = KipatSpacing.gutter, vertical = KipatSpacing.s8), verticalArrangement = Arrangement.spacedBy(KipatSpacing.s8)) {
        groups.forEach { (title, steps) ->
            Text(title, style = MaterialTheme.typography.titleSmall, color = Kipat.colors.textPrimary)
            steps.forEachIndexed { i, step ->
                Text("${i + 1}. $step", style = MaterialTheme.typography.bodyMedium, color = Kipat.colors.textSecondary)
            }
        }
    }
}

private fun yesNo(b: Boolean) = if (b) "Yes" else "No"

@Composable
private fun triColor(t: Tri, good: Tri = Tri.YES) = when (t) {
    Tri.UNKNOWN -> Kipat.colors.textSecondary
    good -> Kipat.colors.verified
    else -> Kipat.colors.revoked
}

private fun openAppSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** Writes the ZIP to cache/pilot/ and opens the system share sheet. Nothing leaves the phone otherwise. */
internal fun exportDiagnostic(context: Context) {
    val now = System.currentTimeMillis()
    val state = PilotStore.state(context)
    val dir = File(context.cacheDir, "pilot").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() } // keep only the newest export
    val file = File(dir, PilotReport.fileName(state.device, now))
    file.writeBytes(PilotReport.zip(state, now))
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("application/zip")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
        .putExtra(Intent.EXTRA_TEXT, PilotReport.copyText(state))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

internal fun copyDiagnostic(context: Context) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Kipat pilot diagnostic", PilotReport.copyText(PilotStore.state(context))))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}
