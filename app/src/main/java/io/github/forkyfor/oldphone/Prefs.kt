package io.github.forkyfor.oldphone

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build

/** All local settings. Nothing here ever leaves the device. */
class Prefs(ctx: Context) {
    val sp: SharedPreferences =
        ctx.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val lowEnd: Boolean = run {
        val am = ctx.applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.isLowRamDevice || Build.VERSION.SDK_INT < 26
    }

    private fun bool(key: String, def: Boolean) = sp.getBoolean(key, def)
    private fun put(key: String, v: Boolean) = sp.edit().putBoolean(key, v).apply()
    private fun str(key: String, def: String) = sp.getString(key, def) ?: def
    private fun put(key: String, v: String) = sp.edit().putString(key, v).apply()

    var setupDone: Boolean
        get() = bool("setup_done", false)
        set(v) = put("setup_done", v)

    /** "system", "en" or "it". */
    var language: String
        get() = str("language", "system")
        set(v) = put("language", v)

    /** "system", "dark" or "light". Before Android 10 there is no system dark mode, so default to dark. */
    var theme: String
        get() = str("theme", if (Build.VERSION.SDK_INT >= 29) "system" else "dark")
        set(v) = put("theme", v)

    /** "webview" or "gecko" (the latter only exists in the gecko flavor). */
    var engine: String
        get() = str("engine", "webview")
        set(v) = put("engine", v)

    /** Native dictation helper that types into the page's message box (fragile, see docs). */
    var dictation: Boolean
        get() = bool("dictation", true)
        set(v) = put("dictation", v)

    /** Optional read-aloud of the last reply with the system text-to-speech. */
    var readAloud: Boolean
        get() = bool("read_aloud", false)
        set(v) = put("read_aloud", v)

    /** "Apps" button on the bar: installed apps and phone settings, without the PIN. */
    var showApps: Boolean
        get() = bool("show_apps", true)
        set(v) = put("show_apps", v)

    /** Local voice commands for the phone: calls, messages, volume, flashlight, time, battery, apps. */
    var phoneCommands: Boolean
        get() = bool("phone_commands", true)
        set(v) = put("phone_commands", v)

    /** A contact named by voice is called after a visible 5 s countdown, without touching. */
    var callCountdown: Boolean
        get() = bool("call_countdown", true)
        set(v) = put("call_countdown", v)

    var reduceMotion: Boolean
        get() = bool("reduce_motion", lowEnd)
        set(v) = put("reduce_motion", v)

    var keepScreenOn: Boolean
        get() = bool("keep_on", true)
        set(v) = put("keep_on", v)

    var restScreen: Boolean
        get() = bool("rest_screen", true)
        set(v) = put("rest_screen", v)

    /** Window brightness 5..100, or -1 to follow the system. */
    var brightness: Int
        get() = sp.getInt("brightness", -1)
        set(v) = sp.edit().putInt("brightness", v).apply()

    /** Dedicated-device (lock task) mode wanted by the owner. Only effective for a device owner. */
    var kiosk: Boolean
        get() = bool("kiosk", false)
        set(v) = put("kiosk", v)

    /** Extra sign-in hosts (company SSO), comma separated. */
    var extraHosts: String
        get() = str("extra_hosts", "")
        set(v) = put("extra_hosts", v)

    fun extraHostSet(): Set<String> =
        extraHosts.split(',', ' ', '\n').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
}
