package com.kipat.probe.ui.screens.developer

import android.content.ComponentName
import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.os.SystemClock
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kipat.probe.data.KipatRepository
import com.kipat.probe.data.NfcProfileReveal.toRevealPayload
import com.kipat.probe.nfc.peer.KipatHceService
import com.kipat.probe.nfc.peer.KipatPeerCapabilities
import com.kipat.probe.nfc.peer.KipatPeerProfile
import com.kipat.probe.nfc.peer.KipatPeerReader
import com.kipat.probe.nfc.peer.KipatPeerSessions
import com.kipat.probe.nfc.peer.PresentState
import com.kipat.probe.nfc.peer.ReceiveState
import com.kipat.probe.nfc.profile.NfcProfilePayload
import com.kipat.probe.reveal.KipatHaptics
import com.kipat.probe.reveal.RevealLauncher
import com.kipat.probe.reveal.RevealSource
import com.kipat.probe.ui.components.ActionRow
import com.kipat.probe.ui.components.KipatDivider
import com.kipat.probe.ui.components.KipatPrimaryButton
import com.kipat.probe.ui.components.KipatSecondaryButton
import com.kipat.probe.ui.components.KipatSymbols
import com.kipat.probe.ui.components.KipatTopBar
import com.kipat.probe.ui.components.SectionHeading
import com.kipat.probe.ui.theme.Kipat
import com.kipat.probe.ui.theme.KipatSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Developer tools → Manual NFC peer test. The explicit-role predecessor of Kipat Tap, kept for
 * diagnosing HCE / reader-mode behaviour on OEM phones: one phone presents for 60 s, the other
 * receives in reader mode. Normal Kipat Tap needs none of this (docs/nfc/ambient-peer-feasibility.md).
 */
@Composable
fun ManualPeerTestScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val store = KipatPeerSessions.presenter
    val reader = remember(activity) { activity?.let { KipatPeerReader(it, scope) } }
    val identity by KipatRepository.identity.collectAsStateWithLifecycle()
    val ownProfile = remember(identity) { KipatPeerProfile.fromIdentity(identity) }
    val present by store.state.collectAsStateWithLifecycle()
    val idleReceive = remember { MutableStateFlow<ReceiveState>(ReceiveState.Idle) }
    val receive by (reader?.state ?: idleReceive).collectAsStateWithLifecycle()
    var caps by remember { mutableStateOf(KipatPeerSessions.capabilities(context)) }
    var sendBack by rememberSaveable { mutableStateOf(true) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    val presenting = present !is PresentState.Idle
    val receiving = receive !is ReceiveState.Idle

    LifecycleResumeEffect(reader, presenting) {
        caps = KipatPeerSessions.capabilities(context)
        reader?.onResume()
        // While presenting in the foreground, prefer Kipat's service for its AID.
        if (presenting) preferKipatService(context, activity, prefer = true)
        onPauseOrDispose {
            reader?.onPause()
            if (presenting) preferKipatService(context, activity, prefer = false)
        }
    }
    // Leaving the screen ends both roles: the profile is never left readable.
    DisposableEffect(Unit) { onDispose { store.cancel(); reader?.reset() } }

    // Countdown + expiry while sharing.
    val sharingOpen = present is PresentState.Ready || present is PresentState.PeerDetected || present is PresentState.ReceivingPeerProfile
    LaunchedEffect(sharingOpen) {
        while (sharingOpen) {
            now = SystemClock.elapsedRealtime()
            store.expireIfDue()
            delay(1_000)
        }
    }

    // One restrained haptic on contact, one on completion.
    LaunchedEffect(present::class, receive::class) {
        when {
            present is PresentState.PeerDetected || receive is ReceiveState.KipatDetected -> KipatHaptics.revealStart(view)
            present is PresentState.Success || receive is ReceiveState.Success -> KipatHaptics.connect(view)
        }
    }

    // A completed exchange opens the existing Reveal once. Discovery is recorded; nothing is connected.
    LaunchedEffect(present) {
        val s = present as? PresentState.Success ?: return@LaunchedEffect
        val peer = s.peer ?: return@LaunchedEffect
        if (!s.isSelf && store.claimReveal(s.exchangeId)) openReveal(context, peer)
    }
    LaunchedEffect(receive) {
        val s = receive as? ReceiveState.Success ?: return@LaunchedEffect
        if (!s.result.isSelf && reader?.claimReveal(s.exchangeId) == true) openReveal(context, s.result.profile)
    }

    Scaffold(
        containerColor = Kipat.colors.background,
        topBar = { KipatTopBar("Manual NFC peer test", navigationIcon = KipatSymbols.ArrowBack, onNavigate = onBack) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = KipatSpacing.s32),
        ) {
            when {
                presenting -> PresentPanel(present, ownProfile, now, onCancel = store::cancel, onPresentAgain = { arm(context, ownProfile) }, onDone = store::cancel)
                receiving -> ReceivePanel(receive, sendBack, onCancel = { reader?.cancel() }, onDone = { reader?.reset() })
                else -> ChooseRole(
                    caps = caps,
                    sendBack = sendBack,
                    onSendBack = { sendBack = it },
                    onPresent = { reader?.reset(); arm(context, ownProfile) },
                    onReceive = { store.cancel(); reader?.start(ownProfile, sendBack) },
                )
            }
            Guidance()
        }
    }
}

