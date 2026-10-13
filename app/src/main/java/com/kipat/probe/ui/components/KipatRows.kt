package com.kipat.probe.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kipat.probe.model.AuthenticityState
import com.kipat.probe.model.StatusTone
import com.kipat.probe.model.initialsOf
import com.kipat.probe.model.present
import com.kipat.probe.ui.theme.Kipat
import com.kipat.probe.ui.theme.KipatSpacing
import com.kipat.probe.ui.theme.KipatType

/** Sentence-case heading, used only directly above a list. */
@Composable
fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = KipatType.sectionHeading,
        color = Kipat.colors.textSecondary,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = KipatSpacing.gutter, end = KipatSpacing.gutter, top = KipatSpacing.s24, bottom = KipatSpacing.s8)
            .semantics { heading() },
    )
}

@Composable
fun KipatDivider(modifier: Modifier = Modifier, inset: Boolean = true) {
    HorizontalDivider(
        modifier = modifier.padding(start = if (inset) KipatSpacing.gutter else 0.dp),
        thickness = 1.dp,
        color = Kipat.colors.divider,
    )
}

/** Leading symbol, label (+ optional supporting text), optional trailing value and chevron. */
@Composable
fun ActionRow(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    supporting: String? = null,
    value: String? = null,
    valueColor: Color = Kipat.colors.textSecondary,
    chevron: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val alpha = if (enabled) 1f else 0.5f
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (supporting != null) KipatSpacing.rowTwoLine else KipatSpacing.rowOneLine)
            .then(if (onClick != null && enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = KipatSpacing.gutter, vertical = KipatSpacing.s8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KipatSpacing.s16),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = Kipat.colors.textSecondary.copy(alpha = alpha), modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = Kipat.colors.textPrimary.copy(alpha = alpha))
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodyMedium, color = Kipat.colors.textSecondary.copy(alpha = alpha))
            }
        }
        if (value != null) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor.copy(alpha = alpha), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (chevron) Icon(KipatSymbols.ChevronRight, contentDescription = null, tint = Kipat.colors.textTertiary, modifier = Modifier.size(20.dp))
    }
}

/**
 * M3 two-line person row: avatar, name, "Role, Organisation", trailing time. Trust appears as a
 * small seal when verified and in words only when it is not normal (unverified, revoked, error).
 */
@OptIn(ExperimentalFoundationApi::class)
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
    onLongClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val p = authenticity.present(isDemo)
    val abnormal = p.tone == StatusTone.WARNING || p.tone == StatusTone.NEGATIVE
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = KipatSpacing.rowTwoLine)
            .combinedClickable(role = Role.Button, onClickLabel = "Open $name", onClick = onClick, onLongClickLabel = if (onLongClick != null) "More actions" else null, onLongClick = onLongClick)
            .padding(horizontal = KipatSpacing.gutter, vertical = KipatSpacing.s12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KipatSpacing.s16),
    ) {
        KipatAvatar(initialsOf(name), size = 40.dp)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium, color = Kipat.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (p.showsCheck) Icon(KipatSymbols.VerifiedFilled, contentDescription = "Verified card", tint = Kipat.colors.verified, modifier = Modifier.size(16.dp))
                if (pinned) Icon(KipatSymbols.Pin, contentDescription = "Pinned", tint = Kipat.colors.textTertiary, modifier = Modifier.size(16.dp))
            }
            if (detail.isNotBlank()) {
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = Kipat.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (abnormal) {
                Text(p.label, style = MaterialTheme.typography.bodyMedium, color = Kipat.colors.tone(p.tone))
            }
        }
        if (trailing != null) {
            trailing()
        } else if (trailingText != null) {
            Text(trailingText, style = MaterialTheme.typography.bodySmall, color = Kipat.colors.textTertiary)
        }
    }
}

/** Radio option for single choices (Reveal style, accent, template). */
@Composable
fun OptionRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (supporting != null) KipatSpacing.rowTwoLine else KipatSpacing.rowOneLine)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = KipatSpacing.gutter, vertical = KipatSpacing.s8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KipatSpacing.s16),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = Kipat.colors.textPrimary.copy(alpha = if (enabled) 1f else 0.5f))
            if (supporting != null) Text(supporting, style = MaterialTheme.typography.bodyMedium, color = Kipat.colors.textSecondary.copy(alpha = if (enabled) 1f else 0.5f))
        }
        RadioButton(selected = selected, onClick = null, enabled = enabled, colors = RadioButtonDefaults.colors(selectedColor = Kipat.colors.accent))
    }
}

/** "2h", "3d" — compact trailing time for lists. */
fun shortTime(atMillis: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = ((now - atMillis).coerceAtLeast(0)) / 60_000
    return when {
        minutes < 1 -> "Now"
        minutes < 60 -> "${minutes}m"
        minutes < 60 * 24 -> "${minutes / 60}h"
        minutes < 60 * 24 * 7 -> "${minutes / (60 * 24)}d"
        else -> "${minutes / (60 * 24 * 7)}w"
    }
}

/** "2 hours ago" — for sentences. */
fun relativeTime(atMillis: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = ((now - atMillis).coerceAtLeast(0)) / 60_000
    fun plural(n: Long, unit: String) = "$n $unit${if (n == 1L) "" else "s"} ago"
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> plural(minutes, "minute")
        minutes < 60 * 24 -> plural(minutes / 60, "hour")
        minutes < 60 * 24 * 7 -> plural(minutes / (60 * 24), "day")
        else -> plural(minutes / (60 * 24 * 7), "week")
    }
}
