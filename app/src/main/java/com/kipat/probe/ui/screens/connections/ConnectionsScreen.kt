package com.kipat.probe.ui.screens.connections

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kipat.probe.data.KipatRepository
import com.kipat.probe.model.Connection
import com.kipat.probe.model.ConnectionSort
import com.kipat.probe.model.organizeConnections
import com.kipat.probe.ui.components.KipatSymbols
import com.kipat.probe.ui.components.KipatTopBar
import com.kipat.probe.ui.components.PersonRow
import com.kipat.probe.ui.components.SectionHeading
import com.kipat.probe.ui.components.shortTime
import com.kipat.probe.ui.theme.Kipat
import com.kipat.probe.ui.theme.KipatSpacing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** People you connected with: search, then a plain list. Row tap opens the person. */
@Composable
fun ConnectionsScreen(snackbar: SnackbarHostState, onOpenPerson: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val all by KipatRepository.connections.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(ConnectionSort.RECENT) }
    var sortMenu by remember { mutableStateOf(false) }
    var rowMenuFor by rememberSaveable { mutableStateOf<String?>(null) }
    val sections = remember(all, query, sort) { organizeConnections(all, query, sort) }

    Scaffold(
        containerColor = Kipat.colors.background,
        topBar = {
            KipatTopBar("Connections") {
                Box {
                    IconButton(onClick = { sortMenu = true }) { Icon(KipatSymbols.Sort, contentDescription = "Sort") }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        ConnectionSort.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(if (option == ConnectionSort.RECENT) "Most recent" else "Name") },
                                trailingIcon = { if (sort == option) Icon(KipatSymbols.Check, contentDescription = "Selected") },
                                onClick = { sort = option; sortMenu = false },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = KipatSpacing.s32),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(64) },
                    placeholder = { Text("Search people") },
                    leadingIcon = { Icon(KipatSymbols.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(KipatSymbols.Close, contentDescription = "Clear search") }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Kipat.colors.outline,
                        focusedBorderColor = Kipat.colors.accent,
                        unfocusedContainerColor = Kipat.colors.surface,
                        focusedContainerColor = Kipat.colors.surface,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = KipatSpacing.gutter, vertical = KipatSpacing.s8),
                )
            }
            if (sections.isEmpty) {
                item {
                    Text(
                        if (query.isBlank()) "No connections yet. After a tap, choose Connect to keep someone here." else "No one matches \"$query\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Kipat.colors.textSecondary,
                        modifier = Modifier.padding(KipatSpacing.gutter),
                    )
                }
            }
            if (sections.pinned.isNotEmpty()) {
                item { SectionHeading("Pinned") }
                items(sections.pinned, key = { "p-" + it.id }) { c ->
                    ConnectionRow(c, onOpenPerson, rowMenuFor == c.id, { rowMenuFor = it }, snackbar, scope)
                }
            }
            if (sections.others.isNotEmpty()) {
                item { SectionHeading(if (sort == ConnectionSort.RECENT) "Recent" else "All") }
                items(sections.others, key = { it.id }) { c ->
                    ConnectionRow(c, onOpenPerson, rowMenuFor == c.id, { rowMenuFor = it }, snackbar, scope)
                }
            }
        }
    }
}

@Composable
private fun ConnectionRow(
    c: Connection,
    onOpenPerson: (String) -> Unit,
    menuOpen: Boolean,
    setMenu: (String?) -> Unit,
    snackbar: SnackbarHostState,
    scope: CoroutineScope,
) {
    Box {
        PersonRow(
            name = c.displayName,
            detail = listOf(c.subtitle, c.organization).filter { it.isNotBlank() }.joinToString(", "),
            authenticity = c.authenticity,
            isDemo = c.isDemo,
            pinned = c.pinned,
            trailingText = shortTime(c.connectedAtMillis),
            onClick = { onOpenPerson(c.id) },
            onLongClick = { setMenu(c.id) },
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { setMenu(null) }) {
            DropdownMenuItem(
                text = { Text(if (c.pinned) "Unpin" else "Pin") },
                leadingIcon = { Icon(KipatSymbols.Pin, contentDescription = null) },
                onClick = { KipatRepository.togglePin(c.id); setMenu(null) },
            )
            DropdownMenuItem(
                text = { Text("Remove") },
                leadingIcon = { Icon(KipatSymbols.Delete, contentDescription = null) },
                onClick = {
                    setMenu(null)
                    removeWithUndo(c.id, snackbar, scope)
                },
            )
        }
    }
}

internal fun removeWithUndo(id: String, snackbar: SnackbarHostState, scope: CoroutineScope) {
    val removed = KipatRepository.removeConnection(id) ?: return
    scope.launch {
        val result = snackbar.showSnackbar("Removed ${removed.displayName}", actionLabel = "Undo", duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) KipatRepository.restoreConnection(removed)
    }
}
