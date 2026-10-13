package com.kipat.probe.ui.screens.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kipat.probe.data.KipatRepository
import com.kipat.probe.data.toRevealPayload
import com.kipat.probe.model.Entitlement
import com.kipat.probe.reveal.RevealLauncher
import com.kipat.probe.reveal.RevealSource
import com.kipat.probe.reveal.RevealTemplateId
import com.kipat.probe.reveal.RevealTheme
import com.kipat.probe.reveal.TemplateSupport
import com.kipat.probe.ui.components.KipatPrimaryButton
import com.kipat.probe.ui.components.KipatSymbols
import com.kipat.probe.ui.components.KipatTopBar
import com.kipat.probe.ui.components.OptionRow
import com.kipat.probe.ui.components.SectionHeading
import com.kipat.probe.ui.theme.Kipat
import com.kipat.probe.ui.theme.KipatSpacing

/** Choose how your Reveal plays for others. Changes apply immediately. */
@Composable
fun RevealStyleScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val identity by KipatRepository.identity.collectAsStateWithLifecycle()
    val card by KipatRepository.card.collectAsStateWithLifecycle()
    val entitlement by KipatRepository.entitlement.collectAsStateWithLifecycle()
    val (ready, preview) = RevealTemplateId.entries.partition { it.support == TemplateSupport.FULL }

    Scaffold(
        containerColor = Kipat.colors.background,
        topBar = { KipatTopBar("Reveal style", navigationIcon = KipatSymbols.ArrowBack, onNavigate = onBack) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = KipatSpacing.s32),
        ) {
            SectionHeading("Style")
            ready.forEach { t -> StyleOption(t, identity.revealTemplate == t, true) { KipatRepository.publishIdentity(identity.copy(revealTemplate = t)) } }
            SectionHeading("Early styles")
            preview.forEach { t ->
                val locked = t.requiresPlus && entitlement != Entitlement.PLUS
                StyleOption(t, identity.revealTemplate == t, !locked) { KipatRepository.publishIdentity(identity.copy(revealTemplate = t)) }
            }
            SectionHeading("Accent")
            RevealTheme.entries.forEach { theme ->
                OptionRow(theme.label, selected = identity.revealTheme == theme, onSelect = { KipatRepository.publishIdentity(identity.copy(revealTheme = theme)) })
            }
            KipatPrimaryButton(
                "Preview Reveal",
                icon = KipatSymbols.Play,
                onClick = { RevealLauncher.launch(context, identity.toRevealPayload(RevealSource.PREVIEW, isDemo = card.isDemo, authenticity = card.authenticity)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(KipatSpacing.gutter),
            )
        }
    }
}

@Composable
private fun StyleOption(t: RevealTemplateId, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    OptionRow(
        label = t.label,
        supporting = if (enabled) t.description else "Needs Kipat Plus, not available yet",
        selected = selected,
        enabled = enabled,
        onSelect = onSelect,
    )
}
