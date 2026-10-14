package com.kipat.probe.reveal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kipat.probe.nfc.peer.TapPending
import com.kipat.probe.ui.theme.Kipat

/**
 * What the Reveal host shows while a Kipat Tap exchange is still in flight. Usually nothing: the
 * other app stays visible until the Reveal starts. Only if the phones moved apart, or something
 * needs saying, a small surface near the bottom does. No spinner, no looping motion. Tapping
 * anywhere closes it.
 */
@Composable
fun TapPendingOverlay(state: TapPending, onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val (title, detail) = when (state) {
            TapPending.Connecting -> return@Box
            TapPending.KeepTogether -> "Keep the phones together" to "Kipat found. Hold still for a moment."
            is TapPending.Message -> state.text to null
        }
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(20.dp)
                .widthIn(max = 380.dp)
                .background(Kipat.colors.surfaceElevated, RoundedCornerShape(16.dp))
                .padding(20.dp)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Kipat.colors.textPrimary)
            detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Kipat.colors.textSecondary) }
        }
    }
}
