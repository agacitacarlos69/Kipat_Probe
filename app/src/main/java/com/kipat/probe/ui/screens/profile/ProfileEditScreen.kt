package com.kipat.probe.ui.screens.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kipat.probe.data.KipatRepository
import com.kipat.probe.model.IdentityType
import com.kipat.probe.model.ProfileField
import com.kipat.probe.model.ProfileTemplateId
import com.kipat.probe.ui.components.ActionRow
import com.kipat.probe.ui.components.KipatSymbols
import com.kipat.probe.ui.components.KipatTextButton
import com.kipat.probe.ui.components.KipatTopBar
import com.kipat.probe.ui.components.SectionHeading
import com.kipat.probe.ui.theme.Kipat
import com.kipat.probe.ui.theme.KipatSpacing
import kotlinx.coroutines.launch

private data class FieldGroup(val title: String, val fields: List<ProfileField>)

private val Groups = listOf(
    FieldGroup("About you", listOf(ProfileField.DISPLAY_NAME, ProfileField.BIO)),
    FieldGroup("Education", listOf(ProfileField.PROGRAM, ProfileField.UNIVERSITY)),
    FieldGroup("Work", listOf(ProfileField.ROLE, ProfileField.COMPANY)),
    FieldGroup("Links", listOf(ProfileField.GITHUB, ProfileField.LINKEDIN, ProfileField.WEBSITE, ProfileField.PUBLIC_EMAIL)),
)

/**
 * Full-screen editor: profile type, then grouped fields approved by that type. Save publishes
 * locally; leaving with unsaved changes asks first.
 */
@Composable
fun ProfileEditScreen(snackbar: SnackbarHostState, onDone: () -> Unit, vm: ProfileViewModel = viewModel()) {
    val scope = rememberCoroutineScope()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val published by KipatRepository.identity.collectAsStateWithLifecycle()
    val dirty = draft != published
    val errors = remember(draft) { vm.errors() }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var typeMenu by remember { mutableStateOf(false) }

    fun close() = if (dirty) confirmDiscard = true else onDone()
    BackHandler(enabled = dirty) { confirmDiscard = true }

    Scaffold(
        containerColor = Kipat.colors.background,
        topBar = {
            KipatTopBar("Edit profile", navigationIcon = KipatSymbols.Close, navigationDescription = "Close", onNavigate = ::close) {
                KipatTextButton(
                    "Save",
                    enabled = dirty,
                    onClick = {
                        if (vm.publish()) {
                            onDone()
                            scope.launch { snackbar.showSnackbar("Profile saved") }
                        } else {
                            scope.launch { snackbar.showSnackbar("Fix the highlighted fields to save") }
                        }
                    },
                )
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
            Box {
                ActionRow(
                    "Profile type",
                    supporting = "Decides which details appear on your card",
                    value = draft.type.label,
                    chevron = true,
                    onClick = { typeMenu = true },
                )
                DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                    IdentityType.entries.filter { ProfileTemplateId.suggestedFor(it).available }.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.label) },
                            trailingIcon = { if (draft.type == type) Icon(KipatSymbols.Check, contentDescription = "Selected") },
                            onClick = { vm.setType(type); typeMenu = false },
                        )
                    }
                }
            }
            Groups.forEach { group ->
                val fields = group.fields.filter { it in draft.profileTemplate.fields }
                if (fields.isEmpty()) return@forEach
                SectionHeading(group.title)
                fields.forEach { field ->
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
                            capitalization = if (field in setOf(ProfileField.DISPLAY_NAME, ProfileField.BIO, ProfileField.PROGRAM, ProfileField.UNIVERSITY, ProfileField.ROLE, ProfileField.COMPANY)) KeyboardCapitalization.Words else KeyboardCapitalization.None,
                            keyboardType = when (field) {
                                ProfileField.PUBLIC_EMAIL -> KeyboardType.Email
                                ProfileField.WEBSITE, ProfileField.LINKEDIN -> KeyboardType.Uri
                                else -> KeyboardType.Text
                            },
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
            }
            Text(
                "Saved on this device only for now.",
                style = MaterialTheme.typography.bodySmall,
                color = Kipat.colors.textTertiary,
                modifier = Modifier.padding(KipatSpacing.gutter),
            )
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("Your edits haven't been saved.") },
            confirmButton = {
                KipatTextButton("Discard", color = Kipat.colors.revoked, onClick = {
                    confirmDiscard = false
                    vm.discard()
                    onDone()
                })
            },
            dismissButton = { KipatTextButton("Keep editing", onClick = { confirmDiscard = false }) },
            containerColor = Kipat.colors.surfaceElevated,
        )
    }
}
