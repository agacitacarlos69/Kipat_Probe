package com.kipat.probe.nfc.peer

import com.kipat.probe.nfc.peer.KipatApduProtocol.shortId

/**
 * Minimal, read-only NFC Forum Type 4 Tag (T4T) emulation — just what Android's NDEF check needs
 * to read the Kipat bootstrap: SELECT the NDEF Tag Application, SELECT the Capability Container
 * (E103), READ BINARY it, SELECT the NDEF file (E104), READ BINARY NLEN and the message.
 * Writes are refused. Pure Kotlin, unit tested on exact bytes.
 *
 * The NDEF file is built when the application is selected, from the encounter session opened by
 * [PeerSessionStore.beginEncounter]; if Kipat Tap isn't eligible the application isn't there
 * (6A82), so the phone exposes nothing.
 */
class KipatType4Tag(
    private val store: PeerSessionStore,
    private val packageName: String,
    private val log: PeerLog = PeerLog.NONE,
) {
    private enum class File { NONE, CC, NDEF }

    private var appSelected = false
    private var file = File.NONE
    private var ndefFile = ByteArray(0)
    private var cc = ByteArray(0)

    fun process(apdu: ByteArray?): ByteArray {
        if (apdu == null || apdu.size < 4) return Sw.WRONG_LENGTH.bytes
        if (apdu[0] != 0x00.toByte()) return Sw.CLA_NOT_SUPPORTED.bytes
        return when (apdu[1]) {
            KipatApduProtocol.INS_SELECT -> when (apdu[2]) {
                0x04.toByte() -> selectApplication(apdu)
                0x00.toByte() -> selectFile(apdu)
                else -> Sw.WRONG_P1P2.bytes
            }
            INS_READ_BINARY -> readBinary(apdu)
            INS_UPDATE_BINARY -> SW_SECURITY_NOT_SATISFIED // read-only tag
            else -> Sw.INS_NOT_SUPPORTED.bytes
        }
    }

    fun reset() {
        appSelected = false
        file = File.NONE
    }

    private fun selectApplication(apdu: ByteArray): ByteArray {
        val aid = data(apdu) ?: return Sw.WRONG_LENGTH.bytes
        reset()
        if (!aid.contentEquals(NDEF_AID_V2)) return Sw.NOT_FOUND.bytes
        val session = store.beginEncounter() ?: return Sw.NOT_FOUND.bytes.also {
            log.event(PeerLog.TAG_TYPE4, "type4_refused", "reason=not_eligible")
        }
        val message = KipatPeerBootstrap.message(session.sessionId, packageName)
        ndefFile = byteArrayOf((message.size shr 8).toByte(), message.size.toByte()) + message
        cc = capabilityContainer(ndefFile.size)
        appSelected = true
        log.event(PeerLog.TAG_TYPE4, "type4_selected", "session=${session.sessionId.shortId()} ndef=${message.size}")
        return Sw.OK.bytes
    }

    private fun selectFile(apdu: ByteArray): ByteArray {
        if (!appSelected) return Sw.NOT_FOUND.bytes
        val id = data(apdu) ?: return Sw.WRONG_LENGTH.bytes
        if (id.size != 2) return Sw.WRONG_LENGTH.bytes
        file = when {
            id.contentEquals(CC_FILE_ID) -> File.CC
            id.contentEquals(NDEF_FILE_ID) -> File.NDEF
            else -> return Sw.NOT_FOUND.bytes.also { file = File.NONE }
        }
        return Sw.OK.bytes
    }

    private fun readBinary(apdu: ByteArray): ByteArray {
        if (apdu.size != 5) return Sw.WRONG_LENGTH.bytes
        val content = when (file) {
            File.CC -> cc
            File.NDEF -> ndefFile
            File.NONE -> return SW_NO_CURRENT_EF
        }
        val offset = ((apdu[2].toInt() and 0x7F) shl 8) or (apdu[3].toInt() and 0xFF)
        if (offset >= content.size) return Sw.WRONG_P1P2.bytes
        val le = (apdu[4].toInt() and 0xFF).let { if (it == 0) 256 else it }
        val end = minOf(content.size, offset + minOf(le, MAX_LE))
        if (file == File.NDEF && offset + (end - offset) >= content.size) {
            log.event(PeerLog.TAG_TYPE4, "bootstrap_read", "bytes=${content.size - 2}")
        }
        return content.copyOfRange(offset, end) + Sw.OK.bytes
    }

    /** Command data of SELECT: header(4) Lc data [Le]. */
    private fun data(apdu: ByteArray): ByteArray? {
        if (apdu.size < 5) return null
        val lc = apdu[4].toInt() and 0xFF
        if (lc == 0) return null
        return when (apdu.size) {
            5 + lc, 6 + lc -> apdu.copyOfRange(5, 5 + lc)
            else -> null
        }
    }

    companion object {
        /** NFC Forum Type 4 Tag, NDEF Tag Application (mapping version 2.0). */
        val NDEF_AID_V2: ByteArray = KipatApduProtocol.hex(NDEF_AID_V2_HEX)
        const val NDEF_AID_V2_HEX = "D2760000850101"
        val CC_FILE_ID = byteArrayOf(0xE1.toByte(), 0x03)
        val NDEF_FILE_ID = byteArrayOf(0xE1.toByte(), 0x04)
        const val INS_READ_BINARY: Byte = 0xB0.toByte()
        const val INS_UPDATE_BINARY: Byte = 0xD6.toByte()
        /** Largest R-APDU data we return; announced as MLe in the CC. */
        const val MAX_LE = 0xF6
        val SW_NO_CURRENT_EF = byteArrayOf(0x69, 0x86.toByte())
        val SW_SECURITY_NOT_SATISFIED = byteArrayOf(0x69, 0x82.toByte())

        /**
         * 15-byte Capability Container: CCLEN 000F, mapping version 2.0, MLe, MLc, then the NDEF
         * File Control TLV (T=04, L=06): file E104, max NDEF file size, read 00 (open),
         * write FF (no write access).
         */
        fun capabilityContainer(ndefFileSize: Int): ByteArray = byteArrayOf(
            0x00, 0x0F,
            0x20,
            0x00, MAX_LE.toByte(),
            0x00, 0xFF.toByte(),
            0x04, 0x06,
            0xE1.toByte(), 0x04,
            (ndefFileSize shr 8).toByte(), ndefFileSize.toByte(),
            0x00,
            0xFF.toByte(),
        )
    }
}

