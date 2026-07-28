package com.telnyx.webrtc.sdk.utility

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import timber.log.Timber

/**
 * There is no public Android API that lets an app grant itself "run
 * reliably in background" / "autostart" permission — every phone maker
 * (Xiaomi, Transsion/Infinix, Oppo, Vivo, Huawei, Samsung, Asus) built
 * their own separate settings screen for this, each with a different
 * package/activity name, and none of it is standardized.
 *
 * This helper tries to jump the user directly to their specific phone's
 * screen for that setting, based on Build.MANUFACTURER, so it's a single
 * tap instead of a manual hunt through nested menus. If the manufacturer
 * isn't recognised, or their screen has moved in a firmware update, it
 * falls back to the app's own system settings page.
 */
object OemBackgroundHelper {

    private data class OemTarget(val pkg: String, val cls: String)

    // Best-effort list, compiled from publicly known component names used
    // by each manufacturer's "autostart" / "protected apps" / "background
    // app management" screen. Not guaranteed to exist on every firmware
    // version — wrapped in try/catch with a safe fallback below.
    private val knownTargets: List<OemTarget> = listOf(
        // Transsion family (Infinix / Tecno / itel) — XOS / HiOS
        OemTarget("com.transsion.phonemanager", "com.transsion.phonemanager.ui.main.MainActivity"),
        OemTarget("com.transsion.batterymanager", "com.transsion.batterymanager.ui.BatteryMainActivity"),
        // Xiaomi / MIUI
        OemTarget("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        // Oppo / ColorOS
        OemTarget("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        OemTarget("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
        // Vivo / FuntouchOS
        OemTarget("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
        // Huawei / EMUI
        OemTarget("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        // Samsung
        OemTarget("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
        // Asus
        OemTarget("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"),
        // Letv
        OemTarget("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity"),
    )

    /**
     * Tries each known manufacturer screen in turn. Returns true if one
     * actually opened. Callers should fall back to
     * [openAppSettingsFallback] if this returns false.
     */
    fun openManufacturerAutostartSettings(context: Context): Boolean {
        for (target in knownTargets) {
            try {
                val intent = Intent().apply {
                    component = android.content.ComponentName(target.pkg, target.cls)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return true
            } catch (e: ActivityNotFoundException) {
                // Not this manufacturer / not this firmware version — try the next one
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error opening OEM autostart screen for ${target.pkg}")
            }
        }
        return false
    }

    /** Generic fallback: the app's own detail page in system Settings. */
    fun openAppSettingsFallback(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e, "Could not open app settings fallback screen")
        }
    }

    /**
     * Android 14+ requires explicit user approval for apps to use
     * full-screen incoming-call intents (the actual ringing screen).
     * Below Android 14 this is auto-granted, so this only matters on
     * newer OS versions. Returns true if a permission request screen was
     * shown (i.e. it was NOT already granted).
     */
    fun requestFullScreenIntentPermissionIfNeeded(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 34) return false // UPSIDE_DOWN_CAKE
        val notificationManager =
            context.getSystemService(android.app.NotificationManager::class.java)
        val alreadyGranted = try {
            notificationManager?.canUseFullScreenIntent() ?: true
        } catch (e: Exception) {
            true
        }
        if (alreadyGranted) return false

        return try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Timber.e(e, "Could not request full screen intent permission")
            false
        }
    }
}
