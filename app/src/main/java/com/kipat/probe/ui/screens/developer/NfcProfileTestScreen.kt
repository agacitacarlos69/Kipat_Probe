package com.kipat.probe.ui.screens.developer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kipat.probe.data.NfcProfileReveal.toRevealPayload
import com.kipat.probe.nfc.NfcProfileSession
import com.kipat.probe.nfc.ProfileTagState
import com.kipat.probe.nfc.profile.NfcProfileDiagnostics
import com.kipat.probe.nfc.profile.NfcProfileFormat
import com.kipat.probe.nfc.profile.NfcProfileMode
import com.kipat.probe.nfc.profile.NfcProfileParse
import com.kipat.probe.nfc.profile.NfcProfilePayload
import com.kipat.probe.nfc.profile.NfcProfileType
import com.kipat.probe.nfc.profile.TagSnapshot
import com.kipat.probe.reveal.RevealLauncher
import com.kipat.probe.reveal.RevealSource
import com.kipat.probe.ui.components.ActionRow
import com.kipat.probe.ui.components.KipatDivider
import com.kipat.probe.ui.components.KipatPrimaryButton
import com.kipat.probe.ui.components.KipatSecondaryButton
import com.kipat.probe.ui.components.KipatSymbols
import com.kipat.probe.ui.components.KipatTextButton
import com.kipat.probe.ui.components.KipatTopBar
import com.kipat.probe.ui.components.OptionRow
import com.kipat.probe.ui.components.SectionHeading
import com.kipat.probe.ui.theme.Kipat
import com.kipat.probe.ui.theme.KipatSpacing
import kotlinx.coroutines.flow.MutableStateFlow

const val NFC_PROFILE_PRIVACY_NOTE =
    "Test profiles stored directly on an NFC card can be read and copied by other NFC-capable devices. Do not store sensitive personal information."

/**
 * Developer tools → NFC full profile test. Writes a complete Kipat test profile onto a physical
 * card, reads it back, and opens it in the normal Reveal. Development only: a profile on a card
 * is unverified data, never a verified Kipat identity.
 */
