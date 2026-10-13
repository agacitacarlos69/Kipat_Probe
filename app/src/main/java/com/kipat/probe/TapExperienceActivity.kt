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
import com.kipat.probe.data.KipatRepository
import com.kipat.probe.data.RevealResolver
import com.kipat.probe.data.RevealTiming
import com.kipat.probe.data.ContactSaver
import com.kipat.probe.navigation.KipatDestination
import com.kipat.probe.navigation.KipatRoutes
import com.kipat.probe.nfc.NfcIntentParser
import com.kipat.probe.nfc.NfcIntents
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
import com.kipat.probe.model.AuthenticityState
import com.kipat.probe.ui.theme.KipatTheme

/**
 * Translucent Reveal host. Launched by:
 *  - NFC NDEF_DISCOVERED for Kipat card URIs (own task, so it floats above the previous app)
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
    private var lastTapKey: String? = null
    private var lastTapAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        trace = RevealTrace(intent?.getLongExtra(RevealLauncher.EXTRA_LAUNCH_UPTIME, -1L)?.takeIf { it > 0 })
        // The scrim follows the system theme (paper in light, graphite in dark), so do the bar icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        payload = resolve(intent)
        connected = KipatRepository.isConnected(payload.cardId)
        runKey = savedInstanceState?.getInt(STATE_RUN_KEY) ?: 0
        quality = RevealQualityPolicy.choose(this)
        if (savedInstanceState == null) {
            // Only real taps/links are recorded; in-app previews and replays are not new taps.
            if (!hasTrustedPayload(intent)) KipatRepository.recordTap(payload)
            rememberTap(payload)
        }

        setContent {
            KipatTheme {
                RevealScreen(
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
            val target = NfcIntentParser.parse(uri)
            if (target == null) {
                Log.w(RevealTrace.TAG, "unrecognised launch uri=$uri action=${intent?.action}")
                RevealPayload.unresolved(null, if (NfcIntents.isNfcLaunch(intent)) RevealSource.NFC_TAG else RevealSource.DEEP_LINK)
            } else {
                RevealResolver.resolve(
                    target = target,
                    ownIdentity = KipatRepository.identity.value,
                    ownCardIsDemo = KipatRepository.card.value.isDemo,
                    connections = KipatRepository.connections.value,
                )
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

    private fun isDuplicate(next: RevealPayload): Boolean {
        val key = next.cardId ?: return false
        return key == lastTapKey && SystemClock.elapsedRealtime() - lastTapAt < DUPLICATE_WINDOW_MS && next.source == RevealSource.NFC_TAG
    }

    private fun rememberTap(p: RevealPayload) {
        lastTapKey = p.cardId
        lastTapAt = SystemClock.elapsedRealtime()
    }

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
        private const val DUPLICATE_WINDOW_MS = 1_500L
        const val EXTRA_NONCE = "kipat.reveal.nonce"

        /** Random per-process token; never persisted or logged. */
        val PROCESS_NONCE: String = java.util.UUID.randomUUID().toString()
    }
}
