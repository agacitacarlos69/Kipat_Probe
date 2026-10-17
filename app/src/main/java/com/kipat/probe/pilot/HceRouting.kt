package com.kipat.probe.pilot

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.os.Build
import android.os.PowerManager
import com.kipat.probe.nfc.peer.KipatApduProtocol
import com.kipat.probe.nfc.peer.KipatHceService
import com.kipat.probe.nfc.peer.KipatType4Tag

/** YES / NO from the platform, UNKNOWN when the platform cannot say. Never inferred from our own XML. */
enum class Tri { YES, NO, UNKNOWN }

/**
 * Whether Android will hand the other phone's SELECTs to Kipat on this phone. The AID answers come
 * from CardEmulation.isDefaultServiceForAid, which uses the same AID cache the NFC service resolves
 * a SELECT with (RegisteredAidCache.resolveAid): NO means not registered, another service claims
 * the AID (Android then shows a chooser and drops the tap), or Kipat's "other" service is disabled.
 * It does not see the controller's routing table or an OEM blocking the cold start, hence the
 * background fields.
 */
data class HceRouting(
    val service: String,
    val serviceEnabled: Tri,
    val ndefV2: Tri,
    val ndefV1: Tri,
    val kipatAid: Tri,
    /** From Kipat's own service declaration (checked against the XML by a unit test). */
    val category: String = "other (declared)",
    val unlockRequired: Boolean = true,
    /** The user restricted Kipat's background activity (ActivityManager.isBackgroundRestricted). */
    val backgroundRestricted: Tri,
    /** Exempt from battery optimisation (PowerManager.isIgnoringBatteryOptimizations). */
    val batteryUnrestricted: Tri,
    /** App standby bucket (ACTIVE, WORKING_SET, FREQUENT, RARE, RESTRICTED). */
    val standbyBucket: String,
)

object HceRoutingCheck {
    val NDEF_V2 = KipatType4Tag.NDEF_AID_V2_HEX
    val NDEF_V1 = KipatType4Tag.NDEF_AID_V1_HEX
    val KIPAT = KipatApduProtocol.AID_HEX

    fun check(context: Context): HceRouting {
        val component = ComponentName(context, KipatHceService::class.java)
        val pm = context.packageManager
        val enabled = runCatching {
            when (pm.getComponentEnabledSetting(component)) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> Tri.YES
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> if (pm.getServiceInfo(component, 0).enabled) Tri.YES else Tri.NO
                else -> Tri.NO
            }
        }.getOrDefault(Tri.UNKNOWN)
        val ce = runCatching {
            val adapter = NfcAdapter.getDefaultAdapter(context) ?: return@runCatching null
            if (!pm.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)) null else CardEmulation.getInstance(adapter)
        }.getOrNull()
        fun routed(aid: String): Tri = if (ce == null) Tri.UNKNOWN else
            runCatching { if (ce.isDefaultServiceForAid(component, aid)) Tri.YES else Tri.NO }.getOrDefault(Tri.UNKNOWN)

        val restricted = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) Tri.UNKNOWN else runCatching {
            if (context.getSystemService(ActivityManager::class.java).isBackgroundRestricted) Tri.YES else Tri.NO
        }.getOrDefault(Tri.UNKNOWN)
        val unrestrictedBattery = runCatching {
            if (context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)) Tri.YES else Tri.NO
        }.getOrDefault(Tri.UNKNOWN)
        val bucket = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) "UNKNOWN" else
            runCatching { bucketName(context.getSystemService(UsageStatsManager::class.java).appStandbyBucket) }.getOrDefault("UNKNOWN")

        return HceRouting(
            service = KipatHceService::class.java.simpleName,
            serviceEnabled = enabled,
            ndefV2 = routed(NDEF_V2),
            ndefV1 = routed(NDEF_V1),
            kipatAid = routed(KIPAT),
            backgroundRestricted = restricted,
            batteryUnrestricted = unrestrictedBattery,
            standbyBucket = bucket,
        )
    }

    internal fun bucketName(bucket: Int): String = when (bucket) {
        UsageStatsManager.STANDBY_BUCKET_ACTIVE -> "ACTIVE"
        UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> "WORKING_SET"
        UsageStatsManager.STANDBY_BUCKET_FREQUENT -> "FREQUENT"
        UsageStatsManager.STANDBY_BUCKET_RARE -> "RARE"
        45 -> "RESTRICTED" // UsageStatsManager.STANDBY_BUCKET_RESTRICTED (API 30)
        else -> "OTHER_$bucket"
    }

    /** Xiaomi, Redmi and POCO phones gate background starts behind their own Autostart switch. */
    fun isXiaomiFamily(manufacturer: String = Build.MANUFACTURER.orEmpty(), brand: String = Build.BRAND.orEmpty()): Boolean =
        listOf(manufacturer, brand).any { it.lowercase() in setOf("xiaomi", "redmi", "poco") }
}
