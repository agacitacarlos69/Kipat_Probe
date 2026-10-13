package com.kipat.probe.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kipat.probe.data.KipatRepository
import com.kipat.probe.data.toRevealPayload
import com.kipat.probe.reveal.RevealLauncher
import com.kipat.probe.reveal.RevealSource
import com.kipat.probe.reveal.RevealStyleDraft
import com.kipat.probe.reveal.RevealTemplateGlyph
import com.kipat.probe.reveal.RevealTemplateId
import com.kipat.probe.reveal.RevealTheme
import com.kipat.probe.ui.components.KipatDivider
import com.kipat.probe.ui.components.KipatPrimaryButton
import com.kipat.probe.ui.components.KipatSecondaryButton
import com.kipat.probe.ui.components.KipatSymbols
import com.kipat.probe.ui.components.KipatTopBar
import com.kipat.probe.ui.components.OptionRow
import com.kipat.probe.ui.components.SectionHeading
import com.kipat.probe.ui.theme.Kipat
import com.kipat.probe.ui.theme.KipatSpacing

/**
 * Choose how your Reveal plays for others. Selecting a style only changes the draft: Preview
 * plays it, Apply commits it. Nothing changes for others until Apply.
 */
@Composable
fun RevealStyleScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val identity by KipatRepository.identity.collectAsStateWithLifecycle()
    val card by KipatRepository.card.collectAsStateWithLifecycle()
    // Wire IDs are saveable strings; the draft survives rotation.
    var pendingTemplate by rememberSaveable { mutableStateOf(identity.revealTemplate.wireId) }
    var pendingTheme by rememberSaveable { mutableStateOf(identity.revealTheme.wireId) }
    val draft = RevealStyleDraft(
        appliedTemplate = identity.revealTemplate,
        appliedTheme = identity.revealTheme,
        template = RevealTemplateId.fromWireId(pendingTemplate),
        theme = RevealTheme.fromWireId(pendingTheme),
    )

    Scaffold(
        containerColor = Kipat.colors.background,
        topBar = { KipatTopBar("Reveal style", navigationIcon = KipatSymbols.ArrowBack, onNavigate = onBack) },
        bottomBar = {
            Column(Modifier.navigationBarsPadding()) {
                KipatDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(KipatSpacing.gutter),
                    horizontalArrangement = Arrangement.spacedBy(KipatSpacing.s12),
                ) {
                    KipatSecondaryButton(
                        "Preview",
                        icon = KipatSymbols.Play,
                        onClick = {
                            val previewIdentity = identity.copy(revealTemplate = draft.template, revealTheme = draft.theme)
                            RevealLauncher.launch(context, previewIdentity.toRevealPayload(RevealSource.PREVIEW, isDemo = card.isDemo, authenticity = card.authenticity))
                        },
                        modifier = Modifier.weight(1f),
                    )
                    KipatPrimaryButton(
                        if (draft.hasChanges) "Apply" else "Applied",
                        onClick = {
                            val applied = draft.apply()
                            KipatRepository.publishIdentity(identity.copy(revealTemplate = applied.appliedTemplate, revealTheme = applied.appliedTheme))
                        },
                        enabled = draft.hasChanges,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = KipatSpacing.s24),
        ) {
            SectionHeading("Style")
            Column(Modifier.selectableGroup()) {
                RevealTemplateId.entries.forEach { t ->
                    TemplateRow(
                        template = t,
                        selected = draft.template == t,
                        current = draft.appliedTemplate == t,
                        onSelect = { pendingTemplate = t.wireId },
                    )
                }
            }
            SectionHeading("Accent")
            Column(Modifier.selectableGroup()) {
                RevealTheme.entries.forEach { theme ->
                    OptionRow(
                        theme.label,
                        selected = draft.theme == theme,
                        supporting = if (draft.appliedTheme == theme) "Current" else null,
                        onSelect = { pendingTheme = theme.wireId },
                    )
                }
            }
        }
    }
}

/** Thumbnail, name, one-line character and a radio. Row tap selects; it does not apply. */
@Composable
private fun TemplateRow(template: RevealTemplateId, selected: Boolean, current: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = KipatSpacing.rowTwoLine + 8.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = KipatSpacing.gutter, vertical = KipatSpacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RevealTemplateGlyph(template)
        Spacer(Modifier.width(KipatSpacing.s16))
        Column(Modifier.weight(1f)) {
            Text(template.label, style = MaterialTheme.typography.titleMedium, color = Kipat.colors.textPrimary)
            Text(template.description, style = MaterialTheme.typography.bodyMedium, color = Kipat.colors.textSecondary)
            if (current) Text("Current", style = MaterialTheme.typography.bodySmall, color = Kipat.colors.textTertiary)
        }
        // Decorative: the row itself is the selectable control.
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = Kipat.colors.accent, unselectedColor = Kipat.colors.outline),
        )
    }
}
