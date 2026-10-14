package com.kipat.probe.nfc.peer

import android.app.KeyguardManager
import android.content.Context
import androidx.core.content.edit
import com.kipat.probe.nfc.profile.NfcProfileParse
import com.kipat.probe.nfc.profile.NfcProfilePayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Kipat Tap settings and the public profile snapshot the HCE service serves, readable in a fresh
 * process with no UI. Only the public profile (Identity.publishedFields via KipatPeerProfile) is
 * ever written here: "what other people may see when I tap".
 *
 * Snapshot file: filesDir/kipat_tap_profile.json, replaced atomically (temp + rename).
 * Settings: SharedPreferences "kipat_tap" (enabled, consent shown).
 */
object KipatTapStore {
    private const val PREFS = "kipat_tap"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_PROMPTED = "prompted"
    private const val KEY_UPDATED_AT = "updated_at"
    private const val FILE = "kipat_tap_profile.json"

    @Volatile private var cached: TapProfile? = null
    @Volatile private var loaded = false

    private val _enabled = MutableStateFlow<Boolean?>(null)
    /** Observed by the UI; null until first read. */
    val enabled: StateFlow<Boolean?> = _enabled.asStateFlow()

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false).also { _enabled.value = it }

    /** The one-time consent ("Enable Kipat Tap") or the Profile switch. */
    fun setEnabled(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_ENABLED, value).putBoolean(KEY_PROMPTED, true) }
        _enabled.value = value
    }

    fun wasPrompted(context: Context): Boolean = prefs(context).getBoolean(KEY_PROMPTED, false)

    fun markPrompted(context: Context) = prefs(context).edit { putBoolean(KEY_PROMPTED, true) }

    /** Writes the public profile when it changed. Cheap no-op otherwise. Call off the main thread. */
    @Synchronized
    fun publish(context: Context, profile: NfcProfilePayload) {
        val tap = TapProfile.of(profile) ?: return
        if (snapshot(context)?.bytes?.contentEquals(tap.bytes) == true) return
        val dir = context.filesDir
        val tmp = File(dir, "$FILE.tmp")
        tmp.writeBytes(tap.bytes)
        if (!tmp.renameTo(File(dir, FILE))) {
            tmp.delete()
            return
        }
        prefs(context).edit { putLong(KEY_UPDATED_AT, System.currentTimeMillis()) }
        cached = tap
        loaded = true
    }

    /** The served profile: parsed with the same validator as any received profile, so it can't drift. */
    fun snapshot(context: Context): TapProfile? {
        if (loaded) return cached
        synchronized(this) {
            if (!loaded) {
                cached = runCatching {
                    val f = File(context.filesDir, FILE)
                    if (!f.exists()) null else (KipatPeerProfile.parse(f.readBytes()) as? NfcProfileParse.Valid)?.let { TapProfile.of(it.profile) }
                }.getOrNull()
                loaded = true
            }
        }
        return cached
    }

    fun updatedAt(context: Context): Long = prefs(context).getLong(KEY_UPDATED_AT, 0)

    fun isDeviceLocked(context: Context): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isDeviceLocked == true

    /** What may be shared right now by the HCE service; null = nothing. */
    fun eligibleProfile(context: Context): TapProfile? =
        if (isEnabled(context) && !isDeviceLocked(context)) snapshot(context) else null

    fun availability(context: Context): KipatTapAvailability = KipatTapAvailability.of(
        enabled = isEnabled(context),
        hasProfile = snapshot(context) != null,
        caps = KipatPeerSessions.capabilities(context),
        locked = isDeviceLocked(context),
    )

    /** For tests: forget the in-memory copy so the next read comes from disk. */
    internal fun clearCache() {
        cached = null
        loaded = false
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
