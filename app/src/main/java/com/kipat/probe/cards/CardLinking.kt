package com.kipat.probe.cards

import com.kipat.probe.nfc.NfcIntentParser
import com.kipat.probe.nfc.profile.Ndef
import com.kipat.probe.nfc.profile.NdefRecordData
import com.kipat.probe.nfc.profile.TagSnapshot

/**
 * The exact NDEF message a linked card carries: one URI record with the opaque route and an
 * Android Application Record. No name, profile, template or accent: those are resolved at tap time.
 */
object LinkedCardMessage {
    fun records(cardId: String, packageName: String): List<NdefRecordData> =
        listOf(Ndef.uri(KipatCardId.uri(cardId)), Ndef.aar(packageName))

    fun size(cardId: String, packageName: String): Int = Ndef.messageSize(records(cardId, packageName))

    /** The linked-card id in a tag's records, if any (valid or not; ownership decides what it means). */
    fun cardIdIn(records: List<NdefRecordData>?): String? = records.orEmpty()
        .firstNotNullOfOrNull { it.uriOrNull() }
        ?.let(NfcIntentParser::parse)
        ?.cardId
        ?.takeIf(KipatCardId::looksLinked)

    /** True only if [readBack] holds exactly the records written (type, TNF and payload of each). */
    fun matches(written: List<NdefRecordData>, readBack: List<NdefRecordData>?): Boolean =
        readBack != null && readBack.size == written.size && written.zip(readBack).all { (w, r) ->
            w.tnf == r.tnf && w.type.contentEquals(r.type) && w.payload.contentEquals(r.payload)
        }
}

sealed interface LinkDecision {
    data object Proceed : LinkDecision
    /** The card already holds something else; ask before replacing it. */
    data class ConfirmReplace(val existing: List<String>) : LinkDecision
    /** The card is already one of this identity's linked cards: nothing to write. */
    data class AlreadyLinked(val card: LinkedKipatCard) : LinkDecision
    /** The id this session just wrote: finish verifying it instead of writing a new one. */
    data class ResumePending(val cardId: String) : LinkDecision
    /** Someone else's linked card (or one linked on another phone): never taken over. */
    data object LinkedElsewhere : LinkDecision
    data class TooLarge(val required: Int, val available: Int) : LinkDecision
    data object ReadOnly : LinkDecision
    data object NotNdef : LinkDecision
}

/**
 * Checks made before any byte is written when linking a card, in order. Ownership comes before
 * writability or size: a card that belongs to another Kipat is refused even if it could be written.
 */
object CardLinkPlan {
    fun decide(
        tag: TagSnapshot,
        required: Int,
        ownerIdentityId: String,
        find: (String) -> LinkedKipatCard?,
        pendingCardId: String?,
        confirmedReplace: Boolean,
    ): LinkDecision {
        if (!tag.supportsNdef) return if (tag.formatable) LinkDecision.Proceed else LinkDecision.NotNdef
        LinkedCardMessage.cardIdIn(tag.records)?.let { existing ->
            if (existing == pendingCardId) return LinkDecision.ResumePending(existing)
            val known = find(existing)
            when {
                known == null || known.ownerIdentityId != ownerIdentityId -> return LinkDecision.LinkedElsewhere
                known.status != LinkedCardStatus.REVOKED -> return LinkDecision.AlreadyLinked(known)
                // A card this identity removed may be linked again, under a new id.
                else -> Unit
            }
        }
        if (tag.writable == false) return LinkDecision.ReadOnly
        val available = tag.maxSize ?: 0
        if (required > available) return LinkDecision.TooLarge(required, available)
        if (tag.hasData && !confirmedReplace) return LinkDecision.ConfirmReplace(tag.records?.map { it.summary() } ?: listOf("Unreadable NDEF data"))
        return LinkDecision.Proceed
    }
}
