package com.kipat.probe.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kipat.probe.ui.theme.Kipat
import com.kipat.probe.ui.theme.KipatMotion
import com.kipat.probe.ui.theme.KipatRadius
import com.kipat.probe.ui.theme.KipatSpacing

/**
 * Selectable template tile used for profile and Reveal template pickers. [preview] draws a small
 * visual hint of the template.
 */
@Composable
fun KipatTemplateCard(
    title: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String? = null,
    enabled: Boolean = true,
    width: Dp = 168.dp,
    preview: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(KipatRadius.lg)
    val border by animateColorAsState(
        if (selected) Kipat.colors.accent else Kipat.colors.divider,
        animationSpec = tween(KipatMotion.Standard),
        label = "templateBorder",
    )
    Column(
        modifier = modifier
            .width(width)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(if (selected) 1.5.dp else 1.dp, border, shape)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(KipatSpacing.md),
    ) {
        if (preview != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .clip(RoundedCornerShape(KipatRadius.sm))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest),
            ) { preview() }
            Spacer(Modifier.height(KipatSpacing.md))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(KipatSpacing.xs)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Kipat.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        }
        Spacer(Modifier.height(2.dp))
        Text(description, style = MaterialTheme.typography.bodySmall, color = Kipat.colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis, minLines = 2)
        if (tag != null) {
            Spacer(Modifier.height(KipatSpacing.sm))
            Text(
                tag.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Kipat.colors.textTertiary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
