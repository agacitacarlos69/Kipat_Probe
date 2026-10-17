package com.kipat.probe.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.room.Room
import com.kipat.probe.data.db.ConnectionEntity
import com.kipat.probe.data.db.KipatDatabase
import com.kipat.probe.data.db.RecentTapEntity
import com.kipat.probe.model.AuthenticityState
import com.kipat.probe.model.Connection
import com.kipat.probe.model.ConnectionSource
import com.kipat.probe.model.RecentTap
import com.kipat.probe.nfc.profile.NfcProfileFormat
import com.kipat.probe.reveal.RevealPayload
import com.kipat.probe.reveal.RevealTemplateId
import com.kipat.probe.reveal.RevealTheme
import com.kipat.probe.reveal.sanitizeId
import java.lang.ref.WeakReference
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Local-first persistence for Connections and recent taps (Room, `kipat.db`). Works offline,
 * survives process death, needs no backend.
 *
 * [KipatRepository] stays the synchronous in-memory source the UI and the Reveal read. This store:
 *  1. loads the persisted rows into it once per process (before any screen or tap reads it);
 *  2. receives every change and writes it on ONE ordered background thread, so writes never block
 *     the Reveal and always land in the order they happened.
 *
 * Sample data (`isDemo`) is never written. Debug builds show it in memory next to real rows so the
 * app isn't empty during development; release builds start empty.
 */
object KipatLocalStore : ConnectionPersistence {
    private const val TAG = "KipatLocalStore"
    const val MAX_TAPS = 20

    private val io: ExecutorService = Executors.newSingleThreadExecutor { r -> Thread(r, "kipat-db").apply { isDaemon = true } }

    /** Which application the open database belongs to (weak: never keeps a Context alive). */
    @Volatile private var app: WeakReference<Context>? = null
    @Volatile private var db: KipatDatabase? = null

    /** Opens the database and loads it into [KipatRepository]. Idempotent per application. */
    @Synchronized
    fun init(context: Context) {
        val appContext = context.applicationContext
        if (app?.get() === appContext && db != null) return
        db?.close()
        val database = Room.databaseBuilder(appContext, KipatDatabase::class.java, KipatDatabase.NAME).build()
        db = database
        app = WeakReference(appContext)
        // Blocking, but off the main thread for Room: a few rows, a few ms, and nothing may read
        // Connections before this (otherwise a cold card tap could miss an existing Connection).
        val (connections, taps) = io.submit(Callable { load(database) }).get(5, TimeUnit.SECONDS)
        KipatRepository.restoreLocal(connections, taps, includeSamples = isDebuggable(appContext))
        KipatRepository.connectionPersistence = this
        Log.i(TAG, "loaded connections=${connections.size} taps=${taps.size}")
    }

    private fun load(database: KipatDatabase): Pair<List<Connection>, List<RecentTap>> {
        val connections = database.connections().all().mapNotNull(::toConnection)
        val taps = database.recentTaps().latest(MAX_TAPS).mapNotNull { e -> toTap(e, connections) }
        return connections to taps
    }

    // ------------------------------------------------------------------ ConnectionPersistence

    override fun saveConnection(connection: Connection) {
        val entity = toEntity(connection) ?: return
        write { it.connections().save(entity) }
    }

    override fun deleteConnection(id: String) = write { it.connections().delete(id) }

    override fun saveTap(tap: RecentTap) {
        val entity = toEntity(tap) ?: return
        write { it.recentTaps().add(entity, MAX_TAPS) }
    }

    private fun write(block: (KipatDatabase) -> Unit) {
        val database = db ?: return
        io.execute {
            try {
                block(database)
            } catch (t: Throwable) {
                // A failed write must never crash the Reveal; the in-memory state still shows it.
                Log.e(TAG, "write failed", t)
            }
        }
    }

    /** Waits for every queued write (tests, and before deliberately closing). */
    fun flush() {
        io.submit {}.get(5, TimeUnit.SECONDS)
    }

    /** For tests: close the database and forget it, as in a new process. */
    @Synchronized
    internal fun resetForTest() {
        flush()
        db?.close()
        db = null
        app = null
        KipatRepository.connectionPersistence = null
    }