private fun arm(context: Context, profile: NfcProfilePayload) {
    val caps = KipatPeerSessions.capabilities(context)
    val store = KipatPeerSessions.presenter
    when {
        !caps.canPresent -> store.markUnsupported(caps.notice ?: "This phone can't present over NFC.")
        !caps.nfcEnabled -> store.markUnsupported("Turn on NFC to share by touch.")
        else -> store.arm(profile)
    }
}

private fun openReveal(context: Context, profile: NfcProfilePayload) {
    val payload = profile.toRevealPayload(RevealSource.PEER_NFC, testProfile = false)
    KipatRepository.recordTap(payload)
    RevealLauncher.launch(context, payload)
}

private fun preferKipatService(context: Context, activity: android.app.Activity?, prefer: Boolean) {
    val adapter = NfcAdapter.getDefaultAdapter(context) ?: return
    activity ?: return
    try {
        val ce = CardEmulation.getInstance(adapter)
        if (prefer) ce.setPreferredService(activity, ComponentName(context, KipatHceService::class.java)) else ce.unsetPreferredService(activity)
    } catch (e: RuntimeException) {
        // No HCE on this phone, or not in the foreground: the AID still routes by category.
    }
}

// ------------------------------------------------------------------------------------ panels

@Composable
private fun ChooseRole(
    caps: KipatPeerCapabilities,
    sendBack: Boolean,
    onSendBack: (Boolean) -> Unit,
    onPresent: () -> Unit,
    onReceive: () -> Unit,
) {
    Hero(
        title = "Touch phones to share",
        body = "For diagnosing HCE and reader behaviour. Normal Kipat Tap doesn't need these controls: one phone presents for 60 seconds, the other receives.",
    )
    caps.notice?.let { Notice(it) }
    Column(Modifier.padding(KipatSpacing.gutter), verticalArrangement = Arrangement.spacedBy(KipatSpacing.s12)) {
        KipatPrimaryButton("Present my Kipat", icon = KipatSymbols.Contactless, onClick = onPresent, enabled = caps.presentReady, modifier = Modifier.fillMaxWidth())
        KipatSecondaryButton("Receive a Kipat", onClick = onReceive, enabled = caps.receiveReady, modifier = Modifier.fillMaxWidth())
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = KipatSpacing.rowTwoLine)
            .clickable(role = Role.Switch) { onSendBack(!sendBack) }
            .padding(horizontal = KipatSpacing.gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Send my profile back", style = MaterialTheme.typography.bodyLarge, color = Kipat.colors.textPrimary)
            Text(
                if (sendBack) "When you receive, they see your Kipat too" else "When you receive, they won't see your Kipat",
                style = MaterialTheme.typography.bodyMedium,
                color = Kipat.colors.textSecondary,
            )
        }
        Switch(checked = sendBack, onCheckedChange = null, colors = SwitchDefaults.colors(checkedTrackColor = Kipat.colors.accent))
    }
}

@Composable
private fun PresentPanel(state: PresentState, profile: NfcProfilePayload, now: Long, onCancel: () -> Unit, onPresentAgain: () -> Unit, onDone: () -> Unit) {
    when (state) {
        PresentState.Idle, PresentState.Preparing -> Hero("Getting ready", "Preparing your profile.")
        is PresentState.Ready -> {
            Hero("Ready to share", "Hold the back of this phone near another Kipat phone.")
            SharedProfile(profile)
            Countdown(state.expiresAtMs, now)
            KipatSecondaryButton("Cancel", onClick = onCancel, modifier = Modifier.fillMaxWidth().padding(KipatSpacing.gutter))
        }
        is PresentState.PeerDetected -> {
            Hero("Kipat found", "Keep the phones together.")
            KipatSecondaryButton("Cancel", onClick = onCancel, modifier = Modifier.fillMaxWidth().padding(KipatSpacing.gutter))
        }
        PresentState.ReceivingPeerProfile -> Hero("Receiving profile", "Keep the phones together.")
        is PresentState.Success -> {
            val peer = state.peer
            when {
                state.isSelf -> Hero("This is your own Kipat profile", "Both phones show the same Kipat. Nothing was added.")
                peer != null -> Hero("${peer.displayName.substringBefore(' ')} received", "Move phones apart. Their Kipat is open; connect if you want to keep it.")
                else -> Hero("Shared", "They received your Kipat. Move phones apart.")
            }
            KipatPrimaryButton("Done", onClick = onDone, modifier = Modifier.fillMaxWidth().padding(KipatSpacing.gutter))
        }
        PresentState.Expired -> {
            Hero("Sharing expired", "Your Kipat is no longer readable by touch.")
            KipatPrimaryButton("Present again", onClick = onPresentAgain, modifier = Modifier.fillMaxWidth().padding(KipatSpacing.gutter))
        }
        is PresentState.Unsupported -> {
            Hero("Can't present from this phone", state.reason)
            KipatSecondaryButton("Back", onClick = onDone, modifier = Modifier.fillMaxWidth().padding(KipatSpacing.gutter))
        }
        is PresentState.Error -> {
            Hero("Couldn't share", state.message)
            KipatSecondaryButton("Back", onClick = onDone, modifier = Modifier.fillMaxWidth().padding(KipatSpacing.gutter))
        }
    }
}

