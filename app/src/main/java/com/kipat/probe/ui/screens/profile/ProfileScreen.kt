package com.kipat.probe.ui.screens.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kipat.probe.data.KipatRepository
import com.kipat.probe.data.toRevealPayload
import com.kipat.probe.model.IdentityType
import com.kipat.probe.model.ProfileField
import com.kipat.probe.model.ProfileTemplateId
import com.kipat.probe.model.StatusTone
import com.kipat.probe.model.initialsOf
import com.kipat.probe.reveal.RevealConfig
import com.kipat.probe.reveal.RevealEngine
import com.kipat.probe.reveal.RevealLauncher
import com.kipat.probe.reveal.RevealSource
import com.kipat.probe.reveal.RevealTemplateId
import com.kipat.probe.reveal.RevealTheme
import com.kipat.probe.reveal.TemplateSupport
import com.kipat.probe.reveal.effects.ParticleField
import com.kipat.probe.reveal.effects.RevealEffects.drawReveal
import com.kipat.probe.reveal.effects.RevealGeometry
import com.kipat.probe.ui.components.KipatIdentityCard
import com.kipat.probe.ui.components.KipatPrimaryButton
import com.kipat.probe.ui.components.KipatSecondaryButton
import com.kipat.probe.ui.components.KipatSectionHeader
import com.kipat.probe.ui.components.KipatTemplateCard
import com.kipat.probe.ui.components.StatusPill
import com.kipat.probe.ui.theme.Kipat
import com.kipat.probe.ui.theme.KipatSpacing
import com.kipat.probe.ui.theme.RevealPalette
import kotlinx.coroutines.launch

