package com.kipat.probe.nfc.peer

import com.kipat.probe.nfc.profile.NfcProfilePayload

/** Which side of the NFC encounter this phone was on. Internal only: users never see roles. */
enum class TapRole { INITIATOR, RESPONDER }

/**
 * Hand-off between NFC transport (HCE service or the dispatched initiator) and the Reveal.
 *
 * One physical encounter can reach a phone twice: the controller may re-poll, Android may re-read
 * the bootstrap, or the roles may flip and both phones read each other. Delivery is keyed by the
 * peer's public profile id inside [windowMs]: the first one wins and later ones are duplicates, so
 * there is one Reveal and one recent tap per phone. A deliberate second tap after the window is
 * delivered again.
 */
class IncomingKipatExchangeStore(private val clock: () -> Long, private val windowMs: Long = DEDUPE_WINDOW_MS) {

    data class Delivery(val exchangeId: Long, val peer: NfcProfilePayload, val role: TapRole, val atMs: Long)

    private val recent = ArrayDeque<Delivery>()
    private val shown = HashSet<Long>()
    private var counter = 0L

    /** A new delivery, or null when this peer was already delivered within the window. */
    @Synchronized
    fun offer(peer: NfcProfilePayload, role: TapRole): Delivery? {
        val now = clock()
        while (recent.isNotEmpty() && now - recent.first().atMs > windowMs) recent.removeFirst()
        val key = keyOf(peer)
        if (recent.any { keyOf(it.peer) == key }) return null
        return Delivery(++counter, peer, role, now).also { recent.addLast(it) }
    }

    /** Called by the Reveal when it actually appeared; used to detect a blocked background launch. */
    @Synchronized
    fun markShown(exchangeId: Long) { shown += exchangeId }

    @Synchronized
    fun wasShown(exchangeId: Long): Boolean = exchangeId in shown

    @Synchronized
    fun latest(): Delivery? = recent.lastOrNull()

    private fun keyOf(p: NfcProfilePayload) = p.id ?: "name:${p.displayName}"

    companion object {
        /** Long enough to cover re-polls and a role flip in one touch; short enough for a deliberate re-tap. */
        const val DEDUPE_WINDOW_MS = 8_000L
    }
}

/** Whether this phone takes part in Kipat Tap right now. Users see a short sentence, never the enum. */
enum class KipatTapAvailability(val sentence: String) {
    READY("Kipat Tap ready"),
    NO_PROFILE("Publish your profile to use Kipat Tap"),
    NFC_DISABLED("NFC is off"),
    HCE_UNSUPPORTED("Phone-to-phone tap isn't supported on this device"),
    DISABLED_BY_USER("Kipat Tap is off"),
    DEVICE_LOCKED("Unlock your phone to use Kipat Tap"),
    UNAVAILABLE("This phone doesn't have NFC");

    companion object {
        fun of(enabled: Boolean, hasProfile: Boolean, caps: KipatPeerCapabilities, locked: Boolean): KipatTapAvailability = when {
            !caps.hasNfc -> UNAVAILABLE
            !caps.hasHce -> HCE_UNSUPPORTED
            !enabled -> DISABLED_BY_USER
            !hasProfile -> NO_PROFILE
            !caps.nfcEnabled -> NFC_DISABLED
            locked -> DEVICE_LOCKED
            else -> READY
        }
    }
}
