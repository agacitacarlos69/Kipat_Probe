package com.kipat.probe.nfc

/** Hardware/software NFC availability on this device. */
enum class NfcAvailability(val label: String) {
    UNSUPPORTED("No NFC hardware"),
    DISABLED("NFC is off"),
    ENABLED("NFC is on"),
}

enum class WriteError(val message: String) {
    NO_NFC("This device has no NFC hardware"),
    NFC_DISABLED("Turn on NFC in system settings first"),
    TAG_LOST("Tag moved away — hold it still and try again"),
    READ_ONLY("Tag is read-only"),
    INSUFFICIENT_CAPACITY("Tag capacity is too small for this payload"),
    UNSUPPORTED("Tag does not support NDEF"),
    FORMAT_FAILED("Could not format the tag"),
    IO("Write failed"),
}

/** State machine for the prototype card writer on the Touch screen. */
sealed interface TagWriteState {
    data object Idle : TagWriteState
    data class Armed(val uri: String) : TagWriteState
    data class Writing(val uri: String) : TagWriteState
    data class Success(val uri: String, val bytes: Int, val formatted: Boolean, val atMillis: Long) : TagWriteState
    data class Failed(val error: WriteError, val detail: String?, val atMillis: Long) : TagWriteState
}

val TagWriteState.label: String
    get() = when (this) {
        TagWriteState.Idle -> "Waiting"
        is TagWriteState.Armed -> "Armed — hold a blank NTAG 213/215/216 to the phone"
        is TagWriteState.Writing -> "Writing…"
        is TagWriteState.Success -> if (formatted) "Formatted & written ($bytes bytes)" else "Written successfully ($bytes bytes)"
        is TagWriteState.Failed -> error.message + (detail?.let { " — $it" } ?: "")
    }
