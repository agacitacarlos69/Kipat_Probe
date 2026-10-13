package com.kipat.probe.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kipat.probe.model.AuthenticityState
import com.kipat.probe.model.Connection
import com.kipat.probe.model.initialsOf
import com.kipat.probe.model.present
import com.kipat.probe.ui.theme.Kipat
import com.kipat.probe.ui.theme.KipatSpacing

@Composable
fun KipatConnectionRow(
    connection: Connection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingText: String? = null,
) {
    PersonRow(
        name = connection.displayName,
        detail = listOf(connection.subtitle, connection.organization).filter { it.isNotBlank() }.joinToString(" · "),
        authenticity = connection.authenticity,
        isDemo = connection.isDemo,
        pinned = connection.pinned,
        trailingText = trailingText,
        onClick = onClick,
        modifier = modifier,
    )
}

/** Shared row for connections and recent taps. */
@Composable
fun PersonRow(
    name: String,
    detail: String,
    authenticity: AuthenticityState,
    isDemo: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    pinned: Boolean = false,
    trailingText: String? = null,
) {
    val auth = authenticity.present(isDemo)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClickLabel = "Open $name", onClick = onClick)
            .padding(horizontal = KipatSpacing.lg, vertical = KipatSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KipatSpacing.md),
    ) {
        KipatAvatar(initialsOf(name), size = 44.dp)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(name, style = MaterialTheme.typography.titleSmall, color = Kipat.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (pinned) Icon(Icons.Filled.Star, contentDescription = "Pinned", tint = Kipat.colors.accentSecondary, modifier = Modifier.size(14.dp))
            }
            if (detail.isNotBlank()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = Kipat.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                auth.label,
                style = MaterialTheme.typography.labelMedium,
                color = Kipat.colors.tone(auth.tone),
            )
            if (trailingText != null) {
                Text(trailingText, style = MaterialTheme.typography.bodySmall, color = Kipat.colors.textTertiary)
            }
        }
    }
}

/** "2h ago" style relative time without java.time (min SDK 26 has it, but keep it tiny). */
fun relativeTime(atMillis: Long, now: Long = System.currentTimeMillis()): String {
    val diff = (now - atMillis).coerceAtLeast(0)
    val minutes = diff / 60_000
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 60 * 24 -> "${minutes / 60}h ago"
        minutes < 60 * 24 * 7 -> "${minutes / (60 * 24)}d ago"
        else -> "${minutes / (60 * 24 * 7)}w ago"
    }
}