@Composable
fun NfcProfileTestScreen(onBack: () -> Unit, onEdit: () -> Unit, onCreate: () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val session = remember(activity) { activity?.let { NfcProfileSession(it, scope) } }
    val idle = remember { MutableStateFlow<ProfileTagState>(ProfileTagState.Idle) }
    val state by (session?.state ?: idle).collectAsStateWithLifecycle()
    val draft by NfcProfileLab.draft.collectAsStateWithLifecycle()
    val mode by NfcProfileLab.mode.collectAsStateWithLifecycle()
    val includeAar by NfcProfileLab.includeAar.collectAsStateWithLifecycle()

    LifecycleResumeEffect(session) {
        session?.onResume()
        onPauseOrDispose { session?.onPause() }
    }

    Scaffold(
        containerColor = Kipat.colors.background,
        topBar = { KipatTopBar("NFC full profile test", navigationIcon = KipatSymbols.ArrowBack, onNavigate = { session?.cancel(); onBack() }) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = KipatSpacing.s32),
        ) {
            Row(
                Modifier.padding(horizontal = KipatSpacing.gutter, vertical = KipatSpacing.s8),
                horizontalArrangement = Arrangement.spacedBy(KipatSpacing.s12),
            ) {
                Icon(KipatSymbols.Warning, contentDescription = null, tint = Kipat.colors.warning, modifier = Modifier.size(20.dp))
                Text(NFC_PROFILE_PRIVACY_NOTE, style = MaterialTheme.typography.bodyMedium, color = Kipat.colors.textSecondary)
            }
            Text(
                "Development only. A profile read from a card is shown as an unverified test profile.",
                style = MaterialTheme.typography.bodySmall,
                color = Kipat.colors.textTertiary,
                modifier = Modifier.padding(horizontal = KipatSpacing.gutter),
            )

            SectionHeading("Test profile")
            draft?.let { p ->
                ActionRow(
                    p.displayName,
                    icon = if (p.type == NfcProfileType.ORGANIZATION) KipatSymbols.Badge else KipatSymbols.Person,
                    supporting = listOfNotNull(p.headline ?: p.role, p.organization).joinToString(" · ").ifEmpty { "Name only" },
                    value = "Edit",
                    chevron = true,
                    onClick = onEdit,
                )
                KipatDivider()
            }
            ActionRow(
                "Create test profile",
                icon = KipatSymbols.Edit,
                supporting = "Enter the public fields to put on the card",
                chevron = true,
                onClick = onCreate,
            )
            KipatDivider()
            ActionRow(
                "Load sample profile",
                icon = KipatSymbols.ContactPage,
                supporting = "${NfcProfilePayload.SAMPLE.displayName} · ${NfcProfilePayload.SAMPLE.role}",
                chevron = true,
                onClick = { NfcProfileLab.draft.value = NfcProfilePayload.SAMPLE },
            )

            SectionHeading("Card")
            ActionRow(
                "Read profile card",
                icon = KipatSymbols.Contactless,
                supporting = "Show what a card holds",
                chevron = true,
                enabled = session != null,
                onClick = { session?.armRead() },
            )
            KipatDivider()
            val writeSize = draft?.let { NfcProfileLab.size(it, mode, includeAar, context.packageName) }
            ActionRow(
                "Write profile card",
                icon = KipatSymbols.Nfc,
                supporting = writeSize?.let { "Needs ${it.messageBytes} bytes on the card" } ?: "Create or load a profile first",
                chevron = true,
                enabled = session != null && draft != null,
                onClick = { draft?.let { session?.armWrite(NfcProfileLab.request(it, mode, includeAar, context.packageName)) } },
            )

            StatePanel(
                state = state,
                mode = mode,
                onCancel = { session?.cancel() },
                onScanAnother = { session?.armRead() },
                onPreview = { profile -> RevealLauncher.launch(context, profile.toRevealPayload(RevealSource.NFC_TAG)) },
                onCopy = { label, text -> copy(context, label, text) },
            )

            draft?.let { p ->
                SectionHeading("Size")
                val full = NfcProfileLab.size(p, NfcProfileMode.FULL, includeAar, context.packageName)
                val compact = NfcProfileLab.size(p, NfcProfileMode.COMPACT, includeAar, context.packageName)
                OptionRow("Full", selected = mode == NfcProfileMode.FULL, onSelect = { NfcProfileLab.mode.value = NfcProfileMode.FULL }, supporting = "All fields · ${full.messageBytes} bytes")
                OptionRow("Compact", selected = mode == NfcProfileMode.COMPACT, onSelect = { NfcProfileLab.mode.value = NfcProfileMode.COMPACT }, supporting = "Name, headline, role, organisation, style · ${compact.messageBytes} bytes")
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = KipatSpacing.rowTwoLine)
                        .clickable(role = Role.Switch) { NfcProfileLab.includeAar.value = !includeAar }
                        .padding(horizontal = KipatSpacing.gutter),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Android app record", style = MaterialTheme.typography.bodyLarge, color = Kipat.colors.textPrimary)
                        Text("Opens Kipat on phones without a matching filter · ${full.aarBytes} bytes", style = MaterialTheme.typography.bodyMedium, color = Kipat.colors.textSecondary)
                    }
                    Switch(checked = includeAar, onCheckedChange = null, colors = SwitchDefaults.colors(checkedTrackColor = Kipat.colors.accent))
                }
                val current = if (mode == NfcProfileMode.FULL) full else compact
                ActionRow("Profile JSON", value = "${current.jsonBytes} bytes")
                KipatDivider()
                ActionRow("NDEF message", value = "${current.messageBytes} bytes")
                NfcProfileLab.TYPICAL_CARDS.forEach { (card, capacity) ->
                    KipatDivider()
                    val fits = current.messageBytes <= capacity
                    ActionRow(card, supporting = "Typically $capacity bytes", value = if (fits) "Fits" else "Too large", valueColor = if (fits) Kipat.colors.verified else Kipat.colors.revoked)
                }
                Text(
                    "Typical capacities only. Writing always checks the card's own reported capacity.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Kipat.colors.textTertiary,
                    modifier = Modifier.padding(horizontal = KipatSpacing.gutter, vertical = KipatSpacing.s8),
                )
                KipatTextButton(
                    "Copy profile JSON",
                    onClick = { copy(context, "Kipat test profile JSON", NfcProfileFormat.toJson(p, mode)) },
                    modifier = Modifier.padding(horizontal = KipatSpacing.s8),
                )
            }
        }
    }

    (state as? ProfileTagState.ConfirmReplace)?.let { confirm ->
        AlertDialog(
            onDismissRequest = { session?.cancel() },
            title = { Text("Replace card data?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(KipatSpacing.s8)) {
                    Text("This NFC card already contains data. Writing this Kipat test profile will replace the existing NDEF message.")
                    Text("On the card now:", fontWeight = FontWeight.Medium)
                    confirm.existing.take(6).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = { KipatTextButton("Replace and write", color = Kipat.colors.revoked, onClick = { session?.confirmReplace() }) },
            dismissButton = { KipatTextButton("Cancel", onClick = { session?.cancel() }) },
            containerColor = Kipat.colors.surfaceElevated,
        )
    }
}

@Composable
private fun StatePanel(
    state: ProfileTagState,
    mode: NfcProfileMode,
    onCancel: () -> Unit,
    onScanAnother: () -> Unit,
    onPreview: (NfcProfilePayload) -> Unit,
    onCopy: (String, String) -> Unit,
) {
    val context = LocalContext.current
    when (state) {
        ProfileTagState.Idle -> Unit
        is ProfileTagState.WaitingForCard -> {
            Status(
                if (state.writing) "READY TO WRITE" else "READY TO READ",
                state.note ?: "Hold the card flat against the back of the phone and keep it still.",
                Kipat.colors.accent,
            )
            KipatSecondaryButton("Cancel", onClick = onCancel, modifier = Modifier.fillMaxWidth().padding(KipatSpacing.gutter))
        }
        is ProfileTagState.Working -> Status(state.label.uppercase(), "Keep the card still.", Kipat.colors.accent)
        is ProfileTagState.ConfirmReplace -> Status("WAITING FOR CONFIRMATION", "Keep the card on the phone.", Kipat.colors.warning)
        is ProfileTagState.TooLarge -> {
            Status("PROFILE TOO LARGE", "This profile is too large for this NFC card. Nothing was written.", Kipat.colors.revoked)
            ActionRow("Required", value = "${state.required} bytes")
            KipatDivider()
            ActionRow("Available", value = "${state.available} bytes")
            SectionHeading("To make it fit")
            state.fieldCosts.forEach { (field, bytes) -> ActionRow("Remove $field", value = "−$bytes bytes") }
            if (mode == NfcProfileMode.FULL) ActionRow("Switch to Compact", supporting = "Drops bio, links, email and avatar URL")
            ActionRow("Use a larger card", supporting = "NTAG215 (≈496 bytes) or NTAG216 (≈868 bytes)")
        }
        is ProfileTagState.Failed -> {
            Status(state.title.uppercase(), state.detail ?: "Try again.", Kipat.colors.revoked)
        }
        is ProfileTagState.Verified -> {
            Status("PROFILE WRITE VERIFIED", "Read back from the card and identical to what was written · ${state.messageBytes} of ${state.tag.maxSize ?: "?"} bytes.", Kipat.colors.verified)
            ProfileFields(state.profile)
            TagFields(state.tag)
            Actions(
                onPreview = { onPreview(state.profile) },
                onCopyJson = { onCopy("Kipat test profile JSON", NfcProfileFormat.toJson(state.profile, mode)) },
                onCopyReport = { onCopy("Kipat NFC diagnostic report", report(context, state.tag)) },
                onScanAnother = onScanAnother,
            )
        }
        is ProfileTagState.Detected -> {
            val raw = state.tag.profileRecord?.payload
            when (val p = state.profile) {
                null -> Status(
                    if (state.tag.records == null) "CARD UNREADABLE" else "NO KIPAT PROFILE",
                    state.tag.readError ?: if (state.tag.records.isNullOrEmpty()) "The card is blank." else "The card holds other data (${state.tag.records.size} record(s)).",
                    Kipat.colors.warning,
                )
                is NfcProfileParse.Valid -> Status("KIPAT TEST PROFILE DETECTED", "Unverified test data · version ${p.profile.version} · ${p.payloadBytes} bytes", Kipat.colors.accent)
                is NfcProfileParse.UnsupportedVersion -> Status("Unsupported Kipat NFC profile version", "This card uses version ${p.version}. Only version ${NfcProfileFormat.VERSION} is supported.", Kipat.colors.warning)
                is NfcProfileParse.Invalid -> Status("Kipat profile detected but invalid", p.reason.replaceFirstChar { it.uppercase() } + ".", Kipat.colors.revoked)
            }
            (state.profile as? NfcProfileParse.Valid)?.let { valid ->
                ProfileFields(valid.profile)
                valid.warnings.forEach { w -> ActionRow("Ignored: ${w.field}", supporting = w.message, valueColor = Kipat.colors.warning) }
            }
            TagFields(state.tag)
            Actions(
                onPreview = (state.profile as? NfcProfileParse.Valid)?.let { v -> { onPreview(v.profile) } },
                onCopyJson = raw?.let { bytes -> { onCopy("Kipat test profile JSON", String(bytes, Charsets.UTF_8)) } },
                onCopyReport = { onCopy("Kipat NFC diagnostic report", report(context, state.tag)) },
                onScanAnother = onScanAnother,
            )
        }
    }
}

@Composable
private fun Status(title: String, detail: String, tone: Color) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = KipatSpacing.gutter, vertical = KipatSpacing.s16)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = tone, modifier = Modifier.semantics { heading() })
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = Kipat.colors.textSecondary)
    }
}