/** Controlled identity editor: pick a type and templates, fill approved fields, publish locally. */
@Composable
fun ProfileScreen(snackbar: SnackbarHostState, vm: ProfileViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val published by KipatRepository.identity.collectAsStateWithLifecycle()
    val card by KipatRepository.card.collectAsStateWithLifecycle()
    val dirty = draft != published
    val errors = remember(draft) { vm.errors() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = KipatSpacing.xxl),
    ) {
        item {
            Row(
                Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = KipatSpacing.gutter, vertical = KipatSpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Profile", style = MaterialTheme.typography.headlineMedium, color = Kipat.colors.textPrimary, modifier = Modifier.weight(1f).semantics { heading() })
                StatusPill(
                    label = if (dirty) "Unpublished changes" else "Published locally",
                    tone = if (dirty) StatusTone.WARNING else StatusTone.POSITIVE,
                )
            }
        }
        item {
            KipatIdentityCard(
                displayName = draft.displayName,
                subtitle = draft.subtitle,
                organization = draft.organization,
                initials = initialsOf(draft.displayName),
                authenticity = card.authenticity,
                isDemo = card.isDemo,
                eyebrow = "${draft.type.label} · ${draft.profileTemplate.label}",
                modifier = Modifier.padding(horizontal = KipatSpacing.gutter),
            ) {
                KipatPrimaryButton(
                    text = "Preview Reveal",
                    icon = Icons.Filled.PlayArrow,
                    onClick = {
                        RevealLauncher.launch(context, draft.toRevealPayload(RevealSource.PREVIEW, isDemo = card.isDemo, authenticity = card.authenticity))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item { KipatSectionHeader("Identity type", Modifier.padding(horizontal = KipatSpacing.gutter)) }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = KipatSpacing.gutter), horizontalArrangement = Arrangement.spacedBy(KipatSpacing.sm)) {
                items(IdentityType.entries.filter { ProfileTemplateId.suggestedFor(it).available }) { type ->
                    FilterChip(
                        selected = draft.type == type,
                        onClick = { vm.setType(type) },
                        label = { Text(type.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Kipat.colors.accent.copy(alpha = 0.16f),
                            selectedLabelColor = Kipat.colors.textPrimary,
                        ),
                    )
                }
            }
        }

        item { KipatSectionHeader("Profile template", Modifier.padding(horizontal = KipatSpacing.gutter)) }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = KipatSpacing.gutter), horizontalArrangement = Arrangement.spacedBy(KipatSpacing.md)) {
                items(ProfileTemplateId.entries) { t ->
                    KipatTemplateCard(
                        title = t.label,
                        description = t.description,
                        selected = draft.profileTemplate == t,
                        enabled = t.available,
                        tag = if (t.available) "${t.fields.size} fields" else "Coming later",
                        onSelect = { vm.setTemplate(t) },
                    )
                }
            }
        }

        item { KipatSectionHeader("Reveal", Modifier.padding(horizontal = KipatSpacing.gutter)) }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = KipatSpacing.gutter), horizontalArrangement = Arrangement.spacedBy(KipatSpacing.md)) {
                items(RevealTemplateId.entries) { t ->
                    KipatTemplateCard(
                        title = t.label,
                        description = t.description,
                        selected = draft.revealTemplate == t,
                        tag = buildString {
                            append(if (t.support == TemplateSupport.FULL) "Full" else "Preview")
                            if (t.requiresPlus) append(" · Plus")
                        },
                        onSelect = { vm.setReveal(t) },
                        preview = { RevealThumbnail(t, draft.revealTheme) },
                    )
                }
            }
        }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = KipatSpacing.gutter, vertical = KipatSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(KipatSpacing.sm),
            ) {
                items(RevealTheme.entries) { theme ->
                    FilterChip(
                        selected = draft.revealTheme == theme,
                        onClick = { vm.setTheme(theme) },
                        label = { Text(theme.label) },
                    )
                }
            }
        }

        item {
            KipatSectionHeader("Approved fields", Modifier.padding(horizontal = KipatSpacing.gutter))
            Text(
                "Kipat profiles are built from these fields only — no custom code or markup.",
                style = MaterialTheme.typography.bodySmall,
                color = Kipat.colors.textTertiary,
                modifier = Modifier.padding(horizontal = KipatSpacing.gutter),
            )
            Spacer(Modifier.height(KipatSpacing.sm))
        }
        items(draft.profileTemplate.fields, key = { it.name }) { field ->
            val error = errors[field]
            OutlinedTextField(
                value = draft.fields[field].orEmpty(),
                onValueChange = { vm.setField(field, it) },
                label = { Text(field.label) },
                placeholder = { Text(field.hint) },
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                singleLine = field != ProfileField.BIO,
                minLines = if (field == ProfileField.BIO) 2 else 1,
                keyboardOptions = KeyboardOptions(
                    keyboardType = when (field) {
                        ProfileField.PUBLIC_EMAIL -> KeyboardType.Email
                        ProfileField.WEBSITE, ProfileField.LINKEDIN -> KeyboardType.Uri
                        else -> KeyboardType.Text
                    },
                    imeAction = ImeAction.Next,
                ),
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Kipat.colors.accent, focusedLabelColor = Kipat.colors.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = KipatSpacing.gutter, vertical = KipatSpacing.xs),
            )
        }

        item {
            Spacer(Modifier.height(KipatSpacing.xl))
            Column(Modifier.padding(horizontal = KipatSpacing.gutter), verticalArrangement = Arrangement.spacedBy(KipatSpacing.sm)) {
                KipatPrimaryButton(
                    text = "Publish locally",
                    enabled = dirty,
                    onClick = {
                        val ok = vm.publish()
                        scope.launch {
                            snackbar.showSnackbar(if (ok) "Published on this device. Your Reveal now uses it." else "Fix the highlighted fields first")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (dirty) {
                    KipatSecondaryButton("Discard changes", onClick = vm::discard, modifier = Modifier.fillMaxWidth())
                }
                Text(
                    "Publishing is local to this device for now — nothing is uploaded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Kipat.colors.textTertiary,
                )
            }
        }
    }
}

/** Static thumbnail drawn by the real Reveal engine at a mid-animation frame. */
@Composable
private fun RevealThumbnail(template: RevealTemplateId, theme: RevealTheme) {
    val config = remember(template) { RevealConfig.forTemplate(template).let { it.copy(particleCount = it.particleCount.coerceAtMost(24)) } }
    val particles = remember(config) { ParticleField(config.particleCount.coerceAtLeast(1)) }
    val palette = remember(theme) { RevealPalette.of(theme) }
    val frame = remember(config) { RevealEngine.frameAt((config.timeline.surfaceStart * 0.8f).toLong(), config.timeline) }
    Canvas(Modifier.fillMaxSize()) {
        val card = Rect(Offset(size.width * 0.3f, size.height * 0.28f), Offset(size.width * 0.7f, size.height * 0.78f))
        val geo = RevealGeometry(Offset(size.width / 2, size.height * 0.1f), card.center, card, size.maxDimension * 0.6f)
        drawRect(palette.scrimBottom)
        drawReveal(frame.copy(surface = 0f), config, geo, particles, palette.primary, palette.secondary, null, null, null)
        drawRoundRect(palette.cardBorder, card.topLeft, card.size, androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
    }
}
