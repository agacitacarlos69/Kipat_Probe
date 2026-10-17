package com.kipat.probe.pilot

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** What build this is: the same values on two phones prove they run the same APK. */
data class BuildMetadata(
    val versionName: String,
    val versionCode: Int,
    val buildNumber: Int,
    val gitSha: String,
    val buildTime: String,
    val buildType: String,
) {
    /** "Kipat Pilot 0.2.1-pilot.14" for pilot builds, "Kipat 0.2.0-shell" otherwise. */
    val title: String get() = if (buildType == "pilot") "Kipat Pilot $versionName" else "Kipat $versionName"
    val shortSha: String get() = gitSha.take(7)
}

data class DeviceSnapshot(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val api: Int,
    val hasNfc: Boolean,
    val nfcEnabled: Boolean,
    val hasHce: Boolean,
    /** KipatTapAvailability name: READY, NFC_DISABLED, DISABLED_BY_USER, ... */
    val kipatTap: String,
)

/** The pilot user's own profile, as shown on screen; exports use [pseudonym] only. */
data class ProfileSnapshot(val profileId: String, val name: String, val reveal: String, val accent: String) {
    val pseudonym: String get() = PilotReport.pseudonym(profileId)
}

/** The other person in the last exchange. The name stays on screen; exports carry [idHash] only. */
data class PeerSeen(val name: String, val idHash: String, val reveal: String, val accent: String, val atWallMs: Long)

data class CardTestRecord(
    val action: String,          // read | write
    val technologies: String,
    val ndefType: String?,
    val capacityBytes: Int?,
    val writable: Boolean?,
    val messageBytes: Int?,
    val result: String,          // e.g. VERIFIED, DETECTED, TOO_LARGE, READ_ONLY, FAILED
    val atWallMs: Long,
)

/** The human checklist: things only a person watching the phones can confirm. */
enum class PilotCheck(val label: String) {
    CORRECT_PERSON("Correct other person displayed"),
    CORRECT_REVEAL("Correct Reveal"),
    CORRECT_ACCENT("Correct accent"),
    BOTH_RECEIVED("Both phones received each other"),
    NO_EMPTY_TAG("No generic Empty tag screen"),
    OVER_PREVIOUS_APP("Reveal appeared over previous app"),
    DISMISS_RETURNED("Dismiss returned to previous app"),
    NO_DUPLICATE("No duplicate Reveal"),
}

data class PilotState(
    val build: BuildMetadata,
    val device: DeviceSnapshot,
    val profile: ProfileSnapshot?,
    val last: TapAttempt?,
    val attempts: List<TapAttempt>,
    val events: List<PilotEvent>,
    val peer: PeerSeen?,
    val connectionStored: Boolean?,
    val card: CardTestRecord?,
    val checklist: Map<PilotCheck, Boolean>,
    /** When this APK was first installed / last updated, and when results were last cleared. */
    val installedAtMs: Long? = null,
    val updatedAtMs: Long? = null,
    val clearedAtMs: Long? = null,
    /** Whether Android routes the NDEF and Kipat AIDs to Kipat on this phone. */
    val routing: HceRouting? = null,
)

/**
 * Builds the shareable diagnostic (ZIP and copyable text) from [PilotState]. Content-free by
 * construction: no names, profile text, session ids or device identifiers; profile ids are hashed.
 */
object PilotReport {
    const val FORMAT = 1

    fun pseudonym(id: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest(("kipat-pilot:" + id).toByteArray())
        return "p-" + d.take(4).joinToString("") { "%02x".format(it) }
    }

