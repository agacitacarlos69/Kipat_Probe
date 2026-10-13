package com.kipat.probe.ui.screens.connections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kipat.probe.data.KipatRepository
import com.kipat.probe.data.toRevealPayload
import com.kipat.probe.model.Connection
import com.kipat.probe.model.ConnectionSort
import com.kipat.probe.model.organizeConnections
import com.kipat.probe.reveal.RevealLauncher
import com.kipat.probe.ui.components.KipatAvatar
import com.kipat.probe.ui.components.KipatConnectionRow
import com.kipat.probe.ui.components.KipatGroupDivider
import com.kipat.probe.ui.components.KipatSectionHeader
import com.kipat.probe.ui.components.KipatStatusBadge
import com.kipat.probe.ui.components.relativeTime
import com.kipat.probe.model.initialsOf
import com.kipat.probe.ui.theme.Kipat
import com.kipat.probe.ui.theme.KipatSpacing
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionsScreen(
    snackbar: SnackbarHostState,
    onOpenProfile: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val all by KipatRepository.connections.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(ConnectionSort.RECENT) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val sections = remember(all, query, sort) { organizeConnections(all, query, sort) }
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = KipatSpacing.xxl),
    ) {
        item {
            Text(
                "Connections",
                style = MaterialTheme.typography.headlineMedium,
                color = Kipat.colors.textPrimary,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = KipatSpacing.gutter, vertical = KipatSpacing.lg)
                    .semantics { heading() },
            )
        }
        item {
            TextField(
                value = query,
                onValueChange = { query = it.take(64) },
                placeholder = { Text("Search name, role or organization") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, contentDescription = "Clear search") }
                    }
                },
                singleLine = true,
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = KipatSpacing.gutter),
            )
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = KipatSpacing.gutter, vertical = KipatSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${all.size} connections",
                    style = MaterialTheme.typography.bodySmall,
                    color = Kipat.colors.textTertiary,
                    modifier = Modifier.weight(1f),
                )
                SingleChoiceSegmentedButtonRow {
                    ConnectionSort.entries.forEachIndexed { i, option ->
                        SegmentedButton(
                            selected = sort == option,
                            onClick = { sort = option },
                            shape = SegmentedButtonDefaults.itemShape(i, ConnectionSort.entries.size),
                            icon = {},
                            label = { Text(option.label, style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                }
            }
        }

        if (sections.isEmpty) {
            item {
                Text(
                    if (query.isBlank()) "No connections yet. Taps only become connections when you choose Connect." else "No matches for \"$query\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Kipat.colors.textSecondary,
                    modifier = Modifier.padding(KipatSpacing.gutter),
                )
            }
        }
        if (sections.pinned.isNotEmpty()) {
            item { KipatSectionHeader("Pinned", Modifier.padding(horizontal = KipatSpacing.gutter)) }
            items(sections.pinned, key = { "p-" + it.id }) { c ->
                KipatConnectionRow(c, onClick = { selectedId = c.id }, trailingText = relativeTime(c.connectedAtMillis), modifier = Modifier.animateItem())
            }
        }
        if (sections.others.isNotEmpty()) {
            item {
                KipatSectionHeader(if (sort == ConnectionSort.RECENT) "Recent" else "All", Modifier.padding(horizontal = KipatSpacing.gutter))
            }
            items(sections.others, key = { it.id }) { c ->
                KipatConnectionRow(c, onClick = { selectedId = c.id }, trailingText = relativeTime(c.connectedAtMillis), modifier = Modifier.animateItem())
            }
        }
    }

    val selected = selectedId?.let { id -> all.firstOrNull { it.id == id } }
    if (selected != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { selectedId = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            ConnectionSheet(
                connection = selected,
                onOpenProfile = {
                    selectedId = null
                    onOpenProfile(selected.id)
                },
                onReplay = { RevealLauncher.launch(context, selected.toRevealPayload()) },
                onTogglePin = { KipatRepository.togglePin(selected.id) },
                onRemove = {
                    selectedId = null
                    val removed = KipatRepository.removeConnection(selected.id) ?: return@ConnectionSheet
                    scope.launch {
                        val result = snackbar.showSnackbar(
                            message = "Removed ${removed.displayName}",
                            actionLabel = "Undo",
                            duration = SnackbarDuration.Short,
                        )
                        if (result == SnackbarResult.ActionPerformed) KipatRepository.restoreConnection(removed)
                    }
                },
            )
        }
    }
}

@Composable
private fun ConnectionSheet(
    connection: Connection,
    onOpenProfile: () -> Unit,
    onReplay: () -> Unit,
    onTogglePin: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = KipatSpacing.lg),
    ) {
        Row(
            Modifier.padding(horizontal = KipatSpacing.gutter, vertical = KipatSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KipatSpacing.md),
        ) {
            KipatAvatar(initialsOf(connection.displayName), size = 56.dp)
            Column(Modifier.weight(1f)) {
                Text(connection.displayName, style = MaterialTheme.typography.titleLarge, color = Kipat.colors.textPrimary)
                Text(
                    listOf(connection.subtitle, connection.organization).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Kipat.colors.textSecondary,
                )
                Spacer(Modifier.size(KipatSpacing.sm))
                KipatStatusBadge(connection.authenticity, connection.isDemo)
            }
        }
        Spacer(Modifier.size(KipatSpacing.md))
        SheetAction(Icons.Filled.Person, "Open Profile", onClick = onOpenProfile)
        SheetAction(Icons.Filled.PlayArrow, "Replay Reveal", detail = connection.revealTemplate.label, onClick = onReplay)
        SheetAction(
            if (connection.pinned) Icons.Filled.Star else Icons.Outlined.Star,
            if (connection.pinned) "Unpin" else "Pin to top",
            onClick = onTogglePin,
        )
        SheetAction(
            Icons.Filled.Info,
            "Connection source",
            detail = "${connection.source.label} · ${relativeTime(connection.connectedAtMillis)}",
            onClick = null,
        )
        KipatGroupDivider()
        SheetAction(Icons.Filled.Delete, "Remove Connection", tint = Kipat.colors.revoked, onClick = onRemove)
    }
}

@Composable
private fun SheetAction(
    icon: ImageVector,
    label: String,
    detail: String? = null,
    tint: Color = Kipat.colors.textPrimary,
    onClick: (() -> Unit)?,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = KipatSpacing.gutter, vertical = KipatSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KipatSpacing.lg),
    ) {
        Icon(icon, contentDescription = null, tint = if (tint == Kipat.colors.textPrimary) Kipat.colors.textSecondary else tint)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = Kipat.colors.textTertiary)
        }
    }
}
