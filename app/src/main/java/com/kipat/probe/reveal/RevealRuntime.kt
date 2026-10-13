package com.kipat.probe.reveal

import android.animation.ValueAnimator
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import com.kipat.probe.TapExperienceActivity

/** Starts the Reveal with an explicit payload (Preview, Replay, Profile preview). */
object RevealLauncher {
    const val EXTRA_LAUNCH_UPTIME = "kipat.reveal.launchUptime"

    fun intent(context: Context, payload: RevealPayload): Intent =
        Intent(context, TapExperienceActivity::class.java).apply {
            RevealPayloadCodec.encode(payload).forEach { (k, v) -> putExtra(k, v) }
            putExtra(TapExperienceActivity.EXTRA_NONCE, TapExperienceActivity.PROCESS_NONCE)
            putExtra(EXTRA_LAUNCH_UPTIME, SystemClock.uptimeMillis())
        }

    fun launch(context: Context, payload: RevealPayload) {
        context.startActivity(intent(context, payload))
    }
}

/** Picks a rendering budget from system settings and device class. */
object RevealQualityPolicy {
    fun choose(context: Context, userPrefersReducedMotion: Boolean = false): RevealQuality {
        // Developer option / accessibility "remove animations" sets the animator scale to 0.
        if (userPrefersReducedMotion || !ValueAnimator.areAnimatorsEnabled()) return RevealQuality.REDUCED_MOTION
        val am = context.getSystemService(ActivityManager::class.java)
        if (am?.isLowRamDevice == true) return RevealQuality.LOW
        if (Runtime.getRuntime().availableProcessors() <= 4) return RevealQuality.LOW
        return RevealQuality.HIGH
    }
}

/**
 * Tasteful, sparse haptics via View feedback constants (no VIBRATE permission needed).
 * Fails silently where unsupported or when the user disabled touch feedback.
 */
object KipatHaptics {
    fun revealStart(view: View) = perform(view, if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE else HapticFeedbackConstants.CLOCK_TICK)

    fun verified(view: View) = perform(view, if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK)

    fun connect(view: View) = perform(view, if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)

    private fun perform(view: View, constant: Int) {
        try {
            view.performHapticFeedback(constant)
        } catch (_: Throwable) {
            // Some OEM builds throw on unknown constants; haptics are never essential.
        }
    }
}

/**
 * Lightweight timing markers for the Reveal launch path. Logged under the `KipatReveal` tag and
 * emitted as system trace sections so they appear in Perfetto. These are measurement hooks only;
 * no numbers are claimed from cloud builds.
 */
class RevealTrace(private val launchUptime: Long?) {
    val createdAt: Long = SystemClock.uptimeMillis()
    var contentSetAt: Long? = null; private set
    var firstFrameAt: Long? = null; private set
    var firstVisualAt: Long? = null; private set
    var completeAt: Long? = null; private set

    init {
        log("activity_create", launchUptime?.let { "launch→create=${createdAt - it}ms" })
    }

    fun contentSet() { contentSetAt = mark("content_set") }
    fun firstFrame() { if (firstFrameAt == null) firstFrameAt = mark("first_frame") }
    fun firstVisual() { if (firstVisualAt == null) firstVisualAt = mark("first_reveal_visual") }
    fun complete() { if (completeAt == null) completeAt = mark("reveal_complete") }

    val launchToCreate: Long? get() = launchUptime?.let { createdAt - it }
    fun sinceCreate(t: Long?): Long? = t?.let { it - createdAt }

    private fun mark(name: String): Long {
        val now = SystemClock.uptimeMillis()
        log(name, "+${now - createdAt}ms since create")
        return now
    }

    private fun log(name: String, detail: String?) {
        android.os.Trace.beginSection("KipatReveal:$name")
        android.os.Trace.endSection()
        Log.i(TAG, if (detail == null) name else "$name $detail")
    }

    companion object {
        const val TAG = "KipatReveal"
    }
}
