package com.kipat.probe

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kipat.probe.cards.CardResolution
import com.kipat.probe.cards.KipatCardId
import com.kipat.probe.cards.KipatCardResolver
import com.kipat.probe.cards.KipatCards
import com.kipat.probe.cards.LinkedCardReveal
import com.kipat.probe.data.KipatIdentityStore
import com.kipat.probe.data.KipatRepository
import com.kipat.probe.data.NfcProfileReveal
import com.kipat.probe.data.RevealResolver
import com.kipat.probe.data.RevealTiming
import com.kipat.probe.data.ContactSaver
import com.kipat.probe.navigation.KipatDestination
import com.kipat.probe.navigation.KipatRoutes
import com.kipat.probe.nfc.NfcIntentParser
import com.kipat.probe.nfc.NfcIntents
import com.kipat.probe.nfc.ParsedNfcIntent
import com.kipat.probe.nfc.profile.NfcProfileParse
import com.kipat.probe.reveal.KipatHaptics
import com.kipat.probe.reveal.RevealLauncher
import com.kipat.probe.reveal.RevealMilestone
import com.kipat.probe.reveal.RevealPayload
import com.kipat.probe.reveal.RevealPayloadCodec
import com.kipat.probe.reveal.RevealQuality
import com.kipat.probe.reveal.RevealQualityPolicy
import com.kipat.probe.reveal.RevealScreen
import com.kipat.probe.reveal.RevealSource
import com.kipat.probe.reveal.RevealTrace
import com.kipat.probe.reveal.TapDebouncer
import com.kipat.probe.model.AuthenticityState
import com.kipat.probe.ui.theme.KipatTheme
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.kipat.probe.nfc.peer.KipatPeerBootstrap
import com.kipat.probe.nfc.peer.KipatPeerSessions
import com.kipat.probe.nfc.peer.KipatTapInitiator
import com.kipat.probe.nfc.peer.KipatTapLauncher
import com.kipat.probe.nfc.peer.PeerExchangeResult
import com.kipat.probe.nfc.peer.PeerLog
import com.kipat.probe.nfc.peer.TapPending
import com.kipat.probe.nfc.peer.TapRole
import com.kipat.probe.nfc.profile.NfcProfilePayload
import com.kipat.probe.reveal.TapPendingOverlay

/**
 * Translucent Reveal host. Launched by:
 *  - NFC NDEF_DISCOVERED for Kipat card URIs and self-contained test profiles (own task, so it floats above the previous app)
 *  - Kipat Tap, initiator side: another Kipat phone's Type 4 bootstrap (`application/vnd.kipat.peer`);
 *    the exchange runs on the same contact ([KipatTapInitiator]) and the Reveal starts when the
 *    peer profile is validated
 *  - Kipat Tap, responder side: [com.kipat.probe.nfc.peer.KipatTapLauncher] from the HCE service
 *  - the `kipatprobe://tap/…` debug deep link
 *  - Preview / Replay from inside the app (explicit, nonce-protected payload extras)
 *
 * All paths render through the same [RevealScreen]. Nothing here waits on the network.
 */
class TapExperienceActivity : ComponentActivity() {

