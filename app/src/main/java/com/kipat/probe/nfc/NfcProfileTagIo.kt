package com.kipat.probe.nfc

import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import com.kipat.probe.nfc.profile.NdefRecordData
import com.kipat.probe.nfc.profile.TagSnapshot
import java.io.IOException

fun NdefRecord.toData(): NdefRecordData = NdefRecordData(tnf, type ?: ByteArray(0), id ?: ByteArray(0), payload ?: ByteArray(0))

fun NdefRecordData.toPlatform(): NdefRecord = NdefRecord(tnf, type, id, payload)

fun List<NdefRecordData>.toMessage(): NdefMessage = NdefMessage(map { it.toPlatform() }.toTypedArray())

/**
 * Blocking tag I/O for the NFC full profile test. Every call connects, does one thing and closes
 * in `finally`. Call from the reader-mode callback thread or Dispatchers.IO, never the main thread.
 */
object NfcProfileTagIo {

    sealed interface WriteOutcome {
        data object Written : WriteOutcome
        data class Failed(val error: WriteError, val detail: String? = null) : WriteOutcome
    }

    /** Reads everything the screen and the diagnostic report show. Never throws. */
    fun snapshot(tag: Tag): TagSnapshot {
        val uid = tag.id?.joinToString(":") { "%02X".format(it) }
        val techs = tag.techList.orEmpty().map { it.substringAfterLast('.') }
        val formatable = NdefFormatable.get(tag) != null
        val ndef = Ndef.get(tag) ?: return TagSnapshot(uid, techs, null, null, null, formatable, records = emptyList())
        return try {
            ndef.connect()
            // A fresh read (not the cached message), so a read straight after a write sees the card.
            val message = ndef.ndefMessage
            TagSnapshot(uid, techs, ndef.type, ndef.maxSize, ndef.isWritable, formatable, message?.records.orEmpty().map { it.toData() })
        } catch (e: FormatException) {
            TagSnapshot(uid, techs, ndef.type, ndef.maxSize, ndef.isWritable, formatable, null, "malformed NDEF message")
        } catch (e: TagLostException) {
            TagSnapshot(uid, techs, ndef.type, null, null, formatable, null, "card moved away")
        } catch (e: IOException) {
            TagSnapshot(uid, techs, ndef.type, null, null, formatable, null, e.localizedMessage ?: "I/O error")
        } catch (e: SecurityException) {
            TagSnapshot(uid, techs, ndef.type, null, null, formatable, null, "card moved away")
        } finally {
            try { ndef.close() } catch (_: IOException) {}
        }
    }

    /** Writes [records] as the whole NDEF message (formatting a blank NdefFormatable tag if needed). */
    fun write(tag: Tag, records: List<NdefRecordData>): WriteOutcome {
        val message = records.toMessage()
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            return try {
                ndef.connect()
                if (!ndef.isWritable) return WriteOutcome.Failed(WriteError.READ_ONLY)
                if (ndef.maxSize < message.byteArrayLength) return WriteOutcome.Failed(WriteError.INSUFFICIENT_CAPACITY)
                ndef.writeNdefMessage(message)
                WriteOutcome.Written
            } catch (e: TagLostException) {
                WriteOutcome.Failed(WriteError.TAG_LOST)
            } catch (e: FormatException) {
                WriteOutcome.Failed(WriteError.IO, e.localizedMessage)
            } catch (e: IOException) {
                WriteOutcome.Failed(WriteError.IO, e.localizedMessage)
            } catch (e: SecurityException) {
                WriteOutcome.Failed(WriteError.TAG_LOST, e.localizedMessage)
            } finally {
                try { ndef.close() } catch (_: IOException) {}
            }
        }
        val formatable = NdefFormatable.get(tag) ?: return WriteOutcome.Failed(WriteError.UNSUPPORTED)
        return try {
            formatable.connect()
            formatable.format(message)
            WriteOutcome.Written
        } catch (e: TagLostException) {
            WriteOutcome.Failed(WriteError.TAG_LOST)
        } catch (e: FormatException) {
            WriteOutcome.Failed(WriteError.FORMAT_FAILED, e.localizedMessage)
        } catch (e: IOException) {
            WriteOutcome.Failed(WriteError.FORMAT_FAILED, e.localizedMessage)
        } catch (e: SecurityException) {
            WriteOutcome.Failed(WriteError.TAG_LOST, e.localizedMessage)
        } finally {
            try { formatable.close() } catch (_: IOException) {}
        }
    }
}
