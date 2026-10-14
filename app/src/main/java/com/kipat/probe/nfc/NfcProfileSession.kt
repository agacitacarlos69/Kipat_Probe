package com.kipat.probe.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.util.Log
import com.kipat.probe.nfc.profile.KipatProfileMessage
import com.kipat.probe.nfc.profile.NdefRecordData
import com.kipat.probe.nfc.profile.NfcProfileFormat
import com.kipat.probe.nfc.profile.NfcProfileMode
import com.kipat.probe.nfc.profile.NfcProfileParse
import com.kipat.probe.nfc.profile.NfcProfilePayload
import com.kipat.probe.nfc.profile.ProfileReadBack
import com.kipat.probe.nfc.profile.ProfileWritePlan
import com.kipat.probe.nfc.profile.TagSnapshot
import com.kipat.probe.nfc.profile.VerifyResult
import com.kipat.probe.nfc.profile.WriteDecision
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What is being written: the exact records and the profile they carry. */
data class ProfileWriteRequest(val profile: NfcProfilePayload, val mode: NfcProfileMode, val records: List<NdefRecordData>) {
    val size: Int get() = records.sumOf { it.encodedSize }
}

sealed interface ProfileTagState {
    data object Idle : ProfileTagState
    data class WaitingForCard(val writing: Boolean, val note: String? = null) : ProfileTagState
    data class Working(val label: String) : ProfileTagState

    /** A card was read. [profile] is null when the card holds no Kipat profile record. */
    data class Detected(val tag: TagSnapshot, val profile: NfcProfileParse?) : ProfileTagState
    data class ConfirmReplace(val tag: TagSnapshot, val existing: List<String>) : ProfileTagState
    data class TooLarge(val required: Int, val available: Int, val fieldCosts: List<Pair<String, Int>>) : ProfileTagState
    data class Verified(val tag: TagSnapshot, val profile: NfcProfilePayload, val messageBytes: Int) : ProfileTagState
    data class Failed(val title: String, val detail: String?) : ProfileTagState
}

/**
 * In-app read/write for the NFC full profile test, using reader mode so a card held to the phone
 * while this screen is open is handled here instead of launching the Reveal. Reader mode is on
 * while the screen is resumed; a tap does something only after Read or Write was chosen.
 *
 * Write flow: NDEF? → writable? → exact size vs Ndef.maxSize → confirm replacing existing data →
 * write → close → reconnect and read back → parse → compare. Only an identical read-back is
 * reported as verified.
 */
