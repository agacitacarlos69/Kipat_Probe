package com.kipat.probe.data

import com.kipat.probe.model.Identity
import com.kipat.probe.nfc.peer.KipatApduProtocol
import com.kipat.probe.nfc.peer.KipatPeerProfile
import com.kipat.probe.nfc.profile.NfcProfilePayload

/**
 * What other people may see of one Kipat identity: the single public payload behind every way of
 * sharing it (docs/nfc-card-identity-sync.md).
 *
 * ```
 * KIPAT IDENTITY ──► PublicKipatProfileSnapshot ──┬─► phone-to-phone Kipat Tap (HCE)
 *                                                 ├─► linked physical card (card id → resolver)
 *                                                 └─► profile preview / QR / link
 *                                                          ▼
 *                                                     RevealPayload → Reveal engine
 * ```
 *
 * Built only from [Identity.publishedFields] (via [KipatPeerProfile.fromIdentity]); serialized
 * and hashed once per [revision]. It includes the identity's current Reveal template and accent:
 * presentation belongs to the identity, never to a phone or a card.
 */
class PublicKipatProfileSnapshot(
    /** The identity it represents; null only for ad-hoc test profiles. */
    val identityId: String?,
    /** Increases whenever any public field, the Reveal template or the accent changes. */
    val revision: Long,
    val profile: NfcProfilePayload,
    val bytes: ByteArray,
    val sha256: ByteArray,
) {
    companion object {
        fun of(identity: Identity, revision: Long): PublicKipatProfileSnapshot? =
            of(KipatPeerProfile.fromIdentity(identity), identity.id, revision)

        /** Null when the profile is over the protocol limit: it is then never shared. */
        fun of(profile: NfcProfilePayload, identityId: String? = null, revision: Long = 0): PublicKipatProfileSnapshot? {
            val bytes = KipatPeerProfile.toBytes(profile)
            if (bytes.size > KipatApduProtocol.MAX_PROFILE_BYTES) return null
            return PublicKipatProfileSnapshot(identityId, revision, profile, bytes, KipatApduProtocol.sha256(bytes))
        }
    }
}
