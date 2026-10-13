package com.kipat.probe.nfc

import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import java.io.IOException

/**
 * Prototype NTAG writer migrated from the original probe's MainActivity.java.
 *
 * Preserves: Ndef vs NdefFormatable paths, writable check, capacity check, close() in finally,
 * and the Android Application Record (AAR) so tapping the card opens Kipat when installed.
 * Blocking I/O — always call from a background dispatcher.
 */
object NfcTagWriter {

    sealed interface Result {
        data class Written(val bytes: Int, val formatted: Boolean) : Result
        data class Error(val error: WriteError, val detail: String? = null) : Result
    }

    fun buildMessage(uri: String, packageName: String, includeAar: Boolean = true): NdefMessage {
        val records = buildList {
            add(NdefRecord.createUri(uri))
            if (includeAar) add(NdefRecord.createApplicationRecord(packageName))
        }
        return NdefMessage(records.toTypedArray())
    }

    fun write(tag: Tag, message: NdefMessage): Result {
        val size = message.byteArrayLength
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            return try {
                ndef.connect()
                when {
                    !ndef.isWritable -> Result.Error(WriteError.READ_ONLY)
                    ndef.maxSize < size -> Result.Error(WriteError.INSUFFICIENT_CAPACITY, "needs $size bytes, tag holds ${ndef.maxSize}")
                    else -> {
                        ndef.writeNdefMessage(message)
                        Result.Written(size, formatted = false)
                    }
                }
            } catch (e: TagLostException) {
                Result.Error(WriteError.TAG_LOST)
            } catch (e: FormatException) {
                Result.Error(WriteError.IO, e.localizedMessage)
            } catch (e: IOException) {
                Result.Error(WriteError.IO, e.localizedMessage)
            } catch (e: SecurityException) {
                // Thrown if the tag object is stale (tag left the field and was rediscovered).
                Result.Error(WriteError.TAG_LOST, e.localizedMessage)
            } finally {
                try { ndef.close() } catch (_: IOException) {}
            }
        }

        val formatable = NdefFormatable.get(tag) ?: return Result.Error(WriteError.UNSUPPORTED)
        return try {
            formatable.connect()
            formatable.format(message)
            Result.Written(size, formatted = true)
        } catch (e: TagLostException) {
            Result.Error(WriteError.TAG_LOST)
        } catch (e: FormatException) {
            Result.Error(WriteError.FORMAT_FAILED, e.localizedMessage)
        } catch (e: IOException) {
            Result.Error(WriteError.FORMAT_FAILED, e.localizedMessage)
        } catch (e: SecurityException) {
            Result.Error(WriteError.TAG_LOST, e.localizedMessage)
        } finally {
            try { formatable.close() } catch (_: IOException) {}
        }
    }
}
