package com.kipat.probe.cards

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.util.Log
import com.kipat.probe.nfc.NfcAvailability
import com.kipat.probe.nfc.NfcProfileTagIo
import com.kipat.probe.nfc.NfcWriteController
import com.kipat.probe.nfc.WriteError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CardLinkState {
    data object Waiting : CardLinkState
    data class Working(val label: String) : CardLinkState
    data class ConfirmReplace(val existing: List<String>) : CardLinkState
    data class Linked(val card: LinkedKipatCard) : CardLinkState
    data class AlreadyLinked(val card: LinkedKipatCard) : CardLinkState
    data class Failed(val title: String, val detail: String?) : CardLinkState
}

/**
 * Profile → Kipat cards → Add Kipat card. Reader mode is on while the screen is resumed, so the
 * held card is handled here instead of opening a Reveal.
 *
 * Hold card → ownership and capacity checks → generate an opaque id → write the route →
 * reconnect and read back → only an exact read-back links the card to [ownerIdentityId].
 * A write that wasn't confirmed leaves nothing linked; tapping the same card again finishes it.
 */
class CardLinkSession(
    private val activity: Activity,
    private val scope: CoroutineScope,
    private val ownerIdentityId: () -> String,
) : NfcAdapter.ReaderCallback {
    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)
    private val _state = MutableStateFlow<CardLinkState>(CardLinkState.Waiting)
    val state: StateFlow<CardLinkState> = _state.asStateFlow()

    private var resumed = false
    private var readerEnabled = false
    private var replaceConfirmed = false
    private var heldTag: Tag? = null
    /** Written during this session but not yet confirmed by a read-back. */
    private var pendingCardId: String? = null

    fun availability(): NfcAvailability = NfcWriteController.availability(activity)

    fun confirmReplace() {
        replaceConfirmed = true
        val tag = heldTag ?: run {
            _state.value = CardLinkState.Waiting
            return
        }
        scope.launch(Dispatchers.IO) { link(tag) }
    }

    fun retry() {
        replaceConfirmed = false
        heldTag = null
        _state.value = CardLinkState.Waiting
    }

    fun onResume() {
        resumed = true
        updateReader()
    }

    fun onPause() {
        resumed = false
        updateReader()
    }

    override fun onTagDiscovered(tag: Tag) {
        when (_state.value) {
            CardLinkState.Waiting -> link(tag)
            is CardLinkState.ConfirmReplace -> heldTag = tag
            else -> Unit
        }
    }

    private fun link(tag: Tag) {
        _state.value = CardLinkState.Working("Checking card")
        val before = NfcProfileTagIo.snapshot(tag)
        if (before.readError != null && before.supportsNdef && before.maxSize == null) {
            _state.value = CardLinkState.Failed("Card moved away", "Hold the card still against the back of the phone and try again.")
            return
        }
        val owner = ownerIdentityId()
        val cardId = pendingCardId ?: KipatCardId.generate()
        val required = LinkedCardMessage.size(cardId, activity.packageName)
        val decision = CardLinkPlan.decide(before, required, owner, KipatCards::find, pendingCardId, replaceConfirmed)
        Log.i(TAG, "link_decision ${decision::class.simpleName} ndef=${before.ndefType} max=${before.maxSize} required=$required")
        when (decision) {
            LinkDecision.NotNdef -> return fail("This card can't be used", "Use an NFC Forum card such as NTAG213, NTAG215 or NTAG216.")
            LinkDecision.ReadOnly -> return fail("This card is locked", "It can't be written. Nothing was changed.")
            is LinkDecision.TooLarge -> return fail("This card is too small", "It holds ${decision.available} bytes; a Kipat card needs ${decision.required}.")
            LinkDecision.LinkedElsewhere -> return fail("This card belongs to another Kipat", "It is linked to someone else's Kipat, or to a Kipat on another phone. Nothing was changed.")
            is LinkDecision.AlreadyLinked -> {
                _state.value = CardLinkState.AlreadyLinked(decision.card)
                return
            }
            is LinkDecision.ConfirmReplace -> {
                heldTag = tag
                _state.value = CardLinkState.ConfirmReplace(decision.existing)
                return
            }
            is LinkDecision.ResumePending -> return verifyAndLink(tag, decision.cardId, owner)
            LinkDecision.Proceed -> Unit
        }
        _state.value = CardLinkState.Working("Linking card")
        pendingCardId = cardId
        when (val outcome = NfcProfileTagIo.write(tag, LinkedCardMessage.records(cardId, activity.packageName))) {
            is NfcProfileTagIo.WriteOutcome.Failed -> return when (outcome.error) {
                WriteError.TAG_LOST -> fail("Card moved away", "Hold the card still and tap it again to finish.")
                else -> fail(outcome.error.message, outcome.detail)
            }
            NfcProfileTagIo.WriteOutcome.Written -> Unit
        }
        verifyAndLink(tag, cardId, owner)
    }

    /** Reconnects and reads the card back; only an exact match links it. */
    private fun verifyAndLink(tag: Tag, cardId: String, owner: String) {
        _state.value = CardLinkState.Working("Checking card")
        val after = NfcProfileTagIo.snapshot(tag)
        if (!LinkedCardMessage.matches(LinkedCardMessage.records(cardId, activity.packageName), after.records)) {
            Log.w(TAG, "link_readback_mismatch card=${KipatCardId.masked(cardId)}")
            return fail("Couldn't confirm the card", "Hold the card still and tap it again to finish linking.")
        }
        val now = System.currentTimeMillis()
        val existing = KipatCards.forIdentity(owner).size
        val card = LinkedKipatCard(
            cardId = cardId,
            ownerIdentityId = owner,
            displayName = if (existing == 0) "Kipat card" else "Kipat card ${existing + 1}",
            status = LinkedCardStatus.ACTIVE,
            createdAt = now,
            lastSyncedAt = now,
        )
        KipatCards.add(card)
        pendingCardId = null
        heldTag = null
        replaceConfirmed = false
        _state.value = CardLinkState.Linked(card)
    }

    private fun fail(title: String, detail: String?) {
        heldTag = null
        replaceConfirmed = false
        _state.value = CardLinkState.Failed(title, detail)
    }

    private fun updateReader() {
        val nfc = adapter ?: return
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
            readerEnabled = false
        }
    }

    companion object {
        const val TAG = KipatCards.TAG
    }
}