    private lateinit var trace: RevealTrace
    private var payload by mutableStateOf(RevealPayload.unresolved(null, RevealSource.DEEP_LINK))
    private var runKey by mutableIntStateOf(0)
    private var connected by mutableStateOf(false)
    private var quality = RevealQuality.HIGH
    private val debouncer = TapDebouncer()
    /** Non-null while a Kipat Tap exchange is in flight: the Reveal waits for a validated profile. */
    private var pending by mutableStateOf<TapPending?>(null)
    private var initiator: KipatTapInitiator? = null
    private val cardResolver: KipatCardResolver by lazy { KipatCards.resolver(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        trace = RevealTrace(intent?.getLongExtra(RevealLauncher.EXTRA_LAUNCH_UPTIME, -1L)?.takeIf { it > 0 })
        // The scrim follows the system theme (paper in light, graphite in dark), so do the bar icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        KipatIdentityStore.init(this) // a card tap may start a fresh process: same identity as the app
        KipatPeerSessions.attach(this)
        markDelivered(intent)

        runKey = savedInstanceState?.getInt(STATE_RUN_KEY) ?: 0
        quality = RevealQualityPolicy.choose(this)
        val bootstrap = peerBootstrap(intent)
        if (bootstrap != null) {
            // Kipat Tap initiator: nothing is shown until the other phone's profile is validated.
            if (savedInstanceState == null) startKipatTap(intent, bootstrap) else finish()
        } else {
            val linked = linkedCard(intent)
            if (linked != null) {
                // A linked Kipat card: card id → owner identity → its current Reveal.
                openLinkedCard(linked.first, linked.second, record = savedInstanceState == null, restart = false)
            } else {
                payload = resolve(intent)
                connected = KipatRepository.isConnected(payload.cardId)
                if (savedInstanceState == null) {
                    // Only real taps/links are recorded; in-app previews and replays are not new taps.
                    if (!hasTrustedPayload(intent)) KipatRepository.recordTap(payload)
                    rememberTap(payload)
                }
            }
        }

        setContent {
            KipatTheme {
                val waiting = pending
                if (waiting != null) TapPendingOverlay(waiting, onDismiss = ::finish) else RevealScreen(
                    payload = payload,
                    quality = quality,
                    runKey = runKey,
                    connected = connected,
                    onConnect = ::onConnect,
                    onSaveContact = ::onSaveContact,
                    onOpenProfile = ::onOpenProfile,
                    onDismiss = ::finish,
                    onMilestone = ::onMilestone,
                )
            }
        }
        trace.contentSet()
        observeFirstDraw()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        markDelivered(intent)
        peerBootstrap(intent)?.let { bootstrap ->
            // Re-read of the same phone, or a new one while a Reveal shows: the exchange decides (deduplicated).
            if (initiator?.running != true) startKipatTap(intent, bootstrap)
            return
        }
        linkedCard(intent)?.let { (cardId, source) ->
            if (debouncer.isDuplicate(cardId, source, SystemClock.elapsedRealtime())) {
                Log.i(RevealTrace.TAG, "duplicate tap ignored for ${KipatCardId.masked(cardId)}")
                return
            }
            trace = RevealTrace(intent.getLongExtra(RevealLauncher.EXTRA_LAUNCH_UPTIME, -1L).takeIf { it > 0 })
            openLinkedCard(cardId, source, record = true, restart = true)
            return
        }
        val next = resolve(intent)
        // Duplicate scans (same card within the debounce window) don't restart the Reveal.
        if (isDuplicate(next)) {
            Log.i(RevealTrace.TAG, "duplicate tap ignored for ${next.cardId}")
            return
        }
        rememberTap(next)
        payload = next
        connected = KipatRepository.isConnected(next.cardId)
        if (!hasTrustedPayload(intent)) KipatRepository.recordTap(next)
        trace = RevealTrace(intent.getLongExtra(RevealLauncher.EXTRA_LAUNCH_UPTIME, -1L).takeIf { it > 0 })
        runKey++
    }

    override fun onResume() {
        super.onResume()
        initiator?.onResume()
    }

    override fun onPause() {
        initiator?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        initiator?.cancel()
        super.onDestroy()
    }

    private fun peerBootstrap(intent: Intent?): KipatPeerBootstrap.Result? =
        if (!NfcIntents.isNfcLaunch(intent)) null
        else (NfcIntentParser.parseTap(NfcIntents.recordsFrom(intent), null) as? ParsedNfcIntent.PeerBootstrap)?.result

    private fun startKipatTap(intent: Intent, bootstrap: KipatPeerBootstrap.Result) {
        Log.i(PeerLog.TAG_AMBIENT, "ndef_dispatched since_create_ms=0")
        val showingReveal = pending == null && payload.source == RevealSource.PEER_NFC
        val tap = initiator ?: KipatTapInitiator(
            activity = this,
            scope = lifecycleScope,
            onPending = { if (payload.source != RevealSource.PEER_NFC || pending != null) pending = it },
            onPeer = ::showPeer,
            onFinish = { if (pending != null) finish() },
        ).also { initiator = it }
        if (!showingReveal) pending = TapPending.Connecting
        tap.start(intent, bootstrap)
    }

    /** A validated peer from Kipat Tap (initiator side): one Reveal per person per encounter. */
    private fun showPeer(peer: NfcProfilePayload, result: PeerExchangeResult.Success) {
        val delivery = KipatPeerSessions.deliveries.offer(peer, TapRole.INITIATOR)
        if (delivery == null) {
            Log.i(PeerLog.TAG_LAUNCH, "duplicate_exchange_ignored role=initiator")
            if (pending != null) finish()
            return
        }
        KipatPeerSessions.deliveries.markShown(delivery.exchangeId)
        KipatPeerSessions.recordReceiverDiagnostics(result)
        val next = KipatTapLauncher.payloadFor(peer)
        KipatRepository.recordTap(next)
        rememberTap(next)
        payload = next
        connected = KipatRepository.isConnected(next.cardId)
        trace = RevealTrace(null)
        pending = null
        runKey++
        Log.i(PeerLog.TAG_LAUNCH, "initiator_reveal_requested exchange=${delivery.exchangeId}")
    }

    /** A `https://kipat.app/c/kc_…` card (or debug link): its id and how it arrived; null otherwise. */
    private fun linkedCard(intent: Intent?): Pair<String, RevealSource>? {
        if (intent == null || hasTrustedPayload(intent)) return null
        val tap = runCatching { NfcIntentParser.parseTap(NfcIntents.recordsFrom(intent), NfcIntents.uriFrom(intent)) }.getOrNull()
        val target = (tap as? ParsedNfcIntent.Card)?.target ?: return null
        val id = target.cardId?.takeIf(KipatCardId::looksLinked) ?: return null
        return id to if (NfcIntents.isNfcLaunch(intent)) RevealSource.NFC_TAG else RevealSource.DEEP_LINK
    }

    /**
     * Resolves through [KipatCardResolver] (suspend, so a future backend resolver drops in). The
     * local resolver never suspends, so the Reveal is set before the first frame; a slow resolver
     * would show the same invisible pending state Kipat Tap uses, never a stale profile.
     */
    private fun openLinkedCard(cardId: String, source: RevealSource, record: Boolean, restart: Boolean) {
        var shown = false
        lifecycleScope.launch(Dispatchers.Main.immediate) {
            val resolution = cardResolver.resolve(cardId)
            shown = true
            showLinkedCard(resolution, source, record, restart)
        }
        if (!shown) pending = TapPending.Connecting
    }

    private fun showLinkedCard(resolution: CardResolution, source: RevealSource, record: Boolean, restart: Boolean) {
        val revision = (resolution as? CardResolution.Resolved)?.revision
        Log.i(KipatCards.TAG, "card_resolved result=${resolution::class.simpleName} card=${KipatCardId.masked(resolution.cardId)} source=${source.wireId} revision=$revision")
        if (record && resolution is CardResolution.Resolved) KipatCards.markSeen(resolution.cardId, System.currentTimeMillis(), source.wireId)
        val next = LinkedCardReveal.payloadFor(resolution, source)
        pending = null
        payload = next
        connected = KipatRepository.isConnected(next.cardId)
        // Only a resolved profile is someone you met; a turned-off or unknown card is not recorded.
        if (record && resolution is CardResolution.Resolved) KipatRepository.recordTap(next)
        debouncer.accept(resolution.cardId, SystemClock.elapsedRealtime())
        if (restart) runKey++
    }

    /** The responder's Reveal appeared: proves the background launch was allowed. */
    private fun markDelivered(intent: Intent?) {
        val id = intent?.getLongExtra(KipatTapLauncher.EXTRA_EXCHANGE_ID, -1L) ?: -1L
        if (id > 0 && hasTrustedPayload(intent!!)) KipatPeerSessions.deliveries.markShown(id)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_RUN_KEY, runKey)
    }

