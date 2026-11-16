package io.github.forkyfor.oldphone

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.Settings

/** Empty receiver: it only exists so `dpm set-device-owner` has a component to name. */
class AdminReceiver : DeviceAdminReceiver()

/**
 * Dedicated-device mode. Everything here needs the app to be DEVICE OWNER, which can only be
 * granted from a computer with adb on a freshly reset phone (see docs). No root involved.
 */
object Kiosk {
    private fun dpm(ctx: Context) = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private fun admin(ctx: Context) = ComponentName(ctx, AdminReceiver::class.java)

    fun isDeviceOwner(ctx: Context): Boolean = dpm(ctx).isDeviceOwnerApp(ctx.packageName)

    fun isLocked(ctx: Context): Boolean {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return if (Build.VERSION.SDK_INT >= 23) {
            am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
        } else {
            @Suppress("DEPRECATION")
            am.isInLockTaskMode
        }
    }

    /** Allows lock task for this app (and Settings, reachable only from the PIN-protected owner menu). */
    fun prepare(ctx: Context) {
        if (!isDeviceOwner(ctx)) return
        val d = dpm(ctx)
        // Besides this app: Settings (reachable only from the PIN-protected owner menu), and
        // the system camera and file picker, so attaching photos and files keeps working.
        val helpers = listOf(
            Intent(Settings.ACTION_SETTINGS),
            Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE),
            Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),
        ).mapNotNull { it.resolveActivity(ctx.packageManager)?.packageName }.filter { it != "android" }
        d.setLockTaskPackages(admin(ctx), (listOf(ctx.packageName) + helpers).distinct().toTypedArray())
        if (Build.VERSION.SDK_INT >= 28) {
            // Keep only the power menu: no status bar, notifications, Home or Recents.
            d.setLockTaskFeatures(admin(ctx), DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS)
        }
        val home = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        d.addPersistentPreferredActivity(admin(ctx), home, ComponentName(ctx, MainActivity::class.java))
    }

    fun enter(activity: Activity) {
        prepare(activity)
        try { activity.startLockTask() } catch (e: Exception) { }
    }

    /** Leaves lock task. For a device owner it also releases the Home and lock task policies. */
    fun exit(activity: Activity) {
        try { activity.stopLockTask() } catch (e: Exception) { }
        if (!isDeviceOwner(activity)) return
        val d = dpm(activity)
        try {
            d.clearPackagePersistentPreferredActivities(admin(activity), activity.packageName)
            d.setLockTaskPackages(admin(activity), arrayOf())
        } catch (e: Exception) { }
    }

    /** Gives the device back: after this the app is an ordinary app again and can be uninstalled. */
    fun removeDeviceOwner(activity: Activity): Boolean {
        exit(activity)
        return try {
            @Suppress("DEPRECATION")
            dpm(activity).clearDeviceOwnerApp(activity.packageName)
            true
        } catch (e: Exception) {
            false
        }
    }
}
