package com.kipat.probe.nfc.peer

import android.app.KeyguardManager
import android.content.Context
import androidx.core.content.edit
import com.kipat.probe.data.KipatIdentityStore
import com.kipat.probe.data.PublicKipatProfileSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Kipat Tap settings and access to the public profile the HCE service serves, readable in a fresh
 * process with no UI. The profile itself is never stored here: it is the canonical identity's
 * [PublicKipatProfileSnapshot] (Identity.publishedFields only), persisted by KipatIdentityStore.
 *
 * Settings: SharedPreferences "kipat_tap" (enabled, consent shown).
 */
object KipatTapStore {
    private const val PREFS = "kipat_tap"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_PROMPTED = "prompted"

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

    /**
     * The served profile: the canonical identity's public snapshot (KipatIdentityStore), the same
     * one linked cards and the preview use. Rebuilt per profile revision, so a Reveal or profile
     * change is served on the next tap without any separate publish step.
     */
    fun snapshot(context: Context): PublicKipatProfileSnapshot? = KipatIdentityStore.publicSnapshot(context)

    fun isDeviceLocked(context: Context): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isDeviceLocked == true

    /** Why [eligibleProfile] is null, as a diagnostic code; null when Kipat Tap may share. */
    fun refusal(context: Context): String? = when {
        !isEnabled(context) -> "kipat_tap_disabled"
        isDeviceLocked(context) -> "device_locked"
        snapshot(context) == null -> "no_public_profile"
        else -> null
    }

    /** What may be shared right now by the HCE service; null = nothing. */
    fun eligibleProfile(context: Context): PublicKipatProfileSnapshot? =
        if (isEnabled(context) && !isDeviceLocked(context)) snapshot(context) else null

    fun availability(context: Context): KipatTapAvailability = KipatTapAvailability.of(
        enabled = isEnabled(context),
        hasProfile = snapshot(context) != null,
        caps = KipatPeerSessions.capabilities(context),
        locked = isDeviceLocked(context),
    )

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
