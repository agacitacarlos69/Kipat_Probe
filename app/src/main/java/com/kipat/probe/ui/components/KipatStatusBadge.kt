package com.kipat.probe.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.kipat.probe.model.AuthenticityState
import com.kipat.probe.model.StatusTone
import com.kipat.probe.model.present
import com.kipat.probe.ui.theme.Kipat
import com.kipat.probe.ui.theme.KipatSpacing

/**
 * Authenticity pill. Only [AuthenticityState.AUTHENTIC] gets a check mark; demo data is always
 * labelled "demo". Colour is never the only signal — the label always states the state.
 */
@Composable
fun KipatStatusBadge(
    state: AuthenticityState,
    isDemo: Boolean,
    modifier: Modifier = Modifier,
    toneColor: Color? = null,
) {
    val p = state.present(isDemo)
    StatusPill(label = p.label, tone = p.tone, showCheck = p.showsCheck, modifier = modifier, toneColor = toneColor, description = "Authenticity: ${p.label}. ${p.detail}")
}

@Composable
fun StatusPill(
    label: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    showCheck: Boolean = false,
    toneColor: Color? = null,
    description: String = label,
) {
    val color = toneColor ?: Kipat.colors.tone(tone)
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = KipatSpacing.md - 2.dp, vertical = KipatSpacing.xs + 1.dp)
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (showCheck) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.Black.copy(alpha = 0.8f), modifier = Modifier.size(10.dp))
            }
        } else {
            Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}
