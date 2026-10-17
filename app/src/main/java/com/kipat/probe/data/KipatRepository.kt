package com.kipat.probe.data

import com.kipat.probe.model.Connection
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
 * The app's synchronous state, read by every screen and the Reveal. Local-first persistence sits
 * behind it: the identity in KipatIdentityStore, Connections and recent taps in Room via
 * [KipatLocalStore] (loaded into here at process start, written through on every change).
 * No backend yet. Until KipatLocalStore.init runs (unit tests), it holds sample data in memory only.
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

    private val _profileRevision = MutableStateFlow(0L)
    /** Increases on every published identity change (fields, Reveal template, accent). */
    val profileRevision: StateFlow<Long> = _profileRevision.asStateFlow()

    /**
     * Persists the identity and its revision (KipatIdentityStore) BEFORE the change is visible, so
     * no reader — UI, HCE service, card resolver — can see a new Reveal with an old profile.
     */
    @Volatile var identityPersistence: ((Identity, Long) -> Unit)? = null

    /** The one place the canonical identity changes: profile edits and Reveal style both land here. */
    @Synchronized
    fun publishIdentity(identity: Identity) {
        if (identity == _identity.value) return
        val revision = _profileRevision.value + 1
        identityPersistence?.invoke(identity, revision)
        _identity.value = identity
        _profileRevision.value = revision
    }

    /** Identity and revision read together (never a new identity with an old revision). */
    @Synchronized
    fun published(): Pair<Identity, Long> = _identity.value to _profileRevision.value

    /** Loads a persisted identity at process start (KipatIdentityStore.init). */
    @Synchronized
    fun restoreIdentity(identity: Identity, revision: Long) {
        _identity.value = identity
        _profileRevision.value = revision
    }

    /** Receives every Connection and recent-tap change (KipatLocalStore); null in pure unit tests. */
    @Volatile var connectionPersistence: ConnectionPersistence? = null

    /**
     * Loads persisted Connections and recent taps at process start. [includeSamples] (debug builds)
     * keeps the sample rows in memory next to real ones; sample rows are never persisted.
     */
    @Synchronized
    fun restoreLocal(connections: List<Connection>, taps: List<RecentTap>, includeSamples: Boolean) {
        val real = connections.distinctBy { it.cardId }
        val samples = if (includeSamples) SampleData.connections(now).filter { s -> real.none { it.cardId == s.cardId } } else emptyList()
        _connections.value = real + samples
        val sampleTaps = if (includeSamples) SampleData.recentTaps(now) else emptyList()
        _recentTaps.value = (taps + sampleTaps).sortedByDescending { it.atMillis }.take(MAX_TAPS)
    }

    fun connection(id: String): Connection? = _connections.value.firstOrNull { it.id == id }

    fun connectionFor(identityId: String?): Connection? =
        identityId?.let { key -> _connections.value.firstOrNull { it.cardId == key } }

    @Synchronized
    fun togglePin(id: String) {
        val updated = connection(id)?.let { it.copy(pinned = !it.pinned) } ?: return
        _connections.update { list -> list.map { if (it.id == id) updated else it } }
        connectionPersistence?.saveConnection(updated)
    }

    @Synchronized
    fun removeConnection(id: String): Connection? {
        val removed = connection(id) ?: return null
        _connections.update { list -> list.filterNot { it.id == id } }
        _recentTaps.update { taps -> taps.map { if (it.cardId == removed.cardId) it.copy(savedAsConnection = false) else it } }
        connectionPersistence?.deleteConnection(id)
        return removed
    }

    /** Undo of a removal: the same row comes back (same id, connectedAt, pin). */
    @Synchronized
    fun restoreConnection(connection: Connection) {
        if (_connections.value.any { it.id == connection.id || it.cardId == connection.cardId }) return
        _connections.update { it + connection }
        _recentTaps.update { taps -> taps.map { if (it.cardId == connection.cardId) it.copy(savedAsConnection = true) else it } }
        connectionPersistence?.saveConnection(connection)
    }

    fun isConnected(cardId: String?): Boolean = connectionFor(cardId) != null

    /**
     * Records that a Reveal happened. Does NOT create a Connection. If this person is already a
     * Connection, it is refreshed: lastSeenAt always; the public snapshot only from a [live] profile
     * (their phone, their linked card) that isn't older than the stored one ([ConnectionRules]).
     */
    @Synchronized
    fun recordTap(payload: RevealPayload, live: Boolean = payload.source == RevealSource.PEER_NFC, revision: Long? = null) {
        if (!payload.source.isExternalTap) return
        // Tapping your own card (e.g. testing it) is not someone you met.
        if (payload.cardId != null && payload.cardId == _identity.value.cardId) return
        val key = ConnectionRules.identityKeyOf(payload)
        val existing = connectionFor(key)
        if (existing != null) {
            val refreshed = ConnectionRules.refreshed(existing, payload, now, live = live && !payload.testProfile, revision = revision)
            if (refreshed != existing) {
                _connections.update { list -> list.map { if (it.id == existing.id) refreshed else it } }
                connectionPersistence?.saveConnection(refreshed)
            }
        }
        val tap = RecentTap(
            id = "tap-${now}-${idCounter++}",
            cardId = payload.cardId.orEmpty(),
            displayName = payload.displayName,
            subtitle = listOfNotNull(payload.subtitle, payload.organization).joinToString(", "),
            authenticity = payload.authenticity,
            atMillis = now,
            savedAsConnection = existing != null,
            isDemo = payload.isDemo,
            source = ConnectionRules.sourceOf(payload.source),
        )
        _recentTaps.update { (listOf(tap) + it).take(MAX_TAPS) }
        connectionPersistence?.saveTap(tap)
    }

    /** Explicit user action ("Connect") — the only way a tap becomes a connection. */
    @Synchronized
    fun connect(payload: RevealPayload, revision: Long? = null): Connection {
        connectionFor(ConnectionRules.identityKeyOf(payload))?.let { return it }
        val base = ConnectionRules.newConnection("c-${now}-${idCounter++}", payload, now, revision)
        // No usable public id (legacy test tag): kept in memory only, never persisted.
        val connection = if (base.cardId.isBlank()) base.copy(cardId = "unknown-${idCounter}") else base
        _connections.update { it + connection }
        _recentTaps.update { taps -> taps.map { if (it.cardId == connection.cardId) it.copy(savedAsConnection = true) else it } }
        connectionPersistence?.saveConnection(connection)
        return connection
    }

    /** "Connect" pressed on an unsaved recent tap (Home). Same rule: only an explicit action connects. */
    @Synchronized
    fun connect(tap: RecentTap): Connection {
        connectionFor(tap.cardId)?.let { return it }
        val parts = tap.subtitle.split(" · ", ", ", limit = 2)
        val connection = Connection(
            id = "c-${now}-${idCounter++}",
            cardId = tap.cardId,
            displayName = tap.displayName,
            subtitle = parts.getOrNull(0).orEmpty(),
            organization = parts.getOrNull(1).orEmpty(),
            authenticity = tap.authenticity,
            source = tap.source,
            connectedAtMillis = now,
            isDemo = tap.isDemo,
            lastSeenAtMillis = tap.atMillis,
        )
        _connections.update { it + connection }
        _recentTaps.update { taps -> taps.map { if (it.cardId == tap.cardId) it.copy(savedAsConnection = true) else it } }
        connectionPersistence?.saveConnection(connection)
        return connection
    }

    fun recordTiming(timing: RevealTiming) = _revealTimings.update { (listOf(timing) + it).take(MAX_TIMINGS) }

    private const val MAX_TAPS = KipatLocalStore.MAX_TAPS
    private const val MAX_TIMINGS = 10
}