class NfcProfileSession(private val activity: Activity, private val scope: CoroutineScope) : NfcAdapter.ReaderCallback {
    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)
    private val _state = MutableStateFlow<ProfileTagState>(ProfileTagState.Idle)
    val state: StateFlow<ProfileTagState> = _state.asStateFlow()

    private var resumed = false
    private var readerEnabled = false
    private var pendingWrite: ProfileWriteRequest? = null
    private var replaceConfirmed = false
    private var heldTag: Tag? = null

    fun availability(): NfcAvailability = NfcWriteController.availability(activity)

    fun armRead() {
        if (!ready()) return
        pendingWrite = null
        _state.value = ProfileTagState.WaitingForCard(writing = false)
    }

    fun armWrite(request: ProfileWriteRequest) {
        if (!ready()) return
        pendingWrite = request
        replaceConfirmed = false
        _state.value = ProfileTagState.WaitingForCard(writing = true)
    }

    /** "Replace and write": write to the card still in the field, or ask for one more tap. */
    fun confirmReplace() {
        val request = pendingWrite ?: return
        replaceConfirmed = true
        val tag = heldTag
        if (tag == null) {
            _state.value = ProfileTagState.WaitingForCard(writing = true, note = "Tap the card again to write")
            return
        }
        _state.value = ProfileTagState.Working("Writing…")
        scope.launch(Dispatchers.IO) { write(tag, request) }
    }

    fun cancel() {
        pendingWrite = null
        heldTag = null
        replaceConfirmed = false
        _state.value = ProfileTagState.Idle
    }

    fun onResume() {
        resumed = true
        updateReader()
    }

    fun onPause() {
        resumed = false
        updateReader()
    }

    /** Reader-mode callback (binder thread): blocking I/O is allowed here. */
    override fun onTagDiscovered(tag: Tag) {
        val current = _state.value
        val request = pendingWrite
        when {
            current is ProfileTagState.WaitingForCard && request != null -> write(tag, request)
            current is ProfileTagState.WaitingForCard -> read(tag)
            // While the replace prompt is open, a re-tap just refreshes the card we'll write to.
            current is ProfileTagState.ConfirmReplace -> heldTag = tag
        }
    }

    private fun read(tag: Tag) {
        _state.value = ProfileTagState.Working("Reading…")
        val snapshot = NfcProfileTagIo.snapshot(tag)
        val profile = snapshot.profileRecord?.let { NfcProfileFormat.parse(it.payload) }
        _state.value = ProfileTagState.Detected(snapshot, profile)
        // Shape only, never field values: the profile content stays on the screen.
        Log.i(TAG, "read ndef=${snapshot.ndefType} max=${snapshot.maxSize} writable=${snapshot.writable} records=${snapshot.records?.size} bytes=${snapshot.messageSize} profile=${profile?.let { it::class.simpleName }}")
    }

    private fun write(tag: Tag, request: ProfileWriteRequest) {
        _state.value = ProfileTagState.Working("Checking card…")
        val before = NfcProfileTagIo.snapshot(tag)
        if (before.readError != null && before.supportsNdef && before.maxSize == null) {
            _state.value = ProfileTagState.WaitingForCard(writing = true, note = "Card moved away. Hold it still and tap again")
            return
        }
        val decision = ProfileWritePlan.decide(before, request.size, replaceConfirmed) {
            KipatProfileMessage.fieldCosts(request.profile, request.mode)
        }
        when (decision) {
            WriteDecision.NotNdef -> return finish(ProfileTagState.Failed("This card doesn't support NDEF", "Use an NFC Forum Type 2 card such as NTAG215 or NTAG216."))
            WriteDecision.ReadOnly -> return finish(ProfileTagState.Failed("This card is read-only", "It can't be rewritten. Nothing was changed."))
            is WriteDecision.TooLarge -> return finish(ProfileTagState.TooLarge(decision.required, decision.available, decision.fieldCosts))
            is WriteDecision.ConfirmReplace -> {
                heldTag = tag
                _state.value = ProfileTagState.ConfirmReplace(before, decision.existing)
                return
            }
            WriteDecision.Proceed -> Unit
        }

        _state.value = ProfileTagState.Working("Writing…")
        when (val outcome = NfcProfileTagIo.write(tag, request.records)) {
            is NfcProfileTagIo.WriteOutcome.Failed -> {
                if (outcome.error == WriteError.TAG_LOST) {
                    _state.value = ProfileTagState.WaitingForCard(writing = true, note = "Card moved away before the write finished. Tap again")
                } else {
                    finish(ProfileTagState.Failed(outcome.error.message, outcome.detail))
                }
                return
            }
            NfcProfileTagIo.WriteOutcome.Written -> Unit
        }

        // Reconnect and read back: the write only counts once the card returns the same profile.
        _state.value = ProfileTagState.Working("Verifying…")
        val after = NfcProfileTagIo.snapshot(tag)
        val result = ProfileReadBack.verify(request.records, request.profile, request.mode, after.records)
        finish(
            when (result) {
                is VerifyResult.Verified -> ProfileTagState.Verified(after, result.profile, result.messageBytes)
                is VerifyResult.Mismatch -> ProfileTagState.Failed(
                    "Write not verified",
                    "${result.reason.replaceFirstChar { it.uppercase() }}. Hold the card still and read it to check what it contains.",
                )
            },
        )
    }

    private fun finish(state: ProfileTagState) {
        Log.i(TAG, "write ${state::class.simpleName}" + when (state) {
            is ProfileTagState.TooLarge -> " required=${state.required} available=${state.available}"
            is ProfileTagState.Verified -> " bytes=${state.messageBytes} max=${state.tag.maxSize}"
            is ProfileTagState.Failed -> " ${state.title}"
            else -> ""
        })
        pendingWrite = null
        heldTag = null
        replaceConfirmed = false
        _state.value = state
    }

    private fun ready(): Boolean = when (availability()) {
        NfcAvailability.UNSUPPORTED -> {
            _state.value = ProfileTagState.Failed(WriteError.NO_NFC.message, "This test needs a phone with NFC.")
            false
        }
        NfcAvailability.DISABLED -> {
            _state.value = ProfileTagState.Failed(WriteError.NFC_DISABLED.message, null)
            false
        }
        NfcAvailability.ENABLED -> true
    }

    private fun updateReader() {
        val nfc = adapter ?: return
        // On for as long as the screen is resumed, not just while armed: switching it off with a
        // card still on the phone would let Android re-dispatch the tag and open the Reveal.
        val want = resumed
        if (want == readerEnabled) return
        try {
            if (want) {
                val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V
                nfc.enableReaderMode(activity, this, flags, Bundle())
            } else {
                nfc.disableReaderMode(activity)
            }
            readerEnabled = want
        } catch (e: IllegalStateException) {
            // Not in the foreground; retried on the next resume.
            readerEnabled = false
        }
    }

    companion object {
        const val TAG = "KipatNfcProfile"
    }
}
