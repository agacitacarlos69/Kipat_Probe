package com.kipat.probe.nfc

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the "armed write" lifecycle for one Activity.
 *
 * Unlike the original probe, foreground dispatch is NOT enabled globally in onResume: it is only
 * enabled while the user has explicitly armed a write from the Touch screen. Otherwise taps go
 * through normal NDEF dispatch to the Reveal.
 */
class NfcWriteController(
    private val activity: Activity,
    private val scope: CoroutineScope,
) {
    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)
    private val _state = MutableStateFlow<TagWriteState>(TagWriteState.Idle)
    val state: StateFlow<TagWriteState> = _state.asStateFlow()

    /** Last terminal result (success or failure), kept for the diagnostics panel. */
    private val _lastResult = MutableStateFlow<TagWriteState?>(null)
    val lastResult: StateFlow<TagWriteState?> = _lastResult.asStateFlow()

    private var resumed = false
    private var dispatchEnabled = false
    private var lastTagId: String? = null
    private var lastTagAt = 0L

    fun availability(): NfcAvailability = availability(activity)

    fun arm(uri: String) {
        when (availability()) {
            NfcAvailability.UNSUPPORTED -> fail(WriteError.NO_NFC)
            NfcAvailability.DISABLED -> fail(WriteError.NFC_DISABLED)
            NfcAvailability.ENABLED -> {
                _state.value = TagWriteState.Armed(uri)
                updateDispatch()
            }
        }
    }

    fun cancel() {
        if (_state.value is TagWriteState.Armed) _state.value = TagWriteState.Idle
        updateDispatch()
    }

    fun onResume() {
        resumed = true
        updateDispatch()
    }

    fun onPause() {
        resumed = false
        updateDispatch()
    }

    /** @return true if the intent was a tag delivered for an armed write and was consumed. */
    fun handleIntent(intent: Intent): Boolean {
        val armed = _state.value as? TagWriteState.Armed ?: return false
        if (intent.action !in TAG_ACTIONS) return false
        val tag = intent.tagExtra() ?: return false

        val tagId = tag.id?.joinToString("") { "%02x".format(it) }
        val now = SystemClock.elapsedRealtime()
        if (tagId != null && tagId == lastTagId && now - lastTagAt < DUPLICATE_WINDOW_MS) return true
        lastTagId = tagId
        lastTagAt = now

        _state.value = TagWriteState.Writing(armed.uri)
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                NfcTagWriter.write(tag, NfcTagWriter.buildMessage(armed.uri, activity.packageName))
            }
            when (result) {
                // Like the probe: a successful write disarms; any error stays armed so the user can retry.
                is NfcTagWriter.Result.Written -> {
                    val success = TagWriteState.Success(armed.uri, result.bytes, result.formatted, System.currentTimeMillis())
                    _state.value = success
                    _lastResult.value = success
                }
                is NfcTagWriter.Result.Error -> {
                    _lastResult.value = TagWriteState.Failed(result.error, result.detail, System.currentTimeMillis())
                    _state.value = armed
                }
            }
            updateDispatch()
        }
        return true
    }

    private fun fail(error: WriteError) {
        val failed = TagWriteState.Failed(error, null, System.currentTimeMillis())
        _state.value = failed
        _lastResult.value = failed
    }

    private fun updateDispatch() {
        val nfc = adapter ?: return
        val wantDispatch = resumed && (_state.value is TagWriteState.Armed || _state.value is TagWriteState.Writing)
        if (wantDispatch == dispatchEnabled) return
        try {
            if (wantDispatch) {
                val pending = PendingIntent.getActivity(
                    activity,
                    0,
                    Intent(activity, activity.javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    // NFC fills in tag extras, so the PendingIntent must be mutable.
                    PendingIntent.FLAG_MUTABLE,
                )
                // null filters + null tech lists = receive every tag while armed.
                nfc.enableForegroundDispatch(activity, pending, null, null)
            } else {
                nfc.disableForegroundDispatch(activity)
            }
            dispatchEnabled = wantDispatch
        } catch (e: IllegalStateException) {
            // Activity not in the foreground; will be retried on the next resume.
            dispatchEnabled = false
        }
    }

    companion object {
        private const val DUPLICATE_WINDOW_MS = 2_000L
        @Suppress("DEPRECATION") // ACTION_TAG_DISCOVERED is still delivered by foreground dispatch.
        private val TAG_ACTIONS = setOf(
            NfcAdapter.ACTION_TAG_DISCOVERED,
            NfcAdapter.ACTION_NDEF_DISCOVERED,
            NfcAdapter.ACTION_TECH_DISCOVERED,
        )

        fun availability(context: Context): NfcAvailability {
            val nfc = NfcAdapter.getDefaultAdapter(context) ?: return NfcAvailability.UNSUPPORTED
            return if (nfc.isEnabled) NfcAvailability.ENABLED else NfcAvailability.DISABLED
        }
    }
}

/** Android 13+ typed parcelable access with a fallback for API 26–32. */
@Suppress("DEPRECATION")
fun Intent.tagExtra(): Tag? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
    } else {
        getParcelableExtra(NfcAdapter.EXTRA_TAG)
    }
