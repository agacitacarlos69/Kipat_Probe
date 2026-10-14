package com.kipat.probe.ui.screens.share

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kipat.probe.nfc.peer.KipatPeerCapabilities
import com.kipat.probe.nfc.peer.KipatPeerSessions
import com.kipat.probe.nfc.peer.KipatTapAvailability
import com.kipat.probe.nfc.peer.KipatTapStore
import com.kipat.probe.ui.components.ActionRow
import com.kipat.probe.ui.components.KipatSymbols
import com.kipat.probe.ui.components.KipatTextButton
import com.kipat.probe.ui.theme.Kipat
import com.kipat.probe.ui.theme.KipatSpacing

const val KIPAT_TAP_DISCLOSURE =
    "Touch another Kipat-enabled phone to exchange the public profile you chose to share.\n\n" +
        "Kipat Tap works while your phone is unlocked and NFC is on, even when Kipat isn't open.\n\n" +
        "Only your published profile is shared. Connecting is always your choice."

/** Kipat Tap state as the UI needs it; refreshed on every resume (NFC can be toggled in Settings). */
data class KipatTapStatus(
    val availability: KipatTapAvailability,
    val enabled: Boolean,
    val prompted: Boolean,
    val caps: KipatPeerCapabilities,
) {
    /** The phone can take part at all (NFC + HCE). Unsupported phones are never pitched the feature. */
    val capable: Boolean get() = caps.hasNfc && caps.hasHce

    /** One status word for rows ("Kipat Card — Active" style). */
    val word: String
        get() = when (availability) {
            KipatTapAvailability.READY -> "Ready"
            KipatTapAvailability.DISABLED_BY_USER -> "Off"
            KipatTapAvailability.NFC_DISABLED -> "NFC is off"
            KipatTapAvailability.DEVICE_LOCKED -> "Locked"
            KipatTapAvailability.NO_PROFILE -> "No profile"
            KipatTapAvailability.HCE_UNSUPPORTED, KipatTapAvailability.UNAVAILABLE -> "Not supported"
        }
}

@Composable
fun rememberKipatTapStatus(): KipatTapStatus {
    val context = LocalContext.current
    val enabledFlow by KipatTapStore.enabled.collectAsStateWithLifecycle()
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }
    return remember(enabledFlow, tick) {
        KipatTapStatus(
            availability = KipatTapStore.availability(context),
            enabled = KipatTapStore.isEnabled(context),
            prompted = KipatTapStore.wasPrompted(context),
            caps = KipatPeerSessions.capabilities(context),
        )
    }
}

/** The one-time disclosure. Shown once; after that, Kipat Tap is a setting, never a per-tap choice. */
@Composable
fun KipatTapConsentDialog(onEnable: () -> Unit, onNotNow: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNotNow,
        title = { Text("Kipat Tap") },
        text = { Text(KIPAT_TAP_DISCLOSURE) },
        confirmButton = { KipatTextButton("Enable Kipat Tap", onClick = onEnable) },
        dismissButton = { KipatTextButton("Not now", onClick = onNotNow) },
        containerColor = Kipat.colors.surfaceElevated,
    )
}

/**
 * Home: one quiet row under the Kipat Card row. Offers Kipat Tap once on capable phones, then
 * just says whether it's ready (or what's in the way). Nothing on phones that can't do it.
 */
@Composable
fun KipatTapHomeRow(modifier: Modifier = Modifier, divider: @Composable () -> Unit = {}) {
    val context = LocalContext.current
    val status = rememberKipatTapStatus()
    var consent by rememberSaveable { mutableStateOf(false) }
    when {
        !status.enabled && !status.prompted && status.capable -> {
            ActionRow(
                "Turn on Kipat Tap",
                icon = KipatSymbols.Contactless,
                supporting = "Touch another Kipat phone to swap profiles",
                chevron = true,
                onClick = { consent = true },
                modifier = modifier,
            )
            divider()
        }
        status.enabled -> {
            val nfcOff = status.availability == KipatTapAvailability.NFC_DISABLED
            ActionRow(
                "Kipat Tap",
                icon = KipatSymbols.Contactless,
                supporting = status.availability.takeIf { it != KipatTapAvailability.READY }?.sentence,
                value = status.word,
                valueColor = statusColor(status.availability),
                chevron = nfcOff,
                onClick = if (nfcOff) ({ openNfcSettings(context) }) else null,
                modifier = modifier,
            )
            divider()
        }
        else -> Unit
    }
    if (consent) {
        KipatTapConsentDialog(
            onEnable = { KipatTapStore.setEnabled(context, true); consent = false },
            onNotNow = { KipatTapStore.markPrompted(context); consent = false },
        )
    }
}