    private fun isDebuggable(context: Context) = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    // ------------------------------------------------------------------ mapping (defensive both ways)

    private const val MAX_TEXT = RevealPayload.MAX_LINE
    private fun clean(s: String?, max: Int = MAX_TEXT): String? =
        s?.replace(Regex("[\\u0000-\\u001F\\u007F]"), " ")?.trim()?.take(max)?.takeIf { it.isNotEmpty() }

    /** Null = not storable (sample data, or no usable public id: kept in memory only). */
    internal fun toEntity(c: Connection): ConnectionEntity? {
        if (c.isDemo) return null
        val identity = sanitizeId(c.cardId) ?: return null
        val name = clean(c.displayName, RevealPayload.MAX_NAME) ?: return null
        return ConnectionEntity(
            id = clean(c.id, 64) ?: return null,
            identityId = identity,
            displayName = name,
            headline = clean(c.subtitle).orEmpty(),
            role = clean(c.role),
            organization = clean(c.organization).orEmpty(),
            avatarUrl = c.avatarUrl?.takeIf { it.startsWith("https://") && it.length <= 512 },
            profileRevision = c.profileRevision?.takeIf { it >= 0 },
            revealTemplate = c.revealTemplate.stableId,
            accent = NfcProfileFormat.accentWire(c.revealTheme),
            authenticity = c.authenticity.wireId,
            connectedAt = c.connectedAtMillis,
            lastSeenAt = c.lastSeenAtMillis,
            source = c.source.name,
            isPinned = c.pinned,
        )
    }

    internal fun toConnection(e: ConnectionEntity): Connection? {
        val identity = sanitizeId(e.identityId) ?: return null
        val name = clean(e.displayName, RevealPayload.MAX_NAME) ?: return null
        return Connection(
            id = e.id,
            cardId = identity,
            displayName = name,
            subtitle = clean(e.headline).orEmpty(),
            organization = clean(e.organization).orEmpty(),
            authenticity = AuthenticityState.fromWireId(e.authenticity),
            source = ConnectionSource.entries.firstOrNull { it.name == e.source } ?: ConnectionSource.LINK,
            connectedAtMillis = e.connectedAt,
            pinned = e.isPinned,
            revealTemplate = RevealTemplateId.fromStableId(e.revealTemplate) ?: RevealTemplateId.DEFAULT,
            revealTheme = NfcProfileFormat.accentOf(e.accent) ?: RevealTheme.NIGHT,
            isDemo = false,
            role = clean(e.role),
            avatarUrl = e.avatarUrl?.takeIf { it.startsWith("https://") },
            profileRevision = e.profileRevision,
            lastSeenAtMillis = e.lastSeenAt,
        )
    }

    internal fun toEntity(t: RecentTap): RecentTapEntity? {
        if (t.isDemo) return null
        val identity = sanitizeId(t.cardId) ?: return null
        return RecentTapEntity(
            id = clean(t.id, 64) ?: return null,
            identityId = identity,
            displayName = clean(t.displayName, RevealPayload.MAX_NAME) ?: return null,
            subtitle = clean(t.subtitle).orEmpty(),
            authenticity = t.authenticity.wireId,
            atMillis = t.atMillis,
            source = t.source.name,
        )
    }

    internal fun toTap(e: RecentTapEntity, connections: List<Connection>): RecentTap? {
        val identity = sanitizeId(e.identityId) ?: return null
        return RecentTap(
            id = e.id,
            cardId = identity,
            displayName = clean(e.displayName, RevealPayload.MAX_NAME) ?: return null,
            subtitle = clean(e.subtitle).orEmpty(),
            authenticity = AuthenticityState.fromWireId(e.authenticity),
            atMillis = e.atMillis,
            // Derived, never stored: removing a Connection makes the tap "not saved" again.
            savedAsConnection = connections.any { it.cardId == identity },
            isDemo = false,
            source = ConnectionSource.entries.firstOrNull { it.name == e.source } ?: ConnectionSource.NFC_CARD,
        )
    }
}

/** Where [KipatRepository] sends Connection and recent-tap changes. */
interface ConnectionPersistence {
    fun saveConnection(connection: Connection)
    fun deleteConnection(id: String)
    fun saveTap(tap: RecentTap)
}
