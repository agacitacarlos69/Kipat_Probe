package com.kipat.probe.nfc

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Parcelable
import com.kipat.probe.nfc.profile.NdefRecordData

/**
 * Extracts the Kipat URI from a launch intent: the intent data for VIEW/NDEF_DISCOVERED, or the
 * first URI record of the delivered NDEF message as a fallback.
 */
object NfcIntents {
    fun uriFrom(intent: Intent?): String? {
        if (intent == null) return null
        intent.dataString?.let { return it }
        if (intent.action != NfcAdapter.ACTION_NDEF_DISCOVERED) return null
        return ndefMessages(intent)
            .flatMap { it.records.asList() }
            .firstNotNullOfOrNull { record -> runCatching { record.toUri()?.toString() }.getOrNull() }
    }

    fun isNfcLaunch(intent: Intent?): Boolean = intent?.action == NfcAdapter.ACTION_NDEF_DISCOVERED

    /** Every record of every delivered NDEF message, as platform-free data. */
    fun recordsFrom(intent: Intent?): List<NdefRecordData> {
        if (intent == null || intent.action != NfcAdapter.ACTION_NDEF_DISCOVERED) return emptyList()
        return ndefMessages(intent).flatMap { it.records.asList() }.map { it.toData() }
    }

    @Suppress("DEPRECATION")
    private fun ndefMessages(intent: Intent): List<NdefMessage> {
        val raw: Array<out Parcelable>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, NdefMessage::class.java)
        } else {
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
        }
        return raw?.filterIsInstance<NdefMessage>().orEmpty()
    }
}