/** Share: explains Kipat Tap. Informational and a way to turn it on; it never needs to be open. */
@Composable
fun KipatTapShareSection() {
    val context = LocalContext.current
    val status = rememberKipatTapStatus()
    var consent by rememberSaveable { mutableStateOf(false) }
    ActionRow(
        "Kipat Tap",
        icon = KipatSymbols.Contactless,
        supporting = when (status.availability) {
            KipatTapAvailability.READY -> "Touch the back of your unlocked phone to another Kipat phone"
            KipatTapAvailability.DISABLED_BY_USER -> "Turn on to swap profiles by touching phones"
            else -> status.availability.sentence
        },
        value = status.word,
        valueColor = statusColor(status.availability),
        chevron = status.availability == KipatTapAvailability.DISABLED_BY_USER && status.capable || status.availability == KipatTapAvailability.NFC_DISABLED,
        onClick = when {
            status.availability == KipatTapAvailability.DISABLED_BY_USER && status.capable -> ({ consent = true })
            status.availability == KipatTapAvailability.NFC_DISABLED -> ({ openNfcSettings(context) })
            else -> null
        },
    )
    if (status.capable) {
        Text(
            "No setup needed and Kipat doesn't need to be open. Hold the backs of the phones together, " +
                "near the upper half of each, and keep them still until the Reveal appears. Both phones must be unlocked.",
            style = MaterialTheme.typography.bodySmall,
            color = Kipat.colors.textTertiary,
            modifier = Modifier.padding(horizontal = KipatSpacing.gutter, vertical = KipatSpacing.s4),
        )
    }
    if (consent) {
        KipatTapConsentDialog(
            onEnable = { KipatTapStore.setEnabled(context, true); consent = false },
            onNotNow = { KipatTapStore.markPrompted(context); consent = false },
        )
    }
}

/** Profile: the global privacy control. Turning it on the first time shows the disclosure. */
@Composable
fun KipatTapSettingRow() {
    val context = LocalContext.current
    val status = rememberKipatTapStatus()
    var consent by rememberSaveable { mutableStateOf(false) }
    if (!status.capable) {
        ActionRow("Kipat Tap", supporting = status.availability.sentence, value = "Not supported")
        return
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = KipatSpacing.rowTwoLine)
            .clickable(role = Role.Switch) {
                if (status.enabled) KipatTapStore.setEnabled(context, false)
                else if (!status.prompted) consent = true
                else KipatTapStore.setEnabled(context, true)
            }
            .padding(horizontal = KipatSpacing.gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Kipat Tap", style = MaterialTheme.typography.bodyLarge, color = Kipat.colors.textPrimary)
            Text(
                "Allow public profile exchange when this unlocked phone touches another Kipat phone",
                style = MaterialTheme.typography.bodyMedium,
                color = Kipat.colors.textSecondary,
            )
        }
        Switch(checked = status.enabled, onCheckedChange = null, colors = SwitchDefaults.colors(checkedTrackColor = Kipat.colors.accent))
    }
    if (consent) {
        KipatTapConsentDialog(
            onEnable = { KipatTapStore.setEnabled(context, true); consent = false },
            onNotNow = { KipatTapStore.markPrompted(context); consent = false },
        )
    }
}

@Composable
private fun statusColor(a: KipatTapAvailability): Color = when (a) {
    KipatTapAvailability.READY -> Kipat.colors.verified
    KipatTapAvailability.NFC_DISABLED, KipatTapAvailability.NO_PROFILE, KipatTapAvailability.DEVICE_LOCKED -> Kipat.colors.warning
    else -> Kipat.colors.textSecondary
}

internal fun openNfcSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
    } catch (e: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
    }
}
