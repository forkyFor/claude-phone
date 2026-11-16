package io.github.forkyfor.oldphone

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            scheduleRestart()
            previous?.uncaughtException(thread, error)
        }
    }

    /**
     * Best effort "come back after a crash". When the app is the Home app, Android restarts it
     * by itself; this covers older versions. A 30 s guard prevents crash loops.
     */
    private fun scheduleRestart() {
        try {
            val prefs = Prefs(this)
            val now = System.currentTimeMillis()
            if (!prefs.setupDone || now - prefs.sp.getLong("last_crash", 0L) < 30_000L) return
            prefs.sp.edit().putLong("last_crash", now).commit()
            val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val flags = PendingIntent.FLAG_ONE_SHOT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
            val pi = PendingIntent.getActivity(this, 1, intent, flags)
            (getSystemService(Context.ALARM_SERVICE) as AlarmManager).set(AlarmManager.RTC, now + 1500L, pi)
        } catch (e: Throwable) {
            // Never make a crash worse.
        }
    }
}

/**
 * Opens the app after the phone boots. From Android 10 apps may not start themselves in the
 * background, so there the reliable way is to make this app the Home app (or device owner).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (Build.VERSION.SDK_INT >= 29 || !Prefs(context).setupDone) return
        try {
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) { }
    }
}