@Composable
private fun ProfileFields(p: NfcProfilePayload) {
    SectionHeading("Profile on the card")
    val rows = listOfNotNull(
        "Name" to p.displayName,
        "Type" to if (p.type == NfcProfileType.ORGANIZATION) "Organization" else "Person",
        p.headline?.let { "Headline" to it },
        p.role?.let { "Role" to it },
        p.organization?.let { "Organization" to it },
        p.bio?.let { "Bio" to it },
        p.avatarUrl?.let { "Avatar URL" to it },
        p.website?.let { "Website" to it },
        p.github?.let { "GitHub" to it },
        p.linkedin?.let { "LinkedIn" to it },
        p.publicEmail?.let { "Public email" to it },
        p.accent?.let { "Accent" to it.label },
        p.reveal?.let { "Reveal" to it.label },
        p.id?.let { "ID" to it },
        "Authenticity" to "Unverified · Test profile",
    )
    // Values are shown as plain text: links on a card are never opened automatically.
    rows.forEachIndexed { i, (label, value) ->
        if (i > 0) KipatDivider()
        ActionRow(label, supporting = value)
    }
}

@Composable
private fun TagFields(tag: TagSnapshot) {
    SectionHeading("Card")
    val mono = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
    val rows = listOf(
        "Tag UID" to (tag.uidHex ?: "Unknown"),
        "Technologies" to tag.techs.joinToString().ifEmpty { "None" },
        "NDEF type" to (tag.ndefType ?: if (tag.formatable) "Blank (NDEF-formatable)" else "Not NDEF"),
        "Capacity" to (tag.maxSize?.let { "$it bytes" } ?: "Unknown"),
        "Writable" to when (tag.writable) { true -> "Yes"; false -> "No (read-only)"; null -> "Unknown" },
        "Message size" to (tag.messageSize?.let { "$it bytes · ${tag.records?.size} record(s)" } ?: "Unreadable"),
    )
    rows.forEachIndexed { i, (label, value) ->
        if (i > 0) KipatDivider()
        ActionRow(label, value = value)
    }
    tag.records?.takeIf { it.isNotEmpty() }?.let { records ->
        Text(
            records.mapIndexed { i, r -> "${i + 1}. ${r.summary()}" }.joinToString("\n"),
            style = mono,
            color = Kipat.colors.textSecondary,
            modifier = Modifier.padding(horizontal = KipatSpacing.gutter, vertical = KipatSpacing.s8),
        )
    }
}

