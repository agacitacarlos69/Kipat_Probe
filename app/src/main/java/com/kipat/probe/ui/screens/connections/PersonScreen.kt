package com.kipat.probe.ui.screens.connections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kipat.probe.data.ContactSaver
import com.kipat.probe.data.KipatRepository
import com.kipat.probe.data.toRevealPayload
import com.kipat.probe.model.initialsOf
import com.kipat.probe.reveal.RevealLauncher
import com.kipat.probe.ui.components.ActionRow
import com.kipat.probe.ui.components.IdentityBlock
import com.kipat.probe.ui.components.KipatDivider
import com.kipat.probe.ui.components.KipatPrimaryButton
import com.kipat.probe.ui.components.KipatSecondaryButton
import com.kipat.probe.ui.components.KipatSymbols
import com.kipat.probe.ui.components.KipatTopBar
import com.kipat.probe.ui.components.SectionHeading
import com.kipat.probe.ui.components.relativeTime
import com.kipat.probe.ui.theme.Kipat
import com.kipat.probe.ui.theme.KipatSpacing

/** A person you connected with. Primary action: Save contact (into Android Contacts). */
@Composable
fun PersonScreen(connectionId: String, snackbar: SnackbarHostState, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val connections by KipatRepository.connections.collectAsStateWithLifecycle()
    val c = connections.firstOrNull { it.id == connectionId }
    var menu by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Kipat.colors.background,
        topBar = {
            KipatTopBar("", navigationIcon = KipatSymbols.ArrowBack, onNavigate = onBack) {
                if (c != null) {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(KipatSymbols.MoreVert, contentDescription = "More actions") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(if (c.pinned) "Unpin" else "Pin") },
                                leadingIcon = { Icon(KipatSymbols.Pin, contentDescription = null) },
                                onClick = { KipatRepository.togglePin(c.id); menu = false },
                            )
                            DropdownMenuItem(
                                text = { Text("Remove") },
                                leadingIcon = { Icon(KipatSymbols.Delete, contentDescription = null) },
                                onClick = {
                                    menu = false
                                    onBack()
                                    removeWithUndo(c.id, snackbar, scope)
                                },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            if (c == null) {
                Text(
                    "This connection was removed.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Kipat.colors.textSecondary,
                    modifier = Modifier.padding(KipatSpacing.gutter),
                )
                return@Column
            }
            IdentityBlock(
                displayName = c.displayName,
                subtitle = c.subtitle,
                organization = c.organization,
                initials = initialsOf(c.displayName),
                authenticity = c.authenticity,
                isDemo = c.isDemo,
                modifier = Modifier.padding(horizontal = KipatSpacing.gutter, vertical = KipatSpacing.s8),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = KipatSpacing.gutter, vertical = KipatSpacing.s24),
                horizontalArrangement = Arrangement.spacedBy(KipatSpacing.s12),
            ) {
                KipatPrimaryButton(
                    "Save contact",
                    icon = KipatSymbols.PersonAdd,
                    onClick = { ContactSaver.save(context, c.displayName, c.subtitle, c.organization, c.cardId) },
                    modifier = Modifier.weight(1f),
                )
                KipatSecondaryButton("Replay Reveal", onClick = { RevealLauncher.launch(context, c.toRevealPayload()) })
            }
            KipatDivider(inset = false)
            SectionHeading("Connection")
            ActionRow("Met by", value = c.source.label)
            KipatDivider()
            ActionRow("When", value = relativeTime(c.connectedAtMillis).replaceFirstChar { it.uppercase() })
            KipatDivider()
            ActionRow("Kipat Card", value = c.cardId)
            if (c.isDemo) {
                Text(
                    "Sample connection. These details are local demo data.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Kipat.colors.textTertiary,
                    modifier = Modifier.padding(KipatSpacing.gutter),
                )
            }
        }
    }
}