/**
 * Routes APDUs from [KipatHceService] to the application the reader selected: the Type 4 NDEF
 * bootstrap or the Kipat protocol. Both AIDs belong to the same service, so a reader can move from
 * one to the other on one link without Android deactivating anything.
 */
class KipatHceRouter(private val type4: KipatType4Tag, private val kipat: KipatApduProcessor) {
    private enum class App { NONE, TYPE4, KIPAT }

    private var active = App.NONE

    fun process(apdu: ByteArray?): ByteArray {
        if (apdu != null && apdu.size >= 5 && apdu[0] == 0x00.toByte() && apdu[1] == KipatApduProtocol.INS_SELECT && apdu[2] == 0x04.toByte()) {
            val lc = apdu[4].toInt() and 0xFF
            val aid = if (apdu.size >= 5 + lc) apdu.copyOfRange(5, 5 + lc) else ByteArray(0)
            active = when {
                aid.contentEquals(KipatType4Tag.NDEF_AID_V2) -> App.TYPE4
                aid.contentEquals(KipatApduProtocol.AID) -> App.KIPAT
                else -> App.NONE
            }
            return when (active) {
                App.TYPE4 -> type4.process(apdu)
                App.KIPAT -> kipat.process(apdu)
                App.NONE -> { type4.reset(); Sw.NOT_FOUND.bytes }
            }
        }
        return when (active) {
            App.TYPE4 -> type4.process(apdu)
            App.KIPAT -> kipat.process(apdu)
            App.NONE -> Sw.NOT_FOUND.bytes // nothing selected on this link
        }
    }

    fun onDeactivated(linkLost: Boolean) {
        type4.reset()
        kipat.onDeactivated(linkLost)
        active = App.NONE
    }
}
