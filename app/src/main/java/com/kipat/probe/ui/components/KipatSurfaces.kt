package com.kipat.probe.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kipat.probe.ui.theme.Kipat
import com.kipat.probe.ui.theme.KipatRadius
import com.kipat.probe.ui.theme.KipatSpacing

/** Translucent "glass" surface with a hairline gradient edge. Used sparingly for hero elements. */
fun Modifier.glassSurface(
    shape: Shape,
    fill: Color,
    edge: Color,
    edgeHighlight: Color = edge,
): Modifier = this
    .clip(shape)
    .background(fill, shape)
    .border(1.dp, Brush.linearGradient(listOf(edgeHighlight, edge, edge.copy(alpha = edge.alpha * 0.4f))), shape)

/** Small uppercase eyebrow + optional trailing action. */
@Composable
fun KipatSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = KipatSpacing.xl, bottom = KipatSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Kipat.colors.textTertiary,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (action != null && onAction != null) {
            Text(
                text = action,
                style = MaterialTheme.typography.labelLarge,
                color = Kipat.colors.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(KipatRadius.xs))
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = KipatSpacing.sm, vertical = KipatSpacing.xs),
            )
        }
    }
}

/** A quiet grouped container for related rows — Kipat's alternative to stacks of big cards. */
@Composable
fun KipatGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer),
        content = content,
    )
}

@Composable
fun KipatGroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = KipatSpacing.lg),
        thickness = 1.dp,
        color = Kipat.colors.divider,
    )
}

/** Label/value row inside a [KipatGroup]. */
@Composable
fun KipatInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = KipatSpacing.lg, vertical = KipatSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KipatSpacing.md),
    ) {
        if (leading != null) {
            Box(contentAlignment = Alignment.Center) { leading() }
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = Kipat.colors.textSecondary)
            Spacer(Modifier.width(KipatSpacing.xxs))
            Text(value, style = MaterialTheme.typography.titleSmall, color = Kipat.colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke(this)
    }
}