    fun fileName(device: DeviceSnapshot, wallMs: Long): String {
        val brand = device.manufacturer.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "phone" }.take(20)
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date(wallMs))
        return "kipat-pilot-$brand-$stamp.zip"
    }

    private fun iso(ms: Long): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(ms))

    fun attemptJson(a: TapAttempt): JSONObject = JSONObject()
        .put("id", a.id)
        .put("role", a.role.name.lowercase())
        .put("exchange", a.correlation ?: JSONObject.NULL)
        .put("startedAt", iso(a.startedWallMs))
        .put("failureCode", a.failureCode ?: JSONObject.NULL)
        .put("durationMs", a.durationMs ?: JSONObject.NULL)
        .put("mutualExchange", a.mutual)
        .put("stages", JSONObject().apply {
            PilotStage.entries.forEach { s ->
                put(s.code.lowercase(), JSONObject().put("result", a.result(s).name).put("elapsedMs", a.elapsed(s) ?: JSONObject.NULL))
            }
        })

    fun summaryJson(state: PilotState, wallMs: Long): JSONObject = JSONObject()
        .put("format", FORMAT)
        .put("generatedAt", iso(wallMs))
        .put("build", JSONObject()
            .put("title", state.build.title)
            .put("versionName", state.build.versionName)
            .put("versionCode", state.build.versionCode)
            .put("buildNumber", state.build.buildNumber)
            .put("gitSha", state.build.gitSha)
            .put("buildTime", state.build.buildTime)
            .put("buildType", state.build.buildType)
            .put("installedAt", state.installedAtMs?.let(::iso) ?: JSONObject.NULL)
            .put("updatedAt", state.updatedAtMs?.let(::iso) ?: JSONObject.NULL)
            .put("resultsClearedAt", state.clearedAtMs?.let(::iso) ?: JSONObject.NULL))
        .put("device", JSONObject()
            .put("manufacturer", state.device.manufacturer)
            .put("model", state.device.model)
            .put("android", state.device.androidVersion)
            .put("api", state.device.api))
        .put("nfc", JSONObject()
            .put("hardware", state.device.hasNfc)
            .put("enabled", state.device.nfcEnabled)
            .put("hce", state.device.hasHce)
            .put("kipatTap", state.device.kipatTap))
        .put("hceRouting", state.routing?.let { r ->
            JSONObject().put("service", r.service).put("serviceEnabled", r.serviceEnabled.name)
                .put("ndefV2Aid", HceRoutingCheck.NDEF_V2).put("ndefV2Routed", r.ndefV2.name)
                .put("ndefV1Aid", HceRoutingCheck.NDEF_V1).put("ndefV1Routed", r.ndefV1.name)
                .put("kipatAid", HceRoutingCheck.KIPAT).put("kipatAidRouted", r.kipatAid.name)
                .put("category", r.category).put("unlockRequired", r.unlockRequired)
                .put("backgroundRestricted", r.backgroundRestricted.name).put("batteryUnrestricted", r.batteryUnrestricted.name)
                .put("standbyBucket", r.standbyBucket)
        } ?: JSONObject.NULL)
        .put("profile", state.profile?.let { JSONObject().put("pseudonym", it.pseudonym).put("reveal", it.reveal).put("accent", it.accent) } ?: JSONObject.NULL)
        .put("lastAttempt", state.last?.let(::attemptJson) ?: JSONObject.NULL)
        .put("attempts", JSONArray().apply { state.attempts.forEach { put(attemptJson(it)) } })
        .put("lastPeer", state.peer?.let { JSONObject().put("peer", it.idHash).put("reveal", it.reveal).put("accent", it.accent).put("at", iso(it.atWallMs)) } ?: JSONObject.NULL)
        .put("connection", when (state.connectionStored) { true -> "stored_by_user"; false -> "not_connected"; null -> "n/a" })
        .put("card", state.card?.let { c ->
            JSONObject().put("action", c.action).put("technologies", c.technologies).put("ndef", c.ndefType ?: JSONObject.NULL)
                .put("capacityBytes", c.capacityBytes ?: JSONObject.NULL).put("writable", c.writable ?: JSONObject.NULL)
                .put("messageBytes", c.messageBytes ?: JSONObject.NULL).put("result", c.result).put("at", iso(c.atWallMs))
        } ?: JSONObject.NULL)
        .put("humanChecklist", JSONObject().apply { PilotCheck.entries.forEach { put(it.name.lowercase(), state.checklist[it] == true) } })
        .put("note", "Stage results are measured by the app. Human-observed behaviour (return to the previous app, correct person) is only in humanChecklist.")

    fun eventsJson(events: List<PilotEvent>): JSONArray = JSONArray().apply {
        events.forEach { e ->
            put(JSONObject().put("at", iso(e.wallMs)).put("event", e.name).put("result", e.result)
                .put("elapsedMs", e.elapsedMs ?: JSONObject.NULL).put("detail", e.detail))
        }
    }

    /** Compact form for app-private persistence (raw epoch ms). */
    internal fun eventsJsonRaw(events: List<PilotEvent>): JSONArray = JSONArray().apply {
        events.forEach { e -> put(JSONObject().put("wall", e.wallMs).put("name", e.name).put("result", e.result).put("elapsed", e.elapsedMs ?: -1).put("detail", e.detail)) }
    }

    fun deviceText(state: PilotState): String = buildString {
        appendLine("${state.build.title}  build ${state.build.buildNumber}  commit ${state.build.gitSha}")
        appendLine("Built ${state.build.buildTime}  type ${state.build.buildType}")
        appendLine("${state.device.manufacturer} ${state.device.model}  Android ${state.device.androidVersion} (API ${state.device.api})")
        appendLine("NFC hardware ${yn(state.device.hasNfc)}  enabled ${yn(state.device.nfcEnabled)}  HCE ${yn(state.device.hasHce)}  Kipat Tap ${state.device.kipatTap}")
    }

    const val README = """Kipat physical pilot diagnostic

summary.json  build, device, NFC capabilities, the last Kipat Tap attempts stage by stage
              (PASS / FAIL / NOT_REACHED / UNKNOWN) with measured elapsed times, the last card test
              and the tester's checklist.
events.json   the bounded NFC event buffer (at most 200 events), content-free.
device.txt    the same build and device facts as plain text.

Privacy: no names, profile text, emails, tokens, session ids, IMEI, Android ID, location or contacts.
Profile ids are replaced by a one-way pseudonym (p-xxxxxxxx).
Results the app cannot observe (returning to the previous app, the right person) are only what the
tester ticked in the checklist.
"""

    fun zip(state: PilotState, wallMs: Long): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            fun entry(name: String, text: String) {
                z.putNextEntry(ZipEntry(name).apply { time = wallMs })
                z.write(text.toByteArray(Charsets.UTF_8))
                z.closeEntry()
            }
            entry("summary.json", summaryJson(state, wallMs).toString(2))
            entry("events.json", eventsJson(state.events).toString(2))
            entry("device.txt", deviceText(state))
            entry("README.txt", README)
        }
        return out.toByteArray()
    }

    /** Short text for pasting into a chat. */
    fun copyText(state: PilotState): String = buildString {
        appendLine("KIPAT PILOT")
        appendLine()
        appendLine("Build: ${state.build.shortSha} (${state.build.versionName}, build ${state.build.buildNumber}, ${state.build.buildType})")
        appendLine("Device: ${state.device.manufacturer} ${state.device.model}, Android ${state.device.androidVersion} (API ${state.device.api})")
        appendLine("NFC: ${if (!state.device.hasNfc) "no hardware" else if (state.device.nfcEnabled) "enabled" else "disabled"}, HCE ${yn(state.device.hasHce)}")
        appendLine("Kipat Tap: ${state.device.kipatTap}")
        state.routing?.let { r ->
            appendLine("HCE routing: NDEF v2 ${r.ndefV2}, NDEF v1 ${r.ndefV1}, Kipat AID ${r.kipatAid}, service enabled ${r.serviceEnabled}")
            appendLine("Background: restricted ${r.backgroundRestricted}, battery unrestricted ${r.batteryUnrestricted}, bucket ${r.standbyBucket}")
        }
        appendLine()
        val a = state.last
        if (a == null) {
            appendLine("Last Kipat Tap: none recorded")
        } else {
            appendLine("Last Kipat Tap (${a.role.label.lowercase()}):")
            a.correlation?.let { appendLine("Exchange: $it") }
            PilotStage.entries.forEach { s ->
                val t = a.elapsed(s)?.let { " ${it}ms" }.orEmpty()
                appendLine("${s.label} ${a.result(s).name.replace('_', ' ')}$t")
            }
            appendLine()
            appendLine("Mutual exchange on this phone: ${if (a.mutual) "YES" else "NO"}")
            appendLine("Failure: ${a.failureCode ?: "none"}")
            a.durationMs?.let { appendLine("Duration: ${it}ms") }
        }
        state.card?.let { c ->
            appendLine()
            appendLine("Card test: ${c.action} ${c.result}, ${c.ndefType ?: "no NDEF"}, ${c.capacityBytes?.let { "$it bytes" } ?: "capacity unknown"}, writable ${c.writable?.let(::yn) ?: "unknown"}")
        }
        val ticked = PilotCheck.entries.count { state.checklist[it] == true }
        appendLine()
        appendLine("Checklist: $ticked/${PilotCheck.entries.size} ticked by tester")
    }

    private fun yn(b: Boolean) = if (b) "yes" else "no"
}
