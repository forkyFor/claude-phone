package io.github.forkyfor.oldphone

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import io.github.forkyfor.oldphone.engine.EngineInfo

/**
 * First-run guide. One step per screen, one main button per step, one line of explanation.
 * Nothing here needs technical knowledge; everything can be changed later in the owner menu.
 */
class SetupActivity : BaseActivity() {
    private enum class Step { WELCOME, LANGUAGE, WIFI, ENGINE, MIC, CAMERA, PIN, MODE, SIGN_IN }

    private lateinit var prefs: Prefs
    private var step = Step.WELCOME
    private var waitingForHome = false
    private var note: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        step = Step.values()[savedInstanceState?.getInt("step") ?: prefs.sp.getInt("setup_step", 0)]
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("step", step.ordinal)
    }

    override fun onResume() {
        super.onResume()
        if (waitingForHome) {
            waitingForHome = false
            go(Step.SIGN_IN)
        } else {
            render() // e.g. coming back from Wi-Fi settings or the Play Store
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (step.ordinal > 0) go(Step.values()[step.ordinal - 1], back = true)
    }

    private fun go(next: Step, back: Boolean = false) {
        var s = next
        // Before Android 6 permissions are granted when the app is installed.
        while (Build.VERSION.SDK_INT < 23 && (s == Step.MIC || s == Step.CAMERA)) {
            s = Step.values()[s.ordinal + if (back) -1 else 1]
        }
        step = s
        note = null
        prefs.sp.edit().putInt("setup_step", s.ordinal).apply()
        render()
    }

    private fun next() = go(Step.values()[step.ordinal + 1])

    private fun render() {
        val col = Ui.column(this)
        col.addView(Ui.body(this, getString(R.string.setup_step, step.ordinal + 1, Step.values().size), muted = true))
        when (step) {
            Step.WELCOME -> {
                col.addView(Ui.title(this, getString(R.string.setup_welcome_title)))
                col.addView(Ui.body(this, getString(R.string.setup_welcome_body)))
                col.addView(Ui.body(this, getString(R.string.disclaimer), muted = true))
                col.addView(Ui.primaryButton(this, getString(R.string.btn_start)) { next() })
            }
            Step.LANGUAGE -> {
                col.addView(Ui.title(this, getString(R.string.setup_language_title)))
                col.addView(Ui.body(this, getString(R.string.setup_language_body)))
                val pick = { code: String ->
                    prefs.language = code
                    prefs.sp.edit().putInt("setup_step", Step.WIFI.ordinal).apply()
                    step = Step.WIFI
                    recreate()
                }
                col.addView(Ui.primaryButton(this, "English") { pick("en") })
                col.addView(Ui.primaryButton(this, "Italiano") { pick("it") })
                col.addView(Ui.secondaryButton(this, getString(R.string.language_system)) { pick("system") })
            }
            Step.WIFI -> {
                val online = isOnline()
                col.addView(Ui.title(this, getString(R.string.setup_wifi_title)))
                col.addView(Ui.body(this, getString(if (online) R.string.setup_wifi_ok else R.string.setup_wifi_body)))
                if (online) {
                    col.addView(Ui.primaryButton(this, getString(R.string.btn_continue)) { next() })
                } else {
                    col.addView(Ui.primaryButton(this, getString(R.string.owner_wifi)) {
                        try { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) } catch (e: Exception) { }
                    })
                    col.addView(Ui.secondaryButton(this, getString(R.string.btn_skip)) { next() })
                }
            }
            Step.ENGINE -> {
                val info = EngineInfo.webView(this)
                val ok = info.major >= EngineInfo.MIN_WEBVIEW_MAJOR
                col.addView(Ui.title(this, getString(R.string.setup_engine_title)))
                if (ok) {
                    col.addView(Ui.body(this, getString(R.string.setup_engine_ok)))
                    col.addView(Ui.primaryButton(this, getString(R.string.btn_continue)) { next() })
                } else {
                    col.addView(Ui.body(this, getString(if (info.present) R.string.err_old_cause else R.string.err_noengine_cause)))
                    col.addView(Ui.body(this, getString(R.string.err_old_fix)))
                    if (BuildConfig.HAS_GECKO) col.addView(Ui.body(this, getString(R.string.err_try_other_engine)))
                    else if (Build.VERSION.SDK_INT >= 26) col.addView(Ui.body(this, getString(R.string.err_try_gecko_build)))
                    col.addView(Ui.primaryButton(this, getString(R.string.btn_update_webview)) {
                        val pkg = info.packageName ?: "com.google.android.webview"
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")))
                        } catch (e: Exception) {
                            note = getString(R.string.no_store)
                            render()
                        }
                    })
                    col.addView(Ui.secondaryButton(this, getString(R.string.btn_continue_anyway)) { next() })
                }
                col.addView(Ui.body(this, "$info · Android ${Build.VERSION.RELEASE}", muted = true))
            }
            Step.MIC -> permissionStep(col, Manifest.permission.RECORD_AUDIO, R.string.setup_mic_title, R.string.setup_mic_body, R.string.btn_allow_mic)
            Step.CAMERA -> permissionStep(col, Manifest.permission.CAMERA, R.string.setup_camera_title, R.string.setup_camera_body, R.string.btn_allow_camera)
            Step.PIN -> {
                col.addView(Ui.title(this, getString(R.string.pin_create_title)))
                col.addView(Ui.body(this, getString(R.string.pin_create_body)))
                val first = pinField(R.string.pin_new_hint)
                val second = pinField(R.string.pin_repeat_hint)
                col.addView(first)
                col.addView(second)
                col.addView(Ui.primaryButton(this, getString(R.string.btn_save)) {
                    val pin = first.text.toString()
                    when {
                        !Pin.isValidFormat(pin) -> first.error = getString(R.string.pin_format)
                        pin != second.text.toString() -> second.error = getString(R.string.pin_mismatch)
                        else -> {
                            Pin.set(this, pin)
                            next()
                        }
                    }
                })
                if (Pin.isSet(this)) col.addView(Ui.secondaryButton(this, getString(R.string.pin_keep)) { next() })
            }
            Step.MODE -> {
                col.addView(Ui.title(this, getString(R.string.setup_mode_title)))
                col.addView(Ui.body(this, getString(R.string.setup_mode_body)))
                col.addView(Ui.primaryButton(this, getString(R.string.btn_mode_home)) { requestHome() })
                col.addView(Ui.secondaryButton(this, getString(R.string.btn_mode_app)) { next() })
                col.addView(Ui.body(this, getString(R.string.setup_mode_more), muted = true))
            }
            Step.SIGN_IN -> {
                col.addView(Ui.title(this, getString(R.string.setup_signin_title)))
                col.addView(Ui.body(this, getString(R.string.setup_signin_body)))
                col.addView(Ui.body(this, getString(R.string.setup_signin_tips), muted = true))
                col.addView(Ui.primaryButton(this, getString(R.string.btn_open_claude)) {
                    prefs.setupDone = true
                    prefs.sp.edit().remove("setup_step").apply()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                    noTransition()
                })
                col.addView(Ui.body(this, getString(R.string.disclaimer), muted = true))
            }
        }
        note?.let { col.addView(Ui.body(this, it, muted = true)) }
        setContentView(Ui.scroll(this, col))
    }

    private fun pinField(hint: Int) = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        setHint(hint)
        textSize = 22f
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(this@SetupActivity, 12) }
    }

    private fun permissionStep(col: LinearLayout, permission: String, title: Int, body: Int, button: Int) {
        col.addView(Ui.title(this, getString(title)))
        col.addView(Ui.body(this, getString(body)))
        val granted = Build.VERSION.SDK_INT < 23 || checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            col.addView(Ui.body(this, getString(R.string.setup_perm_ok)))
            col.addView(Ui.primaryButton(this, getString(R.string.btn_continue)) { next() })
        } else {
            col.addView(Ui.primaryButton(this, getString(button)) {
                if (Build.VERSION.SDK_INT >= 23) requestPermissions(arrayOf(permission), 1)
            })
            col.addView(Ui.secondaryButton(this, getString(R.string.btn_not_now)) { next() })
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            next()
        } else {
            note = getString(R.string.setup_perm_denied)
            render()
        }
    }

    private fun requestHome() {
        waitingForHome = true
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val rm = getSystemService(RoleManager::class.java)
                if (rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                    @Suppress("DEPRECATION")
                    startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), 2)
                    return
                }
                if (rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                    waitingForHome = false
                    next()
                    return
                }
            }
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (e: Exception) {
            waitingForHome = false
            note = getString(R.string.setup_home_manual)
            render()
        }
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        @Suppress("DEPRECATION")
        return cm.activeNetworkInfo?.isConnected == true
    }
}