    override fun onStop() {
        super.onStop()
        // Home / app switch interrupts the Reveal: close it rather than leaving a stale overlay.
        if (!isChangingConfigurations && !isFinishing) finish()
    }

    private fun resolve(intent: Intent?): RevealPayload = try {
        if (intent != null && hasTrustedPayload(intent)) {
            val decoded = RevealPayloadCodec.decode { intent.getStringExtra(it) }
            if (!decoded.isClean) Log.w(RevealTrace.TAG, "payload issues: ${decoded.issues}")
            decoded.payload
        } else {
            val uri = NfcIntents.uriFrom(intent)
            val source = if (NfcIntents.isNfcLaunch(intent)) RevealSource.NFC_TAG else RevealSource.DEEP_LINK
            // An embedded test profile on the tag wins over any Kipat URI on the same tag.
            when (val tap = NfcIntentParser.parseTap(NfcIntents.recordsFrom(intent), uri)) {
                is ParsedNfcIntent.Profile -> {
                    when (val r = tap.result) {
                        is NfcProfileParse.Valid -> Log.i(RevealTrace.TAG, "nfc test profile v${r.profile.version} bytes=${r.payloadBytes} records=${tap.recordCount}")
                        else -> Log.w(RevealTrace.TAG, "nfc profile not shown: ${r::class.simpleName}")
                    }
                    NfcProfileReveal.payloadFor(tap.result, source)
                }
                is ParsedNfcIntent.Card -> RevealResolver.resolve(
                    target = tap.target,
                    ownIdentity = KipatRepository.identity.value,
                    ownCardIsDemo = KipatRepository.card.value.isDemo,
                    connections = KipatRepository.connections.value,
                )
                // Handled before resolve() by the Kipat Tap initiator; never rendered as a card.
                is ParsedNfcIntent.PeerBootstrap -> RevealPayload.unresolved(null, source)
                is ParsedNfcIntent.Unknown -> {
                    Log.w(RevealTrace.TAG, "unrecognised launch uri=$uri action=${intent?.action}")
                    RevealPayload.unresolved(null, source)
                }
            }
        }
    } catch (t: Throwable) {
        // A bad intent must never crash the Reveal; show the generic card instead.
        Log.e(RevealTrace.TAG, "failed to resolve reveal", t)
        RevealPayload.unresolved(null, RevealSource.DEEP_LINK)
    }

