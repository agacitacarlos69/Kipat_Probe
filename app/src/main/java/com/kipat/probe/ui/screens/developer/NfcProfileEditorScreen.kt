package com.kipat.probe.ui.screens.developer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kipat.probe.nfc.profile.NfcProfileLimits
import com.kipat.probe.nfc.profile.NfcProfilePayload
import com.kipat.probe.nfc.profile.NfcProfileType
import com.kipat.probe.nfc.profile.NfcProfileValidator
import com.kipat.probe.reveal.RevealTemplateId
import com.kipat.probe.reveal.RevealTheme
import com.kipat.probe.ui.components.KipatSymbols
import com.kipat.probe.ui.components.KipatTextButton
import com.kipat.probe.ui.components.KipatTopBar
import com.kipat.probe.ui.components.OptionRow
import com.kipat.probe.ui.components.SectionHeading
import com.kipat.probe.ui.theme.Kipat
import com.kipat.probe.ui.theme.KipatSpacing

private data class Field(val key: String, val label: String, val max: Int, val keyboard: KeyboardType = KeyboardType.Text, val words: Boolean = true, val multiline: Boolean = false)

private val TextFields = listOf(
    Field("name", "Display name *", NfcProfileLimits.NAME),
    Field("headline", "Headline", NfcProfileLimits.HEADLINE),
    Field("role", "Role", NfcProfileLimits.ROLE),
    Field("organization", "Organization", NfcProfileLimits.ORGANIZATION),
    Field("bio", "Bio", NfcProfileLimits.BIO, multiline = true),
    Field("avatarUrl", "Avatar URL (https)", NfcProfileLimits.URL, KeyboardType.Uri, words = false),
    Field("website", "Website", NfcProfileLimits.URL, KeyboardType.Uri, words = false),
    Field("github", "GitHub", NfcProfileLimits.URL, KeyboardType.Uri, words = false),
    Field("linkedin", "LinkedIn", NfcProfileLimits.URL, KeyboardType.Uri, words = false),
    Field("publicEmail", "Public email", NfcProfileLimits.EMAIL, KeyboardType.Email, words = false),
)

/** The three accents the test offers (Graphite stays readable on the wire but isn't offered). */
private val Accents = listOf(RevealTheme.NIGHT, RevealTheme.AURORA, RevealTheme.EMBER)

/**
 * Create / edit the test profile. Sizes update with every keystroke; Save is only enabled when
 * every field passes the same rules the reader applies.
 */
@Composable
fun NfcProfileEditorScreen(startBlank: Boolean, onDone: () -> Unit) {
    val context = LocalContext.current
    val initial = remember { if (startBlank) null else NfcProfileLab.draft.value }
    val mode by NfcProfileLab.mode.collectAsStateWithLifecycle()
    val includeAar by NfcProfileLab.includeAar.collectAsStateWithLifecycle()
    var values by rememberSaveable { mutableStateOf<Map<String, String>>(HashMap(initial?.toFieldMap().orEmpty())) }
    var type by rememberSaveable { mutableStateOf(initial?.type ?: NfcProfileType.PERSON) }
    var accent by rememberSaveable { mutableStateOf(initial?.accent ?: RevealTheme.NIGHT) }
    var template by rememberSaveable { mutableStateOf(initial?.reveal ?: RevealTemplateId.DEFAULT) }

    val profile = buildProfile(initial?.id, values, type, accent, template)
    val issues = NfcProfileValidator.issues(profile).associate { it.field to it.message }
    val size = NfcProfileLab.size(profile, mode, includeAar, context.packageName)

    Scaffold(
        containerColor = Kipat.colors.background,
        topBar = {
            KipatTopBar("Test profile", navigationIcon = KipatSymbols.Close, navigationDescription = "Close", onNavigate = onDone) {
                KipatTextButton("Save", enabled = issues.isEmpty(), onClick = {
                    NfcProfileLab.draft.value = profile
                    onDone()
                })
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = KipatSpacing.s32),
        ) {
            Text(
                "${size.messageBytes} bytes on the card · ${size.jsonBytes} bytes of profile JSON (${mode.name.lowercase()})",
                style = MaterialTheme.typography.bodyMedium,
                color = Kipat.colors.textPrimary,
                modifier = Modifier
                    .padding(horizontal = KipatSpacing.gutter, vertical = KipatSpacing.s8)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
            Text(
                NFC_PROFILE_PRIVACY_NOTE,
                style = MaterialTheme.typography.bodySmall,
                color = Kipat.colors.textSecondary,
                modifier = Modifier.padding(horizontal = KipatSpacing.gutter),
            )

            SectionHeading("Details")
            TextFields.forEach { f ->
                val value = values[f.key].orEmpty()
                val error = issues[f.key]
                OutlinedTextField(
                    value = value,
                    onValueChange = { values = values + (f.key to it) },
                    label = { Text(f.label) },
                    isError = error != null,
                    supportingText = { Text(error?.replaceFirstChar { it.uppercase() } ?: "${value.length} / ${f.max}") },
                    singleLine = !f.multiline,
                    minLines = if (f.multiline) 2 else 1,
                    keyboardOptions = KeyboardOptions(
                        capitalization = if (f.words) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
                        keyboardType = f.keyboard,
                        imeAction = ImeAction.Next,
                    ),
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Kipat.colors.accent,
                        focusedLabelColor = Kipat.colors.accent,
                        unfocusedBorderColor = Kipat.colors.outline,
                        unfocusedContainerColor = Kipat.colors.surface,
                        focusedContainerColor = Kipat.colors.surface,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = KipatSpacing.gutter, vertical = KipatSpacing.s4),
                )
            }

            SectionHeading("Profile type")
            OptionRow("Person", selected = type == NfcProfileType.PERSON, onSelect = { type = NfcProfileType.PERSON })
            OptionRow("Organization", selected = type == NfcProfileType.ORGANIZATION, onSelect = { type = NfcProfileType.ORGANIZATION })

            SectionHeading("Accent")
            Accents.forEach { a -> OptionRow(a.label, selected = accent == a, onSelect = { accent = a }) }

            SectionHeading("Reveal template")
            RevealTemplateId.entries.forEach { t -> OptionRow(t.label, selected = template == t, onSelect = { template = t }, supporting = t.description) }
        }
    }
}

private fun NfcProfilePayload.toFieldMap(): Map<String, String> = buildMap {
    put("name", displayName)
    headline?.let { put("headline", it) }
    role?.let { put("role", it) }
    organization?.let { put("organization", it) }
    bio?.let { put("bio", it) }
    avatarUrl?.let { put("avatarUrl", it) }
    website?.let { put("website", it) }
    github?.let { put("github", it) }
    linkedin?.let { put("linkedin", it) }
    publicEmail?.let { put("publicEmail", it) }
}

private fun buildProfile(
    id: String?,
    v: Map<String, String>,
    type: NfcProfileType,
    accent: RevealTheme,
    template: RevealTemplateId,
): NfcProfilePayload {
    fun opt(key: String) = v[key]?.trim()?.takeIf { it.isNotEmpty() }
    return NfcProfilePayload(
        id = id,
        type = type,
        displayName = v["name"].orEmpty().trim(),
        headline = opt("headline"),
        organization = opt("organization"),
        role = opt("role"),
        bio = opt("bio"),
        avatarUrl = opt("avatarUrl"),
        website = opt("website"),
        github = opt("github"),
        linkedin = opt("linkedin"),
        publicEmail = opt("publicEmail"),
        accent = accent,
        reveal = template,
    )
}
