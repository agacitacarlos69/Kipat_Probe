package com.kipat.probe.nfc.peer

/**
 * What this phone can do for phone-to-phone NFC. Presenting needs NFC + Host Card Emulation;
 * receiving needs only an NFC reader. A phone without HCE can still receive.
 */
data class KipatPeerCapabilities(
    val hasNfc: Boolean,
    val nfcEnabled: Boolean,
    val hasHce: Boolean,
) {
    val canPresent: Boolean get() = hasNfc && hasHce
    val canReceive: Boolean get() = hasNfc

    /** Ready to run now (NFC switched on), as opposed to merely supported. */
    val presentReady: Boolean get() = canPresent && nfcEnabled
    val receiveReady: Boolean get() = canReceive && nfcEnabled

    /** One line for the share screen when something is missing; null when everything works. */
    val notice: String?
        get() = when {
            !hasNfc -> "This phone doesn't have NFC, so it can't share by touch."
            !nfcEnabled -> "Turn on NFC to share by touch."
            !hasHce -> "This phone can receive a Kipat but can't present one over NFC."
            else -> null
        }
}