    /**
     * Explicit payload extras are only honoured when they carry this process's nonce, so other
     * apps can't launch a spoofed "Verified" Reveal through the exported activity.
     */
    private fun hasTrustedPayload(intent: Intent): Boolean =
        RevealPayloadCodec.hasPayload { intent.getStringExtra(it) } &&
            intent.getStringExtra(EXTRA_NONCE) == PROCESS_NONCE

    private fun isDuplicate(next: RevealPayload): Boolean =
        debouncer.isDuplicate(next.cardId, next.source, SystemClock.elapsedRealtime())

    private fun rememberTap(p: RevealPayload) = debouncer.accept(p.cardId, SystemClock.elapsedRealtime())

    private fun onMilestone(m: RevealMilestone) {
        val view = window.decorView
        when (m) {
            RevealMilestone.FIRST_VISUAL -> {
                trace.firstVisual()
                if (quality != RevealQuality.REDUCED_MOTION) KipatHaptics.revealStart(view)
            }
            RevealMilestone.IDENTITY_READABLE -> trace.identityReadable()
            RevealMilestone.VERIFIED -> if (payload.authenticity == AuthenticityState.AUTHENTIC) KipatHaptics.verified(view)
            RevealMilestone.COMPLETE -> {
                trace.complete()
                KipatRepository.recordTiming(
                    RevealTiming(
                        source = payload.source,
                        launchToCreateMs = trace.launchToCreate,
                        createToFirstFrameMs = trace.sinceCreate(trace.firstFrameAt),
                        createToIdentityMs = trace.sinceCreate(trace.identityReadableAt),
                        createToCompleteMs = trace.sinceCreate(trace.completeAt),
                        atMillis = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    private fun onConnect() {
        if (payload.source == RevealSource.PREVIEW) {
            // Preview shows the recipient's view; acting on your own card just closes it.
            finish()
            return
        }
        if (payload.canConnect && !connected) {
            KipatRepository.connect(payload)
            connected = true
            KipatHaptics.connect(window.decorView)
        }
    }

    private fun onSaveContact() {
        if (payload.source == RevealSource.PREVIEW) {
            finish()
            return
        }
        ContactSaver.save(this, payload.displayName, payload.subtitle, payload.organization, payload.cardId)
    }

    private fun onOpenProfile() {
        val destination = when {
            payload.source == RevealSource.PREVIEW -> KipatDestination.Profile.route
            else -> KipatRepository.connections.value.firstOrNull { it.cardId == payload.cardId }
                ?.let { KipatRoutes.person(it.id) } ?: KipatDestination.Connections.route
        }
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_DESTINATION, destination),
        )
        finish()
    }

    private fun observeFirstDraw() {
        val decor = window.decorView
        decor.viewTreeObserver.addOnDrawListener(object : ViewTreeObserver.OnDrawListener {
            override fun onDraw() {
                trace.firstFrame()
                decor.post { decor.viewTreeObserver.removeOnDrawListener(this) }
            }
        })
    }

    companion object {
        private const val STATE_RUN_KEY = "kipat.reveal.runKey"
        const val EXTRA_NONCE = "kipat.reveal.nonce"

        /** Random per-process token; never persisted or logged. */
        val PROCESS_NONCE: String = java.util.UUID.randomUUID().toString()
    }
}
