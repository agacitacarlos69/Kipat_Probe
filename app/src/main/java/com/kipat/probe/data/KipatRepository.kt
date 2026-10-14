package com.kipat.probe.data

import com.kipat.probe.model.Connection
import com.kipat.probe.model.ConnectionSource
import com.kipat.probe.model.Entitlement
import com.kipat.probe.model.Identity
import com.kipat.probe.model.KipatCard
import com.kipat.probe.model.RecentTap
import com.kipat.probe.reveal.RevealPayload
import com.kipat.probe.reveal.RevealSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** One measured Reveal, shown in Touch → Developer diagnostics. Values are real device timings. */
data class RevealTiming(
    val source: RevealSource,
    val launchToCreateMs: Long?,
    val createToFirstFrameMs: Long?,
    /** Name, role and organisation fully shown. */
    val createToIdentityMs: Long?,
    val createToCompleteMs: Long?,
    val atMillis: Long,
)

/**
 * In-memory, process-scoped store for this milestone. No persistence and no backend yet:
 * everything resets when the process dies. Kept behind this single object so a Room/Supabase
 * implementation can replace it without touching screens.
 */
object KipatRepository {
    private val now get() = System.currentTimeMillis()

    private val _identity = MutableStateFlow(SampleData.identity)
    val identity: StateFlow<Identity> = _identity.asStateFlow()

    private val _card = MutableStateFlow(SampleData.card)
    val card: StateFlow<KipatCard> = _card.asStateFlow()

    private val _entitlement = MutableStateFlow(Entitlement.FREE)
    val entitlement: StateFlow<Entitlement> = _entitlement.asStateFlow()

    private val _connections = MutableStateFlow(SampleData.connections(now))
    val connections: StateFlow<List<Connection>> = _connections.asStateFlow()

    private val _recentTaps = MutableStateFlow(SampleData.recentTaps(now))
    val recentTaps: StateFlow<List<RecentTap>> = _recentTaps.asStateFlow()

    private val _revealTimings = MutableStateFlow<List<RevealTiming>>(emptyList())
    val revealTimings: StateFlow<List<RevealTiming>> = _revealTimings.asStateFlow()

    private var idCounter = 0

    fun publishIdentity(identity: Identity) {
        _identity.value = identity
    }

    fun connection(id: String): Connection? = _connections.value.firstOrNull { it.id == id }

    fun togglePin(id: String) = _connections.update { list ->
        list.map { if (it.id == id) it.copy(pinned = !it.pinned) else it }
    }

    fun removeConnection(id: String): Connection? {
        val removed = connection(id)
        _connections.update { list -> list.filterNot { it.id == id } }
        return removed
    }

    fun restoreConnection(connection: Connection) = _connections.update { list ->
        if (list.any { it.id == connection.id }) list else list + connection
    }

    fun isConnected(cardId: String?): Boolean = cardId != null && _connections.value.any { it.cardId == cardId }

    /** Records that a Reveal happened. Does NOT create a connection. */
    fun recordTap(payload: RevealPayload) {
        if (!payload.source.isExternalTap) return
        // Tapping your own card (e.g. testing it) is not someone you met.
        if (payload.cardId != null && payload.cardId == _identity.value.cardId) return
        val tap = RecentTap(
            id = "tap-${now}-${idCounter++}",
            cardId = payload.cardId.orEmpty(),
            displayName = payload.displayName,
            subtitle = listOfNotNull(payload.subtitle, payload.organization).joinToString(", "),
            authenticity = payload.authenticity,
            atMillis = now,
            savedAsConnection = isConnected(payload.cardId),
            isDemo = payload.isDemo,
        )
        _recentTaps.update { (listOf(tap) + it).take(MAX_TAPS) }
    }

    /** Explicit user action ("Connect") — the only way a tap becomes a connection. */
    fun connect(payload: RevealPayload): Connection {
        _connections.value.firstOrNull { payload.cardId != null && it.cardId == payload.cardId }?.let { return it }
        val connection = Connection(
            id = "c-${now}-${idCounter++}",
            cardId = payload.cardId ?: "unknown-${idCounter}",
            displayName = payload.displayName,
            subtitle = payload.subtitle.orEmpty(),
            organization = payload.organization.orEmpty(),
            authenticity = payload.authenticity,
            source = when (payload.source) {
                RevealSource.NFC_TAG -> ConnectionSource.NFC_CARD
                RevealSource.PEER_NFC -> ConnectionSource.PHONE_TAP
                else -> ConnectionSource.LINK
            },
            connectedAtMillis = now,
            revealTemplate = payload.template,
            revealTheme = payload.theme,
            isDemo = payload.isDemo,
        )
        _connections.update { it + connection }
        _recentTaps.update { taps -> taps.map { if (it.cardId == payload.cardId) it.copy(savedAsConnection = true) else it } }
        return connection
    }

    /** "Connect" pressed on an unsaved recent tap (Home). Same rule: only an explicit action connects. */
    fun connect(tap: RecentTap): Connection {
        _connections.value.firstOrNull { it.cardId == tap.cardId }?.let { return it }
        val parts = tap.subtitle.split(" · ", ", ", limit = 2)
        val connection = Connection(
            id = "c-${now}-${idCounter++}",
            cardId = tap.cardId,
            displayName = tap.displayName,
            subtitle = parts.getOrNull(0).orEmpty(),
            organization = parts.getOrNull(1).orEmpty(),
            authenticity = tap.authenticity,
            source = ConnectionSource.NFC_CARD,
            connectedAtMillis = now,
            isDemo = tap.isDemo,
        )
        _connections.update { it + connection }
        _recentTaps.update { taps -> taps.map { if (it.id == tap.id) it.copy(savedAsConnection = true) else it } }
        return connection
    }

    fun recordTiming(timing: RevealTiming) = _revealTimings.update { (listOf(timing) + it).take(MAX_TIMINGS) }

    private const val MAX_TAPS = 20
    private const val MAX_TIMINGS = 10
}
