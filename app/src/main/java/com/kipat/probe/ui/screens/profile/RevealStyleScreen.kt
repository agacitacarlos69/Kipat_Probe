package com.kipat.probe.ui.screens.profile

import android.animation.ValueAnimator
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kipat.probe.data.KipatRepository
import com.kipat.probe.data.toRevealPayload
import com.kipat.probe.reveal.RevealChoreography
import com.kipat.probe.reveal.RevealLauncher
import com.kipat.probe.reveal.RevealSource
import com.kipat.probe.reveal.RevealStage
import com.kipat.probe.reveal.RevealStyleDraft
import com.kipat.probe.reveal.RevealTemplateGlyph
import com.kipat.probe.reveal.RevealTemplateId
import com.kipat.probe.reveal.RevealTheme
import com.kipat.probe.ui.components.KipatDivider
import com.kipat.probe.ui.components.KipatPrimaryButton
import com.kipat.probe.ui.components.KipatSecondaryButton
import com.kipat.probe.ui.components.KipatSymbols
import com.kipat.probe.ui.components.KipatTopBar
import com.kipat.probe.ui.components.SectionHeading
import com.kipat.probe.ui.theme.Kipat
import com.kipat.probe.ui.theme.KipatSpacing
import com.kipat.probe.ui.theme.RevealPalette

/**
 * Choose how your Reveal plays for others. A live preview at the top shows the recipient's view
 * of the selection; selecting only changes the draft; Preview plays it full screen; Apply commits
 * it. A full screen rather than a bottom sheet: see design/reveal-surface/surface-scorecard.md.
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
    var replay by remember { mutableIntStateOf(0) }

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
            Text(
                "Used by your phone and linked Kipat cards",
                style = MaterialTheme.typography.bodyMedium,
                color = Kipat.colors.textSecondary,
                modifier = Modifier.padding(horizontal = KipatSpacing.gutter, vertical = KipatSpacing.s8),
            )
            val previewPayload = identity.copy(revealTemplate = draft.template, revealTheme = draft.theme)
                .toRevealPayload(RevealSource.NFC_TAG, isDemo = card.isDemo, authenticity = card.authenticity)
            LivePreview(previewPayload, replay)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = KipatSpacing.gutter, end = KipatSpacing.s8, top = KipatSpacing.s12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(draft.template.label, style = MaterialTheme.typography.titleMedium, color = Kipat.colors.textPrimary)
                    Text(
                        if (draft.hasChanges) "${draft.template.description}. Not applied yet" else draft.template.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Kipat.colors.textSecondary,
                    )
                }
                TextButton(onClick = { replay++ }) {
                    Icon(KipatSymbols.Replay, contentDescription = null, tint = Kipat.colors.textSecondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(KipatSpacing.s8))
                    Text("Replay", style = MaterialTheme.typography.labelLarge, color = Kipat.colors.textPrimary)
                }
            }

            SectionHeading("Style")
            Column(Modifier.selectableGroup().padding(horizontal = KipatSpacing.s8)) {
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
            AccentSwatches(
                selected = draft.theme,
                applied = draft.appliedTheme,
                onSelect = { pendingTheme = it.wireId },
            )
        }
    }
}

/**
 * The recipient's view of the draft, scaled into a panel. Plays once when the selection changes
 * (or on Replay), then holds the settled frame; static when system animations are off.
 */
@Composable
private fun LivePreview(payload: com.kipat.probe.reveal.RevealPayload, replay: Int) {
    val choreography = remember(payload.template) { RevealChoreography.forTemplate(payload.template) }
    val total = choreography.timeline.totalDuration.toLong()
    var ms by remember { mutableLongStateOf(total) }
    LaunchedEffect(payload.template, payload.theme, replay) {
        if (!ValueAnimator.areAnimatorsEnabled()) {
            ms = total
            return@LaunchedEffect
        }
        val start = withFrameMillis { it }
        do {
            ms = (withFrameMillis { it } - start).coerceAtMost(total)
        } while (ms < total)
    }
    val panelHeight = 300.dp
    val stageHeight = 520.dp
    val scale = panelHeight / stageHeight
    val palette = RevealPalette.of(payload.theme, isSystemInDarkTheme())
    Box(
        Modifier
            .padding(horizontal = KipatSpacing.gutter)
            .fillMaxWidth()
            .height(panelHeight)
            .clip(MaterialTheme.shapes.large)
            .background(palette.scrim.copy(alpha = palette.scrimPeak).compositeOver(Kipat.colors.background))
            .border(1.dp, Kipat.colors.divider, MaterialTheme.shapes.large)
            .clearAndSetSemantics { contentDescription = "Preview of ${payload.template.label} with the ${payload.theme.label} accent" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .requiredSize(400.dp, stageHeight)
                .graphicsLayer { scaleX = scale; scaleY = scale },
        ) {
            RevealStage(
                payload = payload,
                choreography = choreography,
                elapsedMs = { ms },
                connected = false,
                onConnect = {}, onSaveContact = {}, onOpenProfile = {}, onDismiss = {},
                contained = true,
            )
        }
    }
}

/** Thumbnail, name, character; selection is a tonal row and a check, not a radio among borders. */
@Composable
private fun TemplateRow(template: RevealTemplateId, selected: Boolean, current: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = KipatSpacing.rowTwoLine)
            .clip(MaterialTheme.shapes.medium)
            .background(if (selected) Kipat.colors.surfaceSecondary else Kipat.colors.background)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = KipatSpacing.s8, vertical = KipatSpacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RevealTemplateGlyph(template)
        Spacer(Modifier.width(KipatSpacing.s16))
        Column(Modifier.weight(1f)) {
            Text(template.label, style = MaterialTheme.typography.titleMedium, color = Kipat.colors.textPrimary)
            Text(template.description, style = MaterialTheme.typography.bodyMedium, color = Kipat.colors.textSecondary)
            if (current) Text("Current", style = MaterialTheme.typography.bodySmall, color = Kipat.colors.textTertiary)
        }
        Box(Modifier.size(KipatSpacing.touchTarget), contentAlignment = Alignment.Center) {
            if (selected) Icon(KipatSymbols.Check, contentDescription = null, tint = Kipat.colors.accent)
        }
    }
}

/** The accents as actual colour swatches with their names; the selected one is ringed and checked. */
@Composable
private fun AccentSwatches(selected: RevealTheme, applied: RevealTheme, onSelect: (RevealTheme) -> Unit) {
    val dark = isSystemInDarkTheme()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = KipatSpacing.s8)
            .selectableGroup(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        RevealTheme.entries.forEach { theme ->
            val p = RevealPalette.of(theme, dark)
            val isSelected = theme == selected
            Column(
                Modifier
                    .widthIn(min = 72.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(theme) })
                    .padding(vertical = KipatSpacing.s8),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .border(2.dp, if (isSelected) Kipat.colors.textPrimary else Kipat.colors.background, CircleShape)
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(p.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) Icon(KipatSymbols.Check, contentDescription = null, tint = p.onAccent, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.height(KipatSpacing.s4))
                Text(theme.label, style = MaterialTheme.typography.bodySmall, color = Kipat.colors.textPrimary)
                Text(if (theme == applied) "Current" else " ", style = MaterialTheme.typography.bodySmall, color = Kipat.colors.textTertiary)
            }
        }
    }
}
