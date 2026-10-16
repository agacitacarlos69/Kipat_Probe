package com.kipat.probe.cards

import android.content.Context
import android.util.Log
import com.kipat.probe.data.KipatIdentityStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * This phone's linked Kipat cards (local prototype registry). Persisted to `kipat_cards.json`,
 * replaced atomically (temp + rename), readable from a fresh process started by a card tap.
 *
 * Cards hold ids and status only; the profile they show is always the owner identity's current
 * public snapshot ([KipatIdentityStore.publicSnapshot]). Many cards may point at one identity.
 */
object KipatCards {
    const val TAG = "KipatCards"
    private const val FILE = "kipat_cards.json"
    private const val FORMAT = 1

    private val _cards = MutableStateFlow<List<LinkedKipatCard>>(emptyList())
    val cards: StateFlow<List<LinkedKipatCard>> = _cards.asStateFlow()

    @Volatile private var file: File? = null

    @Synchronized
    fun init(context: Context) {
        if (file != null) return
        val f = File(context.applicationContext.filesDir, FILE)
        _cards.value = runCatching { if (f.exists()) decode(f.readText()) else emptyList() }.getOrElse {
            Log.w(TAG, "cards_file_unreadable")
            emptyList()
        }
        file = f
    }

    fun find(cardId: String): LinkedKipatCard? = _cards.value.firstOrNull { it.cardId == cardId }

    fun forIdentity(identityId: String): List<LinkedKipatCard> = _cards.value.filter { it.ownerIdentityId == identityId }

    /** The resolver every linked-card tap on this phone uses. */
    fun resolver(context: Context): KipatCardResolver {
        init(context)
        return LocalPrototypeCardResolver(
            cards = ::find,
            snapshotFor = { owner -> KipatIdentityStore.publicSnapshot(context)?.takeIf { it.identityId == owner } },
        )
    }

    @Synchronized
    fun add(card: LinkedKipatCard) {
        require(find(card.cardId) == null) { "Card already linked" }
        commit(_cards.value + card)
        Log.i(TAG, "card_linked card=${card.maskedId}")
    }

    /** ACTIVE ↔ DISABLED, or → REVOKED. A removed card stays removed. */
    @Synchronized
    fun setStatus(cardId: String, status: LinkedCardStatus): Boolean {
        val card = find(cardId) ?: return false
        if (card.status == LinkedCardStatus.REVOKED || card.status == status) return card.status == status
        commit(_cards.value.map { if (it.cardId == cardId) it.copy(status = status) else it })
        Log.i(TAG, "card_status card=${card.maskedId} status=${status.wireId}")
        return true
    }

    /** A tap of this card was resolved on this phone. [source] is logged as event metadata. */
    @Synchronized
    fun markSeen(cardId: String, at: Long, source: String) {
        val card = find(cardId) ?: return
        commit(_cards.value.map { if (it.cardId == cardId) it.copy(lastSeenAt = at) else it })
        Log.i(TAG, "card_seen card=${card.maskedId} source=$source status=${card.status.wireId}")
    }

    private fun commit(next: List<LinkedKipatCard>) {
        file?.let { f ->
            val tmp = File(f.parentFile, "${f.name}.tmp")
            tmp.writeText(encode(next))
            if (!tmp.renameTo(f)) {
                tmp.delete()
                error("Couldn't save linked cards")
            }
        }
        _cards.value = next
    }

    // ------------------------------------------------------------------------- serialization

    internal fun encode(cards: List<LinkedKipatCard>): String = JSONObject()
        .put("format", FORMAT)
        .put("cards", JSONArray().apply {
            cards.forEach { c ->
                put(
                    JSONObject()
                        .put("cardId", c.cardId)
                        .put("ownerIdentityId", c.ownerIdentityId)
                        .put("displayName", c.displayName)
                        .put("status", c.status.wireId)
                        .put("createdAt", c.createdAt)
                        .put("lastSeenAt", c.lastSeenAt ?: JSONObject.NULL)
                        .put("lastSyncedAt", c.lastSyncedAt)
                        .put("cardType", c.cardType.wireId)
                        .put("authenticity", c.authenticityCapability.wireId),
                )
            }
        })
        .toString()

    internal fun decode(text: String): List<LinkedKipatCard> {
        val array = JSONObject(text).optJSONArray("cards") ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            runCatching {
                val o = array.getJSONObject(i)
                val id = o.getString("cardId").takeIf(KipatCardId::isValid) ?: return@runCatching null
                LinkedKipatCard(
                    cardId = id,
                    ownerIdentityId = o.getString("ownerIdentityId"),
                    displayName = o.optString("displayName").ifBlank { "Kipat card" },
                    // An unreadable status is treated as turned off, never as active.
                    status = LinkedCardStatus.fromWireId(o.optString("status")) ?: LinkedCardStatus.DISABLED,
                    createdAt = o.optLong("createdAt"),
                    lastSeenAt = if (o.isNull("lastSeenAt")) null else o.optLong("lastSeenAt"),
                    lastSyncedAt = o.optLong("lastSyncedAt"),
                    cardType = LinkedCardType.fromWireId(o.optString("cardType")),
                    authenticityCapability = CardAuthenticityCapability.fromWireId(o.optString("authenticity")),
                )
            }.getOrNull()
        }
    }

    /** For tests: forget the in-memory registry (as in a fresh process). */
    internal fun resetForTest() {
        file = null
        _cards.value = emptyList()
    }
}