@Composable
private fun Actions(onPreview: (() -> Unit)?, onCopyJson: (() -> Unit)?, onCopyReport: () -> Unit, onScanAnother: () -> Unit) {
    Column(Modifier.padding(KipatSpacing.gutter), verticalArrangement = Arrangement.spacedBy(KipatSpacing.s8)) {
        onPreview?.let { KipatPrimaryButton("Preview Reveal", icon = KipatSymbols.Play, onClick = it, modifier = Modifier.fillMaxWidth()) }
        onCopyJson?.let { KipatSecondaryButton("Copy JSON", icon = KipatSymbols.Copy, onClick = it, modifier = Modifier.fillMaxWidth()) }
        KipatSecondaryButton("Copy diagnostic report", icon = KipatSymbols.Copy, onClick = onCopyReport, modifier = Modifier.fillMaxWidth())
        KipatSecondaryButton("Scan another", icon = KipatSymbols.Contactless, onClick = onScanAnother, modifier = Modifier.fillMaxWidth())
    }
}

private fun report(context: Context, tag: TagSnapshot): String {
    val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "unknown"
    return NfcProfileDiagnostics.report(tag, "Kipat $version", Build.VERSION.SDK_INT, "${Build.MANUFACTURER} ${Build.MODEL}")
}

private fun copy(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    // Android 13+ shows its own clipboard confirmation.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}
