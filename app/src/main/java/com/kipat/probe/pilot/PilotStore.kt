package com.kipat.probe.pilot

import android.content.Context
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.edit
import com.kipat.probe.BuildConfig
import com.kipat.probe.data.KipatRepository
import com.kipat.probe.nfc.peer.KipatTapStore
import com.kipat.probe.reveal.RevealPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.Executors

/**
 * Android side of the physical-pilot diagnostics: owns the [PilotTracker], persists a bounded
 * snapshot to app-private storage (survives the HCE process dying between a tap and opening the
 * pilot screen), and keeps the tester's checklist. Writes happen on one background thread.
 */
object PilotStore {
    private const val TAG = "KipatPilot"
    private const val FILE = "pilot_diagnostics.json"
    private const val PREFS = "kipat_pilot"
    private const val CLEARED_AT = "results_cleared_at"

    val tracker = PilotTracker(monoClock = SystemClock::elapsedRealtime, wallClock = System::currentTimeMillis)
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "kipat-pilot").apply { isDaemon = true } }

    @Volatile private var app: WeakReference<Context>? = null
    @Volatile private var loaded = false
    @Volatile private var peer: PeerSeen? = null
    /** The peer's public id, kept only in memory and app-private storage to check "Connection stored". */
    @Volatile private var peerKey: String? = null
    @Volatile private var card: CardTestRecord? = null

    private val _version = MutableStateFlow(0L)
    /** Bumps whenever anything shown on the pilot screen changes. */
    val version: StateFlow<Long> = _version.asStateFlow()

    val build: BuildMetadata
        get() = BuildMetadata(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, BuildConfig.BUILD_NUMBER, BuildConfig.GIT_SHA, BuildConfig.BUILD_TIME, BuildConfig.BUILD_TYPE)

    /**
     * Called from KipatPeerSessions.attach (service, app, Reveal). Loads the saved snapshot once, on
     * the pilot thread: the HCE service's cold start (and its first NDEF answer) never waits on it.
     */
    @Synchronized
    fun attach(context: Context) {
        val appContext = context.applicationContext
        app = WeakReference(appContext)
        if (loaded) return
        loaded = true
        io.execute {
            runCatching { File(appContext.filesDir, FILE).takeIf { it.exists() }?.readText()?.let { decode(it, merge = true) } }
                .onFailure { Log.w(TAG, "pilot snapshot unreadable, starting fresh") }
            _version.value = _version.value + 1
        }
    }

    /** Every structured NFC log event comes through here (content-free; redacted again by the tracker). */
    fun record(name: String, detail: String = "", tag: String = "") {
        if (tracker.onEvent(name, detail, tag)) changed()
    }

    /** The other person, once their profile is validated. The name is shown on screen only. */
    fun peerSeen(payload: RevealPayload) {
        val key = payload.cardId ?: return
        peerKey = key
        peer = PeerSeen(payload.displayName.take(RevealPayload.MAX_NAME), PilotReport.pseudonym(key), payload.template.label, payload.theme.label, System.currentTimeMillis())
        changed()
    }

    fun cardResult(record: CardTestRecord) {
        card = record
        tracker.onEvent(if (record.action == "write") "card_write" else "card_read", "result=${record.result}")
        changed()
    }

    fun connectionStored(): Boolean? = peerKey?.let { KipatRepository.isConnected(it) }

    // ------------------------------------------------------------------ checklist

    fun checklist(context: Context): Map<PilotCheck, Boolean> {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return PilotCheck.entries.associateWith { p.getBoolean(it.name, false) }
    }

    fun setCheck(context: Context, check: PilotCheck, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(check.name, value) }
        changed()
    }

    /** Clear results before a new test round (events, attempts, last peer, card, checklist). */
    fun reset(context: Context) {
        tracker.clear()
        peer = null; peerKey = null; card = null
        // Exported, so an empty buffer can be told apart from "nothing happened".
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { clear(); putLong(CLEARED_AT, System.currentTimeMillis()) }
        changed()
    }

    // ------------------------------------------------------------------ snapshot for screen and export

    fun state(context: Context): PilotState {
        val pm = context.packageManager
        val adapter = runCatching { NfcAdapter.getDefaultAdapter(context) }.getOrNull()
        val device = DeviceSnapshot(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            androidVersion = Build.VERSION.RELEASE.orEmpty(),
            api = Build.VERSION.SDK_INT,
            hasNfc = pm.hasSystemFeature(PackageManager.FEATURE_NFC) && adapter != null,
            nfcEnabled = adapter?.isEnabled == true,
            hasHce = pm.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION),
            kipatTap = runCatching { KipatTapStore.availability(context).name }.getOrDefault("UNKNOWN"),
        )
        val identity = KipatRepository.identity.value
        val profile = ProfileSnapshot(identity.cardId, identity.displayName, identity.revealTemplate.label, identity.revealTheme.label)
        val pkg = runCatching { pm.getPackageInfo(context.packageName, 0) }.getOrNull()
        val cleared = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(CLEARED_AT, 0L).takeIf { it > 0 }
        return PilotState(
            build, device, profile, tracker.last(), tracker.attempts(), tracker.events(), peer, connectionStored(), card, checklist(context),
            installedAtMs = pkg?.firstInstallTime, updatedAtMs = pkg?.lastUpdateTime, clearedAtMs = cleared,
            routing = runCatching { HceRoutingCheck.check(context) }.getOrNull(),
        )
    }

    // ------------------------------------------------------------------ persistence

    private fun changed() {
        _version.value = _version.value + 1
        val context = app?.get() ?: return
        // Serialized on the pilot thread, after the saved history was merged (same single thread).
        io.execute {
            val text = encode()
            runCatching {
                val dir = context.filesDir
                val tmp = File(dir, "$FILE.tmp")
                tmp.writeText(text)
                if (!tmp.renameTo(File(dir, FILE))) tmp.delete()
            }.onFailure { Log.w(TAG, "pilot snapshot not saved") }
        }
    }

    internal fun encode(): String = JSONObject()
        .put("events", PilotReport.eventsJsonRaw(tracker.events()))
        .put("attempts", JSONArray().apply { tracker.attempts().forEach { put(attemptToJson(it)) } })
        .put("peer", peer?.let { JSONObject().put("name", it.name).put("idHash", it.idHash).put("reveal", it.reveal).put("accent", it.accent).put("at", it.atWallMs) } ?: JSONObject.NULL)
        .put("peerKey", peerKey ?: JSONObject.NULL)
        .put("card", card?.let { c -> JSONObject().put("action", c.action).put("technologies", c.technologies).put("ndef", c.ndefType ?: JSONObject.NULL).put("capacity", c.capacityBytes ?: JSONObject.NULL).put("writable", c.writable ?: JSONObject.NULL).put("bytes", c.messageBytes ?: JSONObject.NULL).put("result", c.result).put("at", c.atWallMs) } ?: JSONObject.NULL)
        .toString()

    internal fun decode(text: String, merge: Boolean = false) {
        val o = JSONObject(text)
        val events = o.optJSONArray("events")?.let { a -> (0 until a.length()).map { i -> a.getJSONObject(i) }.map { e -> PilotEvent(e.getLong("wall"), e.getString("name"), e.getString("result"), e.optLong("elapsed", -1).takeIf { it >= 0 }, e.optString("detail")) } }.orEmpty()
        val attempts = o.optJSONArray("attempts")?.let { a -> (0 until a.length()).mapNotNull { i -> runCatching { attemptFromJson(a.getJSONObject(i)) }.getOrNull() } }.orEmpty()
        if (merge) tracker.prependSaved(events, attempts) else tracker.restore(events, attempts)
        if (merge && peer != null) return // something newer already arrived in this process
        peer = o.optJSONObject("peer")?.let { PeerSeen(it.getString("name"), it.getString("idHash"), it.getString("reveal"), it.getString("accent"), it.getLong("at")) }
        peerKey = o.optString("peerKey").takeIf { it.isNotBlank() && it != "null" }
        card = o.optJSONObject("card")?.let { c ->
            CardTestRecord(c.getString("action"), c.getString("technologies"), c.optString("ndef").takeIf { it.isNotBlank() && it != "null" },
                c.optInt("capacity", -1).takeIf { it >= 0 }, if (c.isNull("writable")) null else c.getBoolean("writable"),
                c.optInt("bytes", -1).takeIf { it >= 0 }, c.getString("result"), c.getLong("at"))
        }
    }

    private fun attemptToJson(a: TapAttempt) = JSONObject()
        .put("id", a.id).put("role", a.role.name).put("wall", a.startedWallMs).put("mono", a.startedMonoMs)
        .put("failure", a.failureCode ?: JSONObject.NULL).put("finished", a.finishedMonoMs ?: JSONObject.NULL)
        .put("x", a.correlation ?: JSONObject.NULL)
        .put("stages", JSONObject().apply { a.stages.forEach { (s, st) -> put(s.name, JSONObject().put("r", st.result.name).put("at", st.atMs ?: JSONObject.NULL)) } })

    private fun attemptFromJson(o: JSONObject): TapAttempt {
        val stages = o.getJSONObject("stages").let { st ->
            st.keys().asSequence().mapNotNull { k ->
                val stage = PilotStage.entries.firstOrNull { it.name == k } ?: return@mapNotNull null
                val v = st.getJSONObject(k)
                stage to StageState(StageResult.valueOf(v.getString("r")), if (v.isNull("at")) null else v.getLong("at"))
            }.toMap()
        }
        return TapAttempt(
            id = o.getInt("id"),
            role = TapRoleSeen.valueOf(o.getString("role")),
            startedWallMs = o.getLong("wall"),
            startedMonoMs = o.getLong("mono"),
            stages = stages,
            failureCode = o.optString("failure").takeIf { it.isNotBlank() && it != "null" },
            finishedMonoMs = if (o.isNull("finished")) null else o.getLong("finished"),
            correlation = o.optString("x").takeIf { it.startsWith("e-") },
        )
    }
}
