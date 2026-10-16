package com.kipat.probe.cards

import com.kipat.probe.data.PublicKipatProfileSnapshot
import com.kipat.probe.nfc.NfcIntentParser
import java.security.SecureRandom

/**
 * A physical Kipat card linked to a Kipat identity (docs/nfc-card-identity-sync.md).
 *
 * The card itself stores only `https://kipat.app/c/<cardId>` (plus an Android Application Record).
 * Everything a Reveal shows — name, fields, Reveal template, accent — is resolved from the owner
 * identity at tap time, so there is deliberately no profile, template or accent here.
 */
data class LinkedKipatCard(
    /** Opaque random id ([KipatCardId]); the only thing written to the card. */
    val cardId: String,
    val ownerIdentityId: String,
    /** The owner's own label for the card ("Kipat card", "Backpack card"); never shown to others. */
    val displayName: String,
    val status: LinkedCardStatus,
    val createdAt: Long,
    /** Last time this phone resolved a tap of the card; null if never. */
    val lastSeenAt: Long? = null,
    /** Last time the routing record was written and read back. A linked card never needs another. */
    val lastSyncedAt: Long,
    val cardType: LinkedCardType = LinkedCardType.NDEF_URI,
    val authenticityCapability: CardAuthenticityCapability = CardAuthenticityCapability.NONE,
) {
    /** "••••B871": enough to tell two cards apart without showing the id. */
    val maskedId: String get() = KipatCardId.masked(cardId)
}

enum class LinkedCardStatus(val wireId: String, val label: String) {
    ACTIVE("active", "Active"),
    /** Turned off by the owner; can be turned on again. A tap shows no profile. */
    DISABLED("disabled", "Turned off"),
    /** Permanently retired (lost, replaced). A tap shows no profile; cannot be re-enabled. */
    REVOKED("revoked", "Removed");

    companion object {
        fun fromWireId(value: String?): LinkedCardStatus? = entries.firstOrNull { it.wireId == value }
    }
}

/** How the card points at Kipat. Today only a plain NDEF URI on an NFC Forum tag (NTAG213/215/216). */
enum class LinkedCardType(val wireId: String, val label: String) {
    NDEF_URI("ndef_uri", "NFC tag, Kipat link");

    companion object {
        fun fromWireId(value: String?): LinkedCardType = entries.firstOrNull { it.wireId == value } ?: NDEF_URI
    }
}

/**
 * Whether a tap can prove the card is genuine. An NTAG URI can be copied byte for byte, so a
 * prototype card is always shown as Unverified, whatever Reveal it uses. NTAG 424 DNA (SUN/SDM
 * signed URLs checked by a backend) is the documented future; nothing here claims it.
 */
enum class CardAuthenticityCapability(val wireId: String) {
    NONE("none"),
    SUN_SIGNATURE("sun_signature");

    companion object {
        fun fromWireId(value: String?): CardAuthenticityCapability = entries.firstOrNull { it.wireId == value } ?: NONE
    }
}

/**
 * Linked-card ids: `kc_` + 16 Crockford base32 characters = 80 random bits from SecureRandom.
 * Not derived from the tag UID, a name or a counter, so ids can't be guessed or enumerated.
 */
object KipatCardId {
    const val PREFIX = "kc_"
    private const val BODY_LENGTH = 16
    private const val ALPHABET = "0123456789abcdefghjkmnpqrstvwxyz"
    private val PATTERN = Regex("^kc_[0-9a-hjkmnp-tv-z]{$BODY_LENGTH}$")

    fun generate(random: SecureRandom = SecureRandom()): String =
        PREFIX + (1..BODY_LENGTH).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")

    fun isValid(value: String?): Boolean = value != null && PATTERN.matches(value)

    /** True for anything that claims to be a linked-card id, valid or not. */
    fun looksLinked(value: String?): Boolean = value?.startsWith(PREFIX) == true

    fun masked(cardId: String): String = "••••" + cardId.takeLast(4).uppercase()

    /** What is written to the card: a route, nothing else. */
    fun uri(cardId: String): String {
        require(isValid(cardId)) { "Invalid linked card id" }
        return "https://${NfcIntentParser.CARD_HOST}/c/$cardId"
    }
}

/** The result of resolving a linked card. Status is always checked before any profile is read. */
sealed interface CardResolution {
    val cardId: String

    data class Resolved(val card: LinkedKipatCard, val snapshot: PublicKipatProfileSnapshot) : CardResolution {
        override val cardId get() = card.cardId
        /** The owner identity's profile revision this tap was resolved at. */
        val revision: Long get() = snapshot.revision
    }
    data class Disabled(override val cardId: String) : CardResolution
    data class Revoked(override val cardId: String) : CardResolution
    /** Well-formed id this resolver doesn't know (in the local prototype: linked on another phone). */
    data class Unknown(override val cardId: String) : CardResolution
    /** The owner has nothing shareable (no identity on this phone, or a profile over the limit). */
    data class ProfileUnavailable(override val cardId: String) : CardResolution
    data class Malformed(override val cardId: String) : CardResolution
}

/**
 * card id → current public profile of its owner identity. Implementations:
 *  - [LocalPrototypeCardResolver]: this phone's linked cards and this phone's identity (today).
 *  - SupabaseCardResolver (future, not built): the same contract against a shared backend, so any
 *    phone resolves any card. See docs/nfc-card-identity-sync.md.
 */
interface KipatCardResolver {
    suspend fun resolve(cardId: String): CardResolution
}

/**
 * Local prototype: resolves only cards linked on this phone, to the identity stored on this phone.
 * Nothing is synced anywhere; another phone tapping the same card gets [CardResolution.Unknown].
 *
 * [cards] looks a card up by id; [snapshotFor] returns the owner identity's current public
 * snapshot (null if that identity isn't on this phone). Both are read on every call, so a Reveal
 * or profile edit applies to the next tap with no rewrite of the card.
 */
class LocalPrototypeCardResolver(
    private val cards: (String) -> LinkedKipatCard?,
    private val snapshotFor: (ownerIdentityId: String) -> PublicKipatProfileSnapshot?,
) : KipatCardResolver {

    override suspend fun resolve(cardId: String): CardResolution = resolveNow(cardId)

    /** Same as [resolve]; synchronous because everything it reads is local. */
    fun resolveNow(cardId: String): CardResolution {
        if (!KipatCardId.isValid(cardId)) return CardResolution.Malformed(cardId)
        val card = cards(cardId) ?: return CardResolution.Unknown(cardId)
        // Status first: a turned-off or removed card never reads the profile.
        when (card.status) {
            LinkedCardStatus.DISABLED -> return CardResolution.Disabled(cardId)
            LinkedCardStatus.REVOKED -> return CardResolution.Revoked(cardId)
            LinkedCardStatus.ACTIVE -> Unit
        }
        val snapshot = snapshotFor(card.ownerIdentityId) ?: return CardResolution.ProfileUnavailable(cardId)
        return CardResolution.Resolved(card, snapshot)
    }
}
