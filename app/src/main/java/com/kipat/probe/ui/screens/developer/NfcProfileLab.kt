package com.kipat.probe.ui.screens.developer

import com.kipat.probe.nfc.ProfileWriteRequest
import com.kipat.probe.nfc.profile.KipatProfileMessage
import com.kipat.probe.nfc.profile.NfcProfileFormat
import com.kipat.probe.nfc.profile.NfcProfileMode
import com.kipat.probe.nfc.profile.NfcProfilePayload
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Process-scoped state of the NFC full profile test, shared by the test screen and its editor.
 * Kept in memory only: test profiles are never persisted by the app.
 */
object NfcProfileLab {
    val draft = MutableStateFlow<NfcProfilePayload?>(null)
    val mode = MutableStateFlow(NfcProfileMode.FULL)
    val includeAar = MutableStateFlow(true)

    /** What would be written for this draft, mode and AAR choice. */
    fun request(profile: NfcProfilePayload, mode: NfcProfileMode, includeAar: Boolean, packageName: String): ProfileWriteRequest {
        val effective = if (mode == NfcProfileMode.COMPACT) profile.compact() else profile
        return ProfileWriteRequest(effective, mode, KipatProfileMessage.records(effective, mode, packageName, includeAar))
    }

    /** Live size numbers for the calculator. */
    data class Size(val jsonBytes: Int, val messageBytes: Int, val aarBytes: Int)

    fun size(profile: NfcProfilePayload, mode: NfcProfileMode, includeAar: Boolean, packageName: String): Size {
        val json = NfcProfileFormat.toBytes(profile, mode).size
        val withAar = KipatProfileMessage.size(profile, mode, packageName, includeAar = true)
        val without = KipatProfileMessage.size(profile, mode, packageName, includeAar = false)
        return Size(json, if (includeAar) withAar else without, withAar - without)
    }

    /**
     * Typical usable NDEF capacity of common cards (NXP data sheets). Guidance only: the write
     * always compares against the card's own Ndef.getMaxSize.
     */
    val TYPICAL_CARDS = listOf("NTAG213" to 137, "NTAG215" to 496, "NTAG216" to 868)
}