@Composable
private fun ReceivePanel(state: ReceiveState, sendBack: Boolean, onCancel: () -> Unit, onDone: () -> Unit) {
    val cancel: @Composable () -> Unit = { KipatSecondaryButton("Cancel", onClick = onCancel, modifier = Modifier.fillMaxWidth().padding(KipatSpacing.gutter)) }
    when (state) {
        ReceiveState.Idle -> Unit
        is ReceiveState.Scanning -> {
            Hero("Looking for Kipat", state.note ?: "Hold the back of another Kipat phone near this phone.")
            if (!sendBack) Notice("They won't see your Kipat. You only receive theirs.")
            cancel()
        }
        ReceiveState.KipatDetected -> Hero("Kipat found", "Keep the phones together.")
        ReceiveState.ReadingProfile -> Hero("Receiving profile", "Keep the phones together.")
        ReceiveState.SendingOwnProfile -> Hero("Exchanging profiles", "Keep the phones together.")
        is ReceiveState.Success -> {
            val r = state.result
            if (r.isSelf) Hero("This is your own Kipat profile", "Both phones show the same Kipat. Nothing was added.")
            else Hero("${r.profile.displayName.substringBefore(' ')} received", "Move phones apart. Their Kipat is open; connect if you want to keep it.")
            KipatPrimaryButton("Done", onClick = onDone, modifier = Modifier.fillMaxWidth().padding(KipatSpacing.gutter))
        }
        is ReceiveState.TagLost -> {
            Hero("Phones moved apart", state.message)
            cancel()
        }
        is ReceiveState.Error -> {
            Hero("Couldn't receive", state.message)
            cancel()
        }
        is ReceiveState.Unsupported -> {
            Hero("Can't receive on this phone", state.reason)
            KipatSecondaryButton("Back", onClick = onDone, modifier = Modifier.fillMaxWidth().padding(KipatSpacing.gutter))
        }
    }
}

// ------------------------------------------------------------------------------------ pieces

@Composable
private fun Hero(title: String, body: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = KipatSpacing.gutter, vertical = KipatSpacing.s24)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Icon(KipatSymbols.Contactless, contentDescription = null, tint = Kipat.colors.accent, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(KipatSpacing.s16))
        Text(title, style = MaterialTheme.typography.headlineSmall, color = Kipat.colors.textPrimary, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(KipatSpacing.s8))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = Kipat.colors.textSecondary)
    }
}

@Composable
private fun Notice(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Kipat.colors.warning, modifier = Modifier.padding(horizontal = KipatSpacing.gutter, vertical = KipatSpacing.s4))
}

@Composable
private fun SharedProfile(profile: NfcProfilePayload) {
    SectionHeading("What they'll see")
    ActionRow(profile.displayName, icon = KipatSymbols.Person, supporting = listOfNotNull(profile.headline ?: profile.role, profile.organization).joinToString(", ").ifEmpty { null })
    Text(
        "Only your public profile is shared. Nothing is saved on their phone unless they choose Connect.",
        style = MaterialTheme.typography.bodySmall,
        color = Kipat.colors.textTertiary,
        modifier = Modifier.padding(horizontal = KipatSpacing.gutter),
    )
}

@Composable
private fun Countdown(expiresAtMs: Long, now: Long) {
    val seconds = ((expiresAtMs - now + 999) / 1000).coerceAtLeast(0)
    KipatDivider(Modifier.padding(top = KipatSpacing.s16))
    ActionRow("Sharing ends in", value = if (seconds == 1L) "1 second" else "$seconds seconds")
}

@Composable
private fun Guidance() {
    SectionHeading("Having trouble?")
    Text(
        "Hold the backs of the phones together, near the upper half of each. Move them slowly until one vibrates, then keep them still until Kipat confirms. Both screens must be on and unlocked.",
        style = MaterialTheme.typography.bodyMedium,
        color = Kipat.colors.textSecondary,
        modifier = Modifier.padding(horizontal = KipatSpacing.gutter),
    )
}
