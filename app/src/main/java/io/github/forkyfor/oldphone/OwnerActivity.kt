package io.github.forkyfor.oldphone

import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Toast
import io.github.forkyfor.oldphone.engine.EngineFactory
import io.github.forkyfor.oldphone.engine.EngineInfo
import io.github.forkyfor.oldphone.engine.UrlPolicy

/**
 * The owner menu: everything a dedicated phone needs without ever showing Android's own
 * Home screen. Reached with the hidden gesture (hold the microphone 3 s) plus the owner PIN.
 */
class OwnerActivity : BaseActivity() {
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun backToClaude(action: String? = null) {
        if (action != null) MainActivity.pendingOwnerAction = action
        finish()
        noTransition()
    }

    private fun open(action: String) {
        try {
            startActivity(Intent(action))
        } catch (e: Exception) {
            toast(getString(R.string.owner_not_available))
        }
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    private fun confirm(message: Int, then: () -> Unit) {
        AlertDialog.Builder(this).setMessage(message)
            .setPositiveButton(android.R.string.ok) { _, _ -> then() }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun choose(title: Int, labels: List<String>, values: List<String>, current: String, then: (String) -> Unit) {
        AlertDialog.Builder(this).setTitle(title)
            .setSingleChoiceItems(labels.toTypedArray(), values.indexOf(current)) { d, which ->
                d.dismiss()
                then(values[which])
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun render() {
        val c = this
        val col = Ui.column(c)
        col.addView(Ui.title(c, getString(R.string.owner_title)))
        col.addView(Ui.body(c, getString(R.string.disclaimer), muted = true))
        col.addView(Ui.primaryButton(c, getString(R.string.owner_back)) { backToClaude() })

        // ---- connection and device
        col.addView(Ui.section(c, getString(R.string.owner_sec_device)))
        col.addView(Ui.secondaryButton(c, getString(R.string.owner_wifi)) { open(Settings.ACTION_WIFI_SETTINGS) })
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        col.addView(Ui.body(c, getString(R.string.owner_volume)))
        col.addView(slider(audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC), audio.getStreamVolume(AudioManager.STREAM_MUSIC), getString(R.string.owner_volume)) {
            try { audio.setStreamVolume(AudioManager.STREAM_MUSIC, it, 0) } catch (e: Exception) { }
        })
        col.addView(Ui.body(c, getString(R.string.owner_brightness)))
        col.addView(slider(95, (if (prefs.brightness < 0) 60 else prefs.brightness) - 5, getString(R.string.owner_brightness)) {
            prefs.brightness = it + 5
            window.attributes = window.attributes.apply { screenBrightness = (it + 5) / 100f }
        })
        col.addView(Ui.secondaryButton(c, getString(R.string.owner_brightness_auto)) {
            prefs.brightness = -1
            window.attributes = window.attributes.apply { screenBrightness = -1f }
            toast(getString(R.string.owner_done))
        })
        col.addView(Ui.secondaryButton(c, getString(R.string.owner_android_settings)) { open(Settings.ACTION_SETTINGS) })

        // ---- the site
        col.addView(Ui.section(c, getString(R.string.owner_sec_claude)))
        col.addView(Ui.secondaryButton(c, getString(R.string.owner_reload)) { backToClaude(MainActivity.ACTION_RELOAD) })
        col.addView(Ui.secondaryButton(c, getString(R.string.owner_paste_link)) { pasteLink() })
        col.addView(Ui.body(c, getString(R.string.owner_paste_link_help), muted = true))
        col.addView(Ui.secondaryButton(c, getString(R.string.owner_open_browser)) { backToClaude(MainActivity.ACTION_BROWSER) })
        col.addView(Ui.secondaryButton(c, getString(R.string.owner_sign_out)) {
            confirm(R.string.owner_sign_out_confirm) { backToClaude(MainActivity.ACTION_SIGN_OUT) }
        })
        col.addView(Ui.secondaryButton(c, getString(R.string.owner_clear)) {
            confirm(R.string.owner_clear_confirm) { backToClaude(MainActivity.ACTION_CLEAR_ALL) }
        })
        col.addView(Ui.secondaryButton(c, getString(R.string.owner_extra_hosts)) { editExtraHosts() })
        col.addView(Ui.body(c, getString(R.string.owner_extra_hosts_help), muted = true))

        // ---- engine
        col.addView(Ui.section(c, getString(R.string.owner_sec_engine)))
        col.addView(Ui.body(c, engineDescription ?: EngineInfo.webView(c).toString(), muted = true))
        if (EngineFactory.available.size > 1) {
            col.addView(Ui.secondaryButton(c, getString(R.string.owner_engine_choose)) {
                val labels = EngineFactory.available.map {
                    if (it == "gecko") "GeckoView ${EngineFactory.geckoVersion() ?: ""}" else EngineInfo.webView(c).toString()
                }
                choose(R.string.owner_engine_choose, labels, EngineFactory.available, prefs.engine) {
                    prefs.engine = it
                    toast(getString(R.string.owner_engine_note))
                    render()
                }
            })
        } else {
            col.addView(Ui.body(c, getString(R.string.owner_engine_lite), muted = true))
        }
        col.addView(Ui.secondaryButton(c, getString(R.string.btn_update_webview)) {
            val pkg = EngineInfo.webView(c).packageName ?: "com.google.android.webview"
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")))
            } catch (e: Exception) {
                toast(getString(R.string.no_store))
            }
        })

        // ---- voice
        col.addView(Ui.section(c, getString(R.string.owner_sec_voice)))
        col.addView(Ui.switchRow(c, getString(R.string.owner_dictation), prefs.dictation) { prefs.dictation = it })
        col.addView(Ui.body(c, getString(R.string.owner_dictation_help), muted = true))
        col.addView(Ui.switchRow(c, getString(R.string.owner_read), prefs.readAloud) { prefs.readAloud = it })
        col.addView(Ui.body(c, getString(R.string.owner_read_help), muted = true))
        col.addView(Ui.switchRow(c, getString(R.string.owner_phone_commands), prefs.phoneCommands) { prefs.phoneCommands = it })
        col.addView(Ui.body(c, getString(R.string.owner_phone_commands_help), muted = true))
        col.addView(Ui.switchRow(c, getString(R.string.owner_call_countdown), prefs.callCountdown) { prefs.callCountdown = it })
        col.addView(Ui.body(c, getString(R.string.owner_call_countdown_help), muted = true))
        col.addView(Ui.secondaryButton(c, getString(R.string.owner_messaging)) {
            choose(
                R.string.owner_messaging,
                listOf(getString(R.string.messaging_auto), "WhatsApp", "SMS"),
                listOf("auto", "whatsapp", "sms"), prefs.messagingApp
            ) { prefs.messagingApp = it }
        })
        col.addView(Ui.secondaryButton(c, getString(R.string.phone_help_title)) {
            android.app.AlertDialog.Builder(this).setTitle(R.string.phone_help_title).setMessage(R.string.phone_help)
                .setPositiveButton(android.R.string.ok, null).show()
        })
        col.addView(Ui.secondaryButton(c, getString(R.string.owner_assistant)) { open(Settings.ACTION_VOICE_INPUT_SETTINGS) })
        col.addView(Ui.body(c, getString(R.string.owner_assistant_help), muted = true))

        // ---- display
        col.addView(Ui.section(c, getString(R.string.owner_sec_display)))
        col.addView(Ui.switchRow(c, getString(R.string.owner_keep_on), prefs.keepScreenOn) { prefs.keepScreenOn = it })
        col.addView(Ui.switchRow(c, getString(R.string.owner_rest), prefs.restScreen) { prefs.restScreen = it })
        col.addView(Ui.switchRow(c, getString(R.string.owner_reduce_motion), prefs.reduceMotion) { prefs.reduceMotion = it })
        col.addView(Ui.secondaryButton(c, getString(R.string.owner_language)) {
            choose(R.string.owner_language, listOf(getString(R.string.language_system), "English", "Italiano"), listOf("system", "en", "it"), prefs.language) {
                prefs.language = it
                recreate()
            }
        })
        col.addView(Ui.secondaryButton(c, getString(R.string.owner_theme)) {
            choose(
                R.string.owner_theme,
                listOf(getString(R.string.theme_system), getString(R.string.theme_dark), getString(R.string.theme_light)),
                listOf("system", "dark", "light"), prefs.theme
            ) {
                prefs.theme = it
                recreate()
            }
        })

        // ---- home and lock
        if (Build.VERSION.SDK_INT >= 29) {
            col.addView(Ui.section(c, getString(R.string.meeting_title)))
            col.addView(Ui.switchRow(c, getString(R.string.owner_meetings), prefs.meetingsEnabled) { prefs.meetingsEnabled = it })
            val nb = NotebookLm.find(c)
            col.addView(Ui.body(c, getString(if (nb != null) R.string.meeting_setup_notebooklm_ok else R.string.meeting_setup_need_notebooklm), muted = true))
            if (nb == null) {
                col.addView(Ui.secondaryButton(c, getString(R.string.meeting_install_notebooklm)) {
                    try { startActivity(NotebookLm.storePage()) } catch (e: Exception) { toast(getString(R.string.no_store)) }
                })
            }
            val title = prefs.meetingsProjectTitle.ifBlank { getString(R.string.meeting_project_default) }
            col.addView(Ui.secondaryButton(c, getString(R.string.meeting_link_project, title)) {
                textDialog(R.string.meeting_setup_project, title, R.string.meeting_project_default) { t ->
                    prefs.meetingsProjectTitle = t.ifBlank { getString(R.string.meeting_project_default) }
                    prefs.meetingsProjectUrl = ""
                    prefs.meetingsLinkPending = true
                    prefs.meetingsEnabled = true
                    MeetingFlow.copy(c, "project", prefs.meetingsProjectTitle)
                    backToClaude(MainActivity.ACTION_LOAD + UrlPolicy.PROJECTS_URL)
                }
            })
            col.addView(Ui.body(c, prefs.meetingsProjectUrl.ifEmpty { getString(R.string.meeting_project_not_linked) }, muted = true))
        }

        col.addView(Ui.section(c, getString(R.string.owner_sec_lock)))
        col.addView(Ui.secondaryButton(c, getString(R.string.owner_home)) { requestHome() })
        col.addView(Ui.switchRow(c, getString(R.string.owner_show_apps), prefs.showApps) { prefs.showApps = it })
        col.addView(Ui.body(c, getString(R.string.owner_show_apps_help), muted = true))
        val owner = Kiosk.isDeviceOwner(c)
        val locked = Kiosk.isLocked(c)
        if (!owner) {
            if (locked) {
                col.addView(Ui.secondaryButton(c, getString(R.string.owner_unpin)) { backToClaude(MainActivity.ACTION_UNLOCK) })
            } else {
                col.addView(Ui.secondaryButton(c, getString(R.string.owner_pin_screen)) { backToClaude(MainActivity.ACTION_PIN_SCREEN) })
            }
            col.addView(Ui.body(c, getString(R.string.owner_pin_screen_help), muted = true))
            col.addView(Ui.body(c, getString(R.string.owner_kiosk_not_owner), muted = true))
        } else {
            col.addView(Ui.switchRow(c, getString(R.string.owner_kiosk), prefs.kiosk) {
                prefs.kiosk = it
                if (!it) MainActivity.pendingOwnerAction = MainActivity.ACTION_UNLOCK
            })
            col.addView(Ui.body(c, getString(R.string.owner_kiosk_help), muted = true))
            col.addView(Ui.secondaryButton(c, getString(R.string.owner_kiosk_remove)) {
                confirm(R.string.owner_kiosk_remove_confirm) {
                    prefs.kiosk = false
                    backToClaude(MainActivity.ACTION_REMOVE_OWNER)
                }
            })
        }

        // ---- app
        col.addView(Ui.section(c, getString(R.string.owner_sec_app)))
        col.addView(Ui.secondaryButton(c, getString(R.string.owner_updates)) {
            if (Kiosk.isLocked(c)) {
                toast(getString(R.string.owner_updates_locked, BuildConfig.REPO_URL))
            } else {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.REPO_URL + "/releases")))
                } catch (e: Exception) {
                    toast(getString(R.string.no_browser))
                }
            }
        })
        col.addView(Ui.body(c, getString(R.string.owner_updates_help), muted = true))
        col.addView(Ui.secondaryButton(c, getString(R.string.owner_change_pin)) {
            PinPrompt.create(this) { toast(getString(R.string.owner_done)) }
        })
        col.addView(
            Ui.body(
                c,
                getString(
                    R.string.owner_about, BuildConfig.VERSION_NAME + (if (BuildConfig.DEBUG) " (debug)" else ""),
                    BuildConfig.REPO_URL
                ), muted = true
            )
        )
        col.addView(Ui.primaryButton(c, getString(R.string.owner_back)) { backToClaude() })
        setContentView(Ui.scroll(c, col))
    }

    private fun slider(max: Int, value: Int, label: String, onChange: (Int) -> Unit) = SeekBar(this).apply {
        this.max = max
        progress = value.coerceIn(0, max)
        contentDescription = label
        minimumHeight = Ui.dp(this@OwnerActivity, 48)
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) onChange(p)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    private fun textDialog(title: Int, initial: String, hint: Int, then: (String) -> Unit) {
        val input = EditText(this).apply {
            setText(initial)
            setHint(hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        val box = LinearLayout(this).apply {
            val p = Ui.dp(this@OwnerActivity, 24)
            setPadding(p, p / 2, p, 0)
            addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        AlertDialog.Builder(this).setTitle(title).setView(box)
            .setPositiveButton(android.R.string.ok) { _, _ -> then(input.text.toString().trim()) }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    /** Sign-in by email: the owner pastes the link from the message; only official links are accepted. */
    private fun pasteLink() {
        val clip = (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
        val fromClipboard = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()?.trim() ?: ""
        val initial = fromClipboard.takeIf {
            val u = Uri.parse(it)
            UrlPolicy.isAcceptablePastedLink(u.scheme, u.host)
        } ?: ""
        textDialog(R.string.owner_paste_link, initial, R.string.owner_paste_link_hint) { text ->
            val u = Uri.parse(text)
            if (UrlPolicy.isAcceptablePastedLink(u.scheme, u.host)) backToClaude(MainActivity.ACTION_LOAD + text)
            else toast(getString(R.string.owner_paste_link_bad))
        }
    }

    private fun editExtraHosts() {
        textDialog(R.string.owner_extra_hosts, prefs.extraHosts, R.string.owner_extra_hosts_hint) { text ->
            prefs.extraHosts = text.split(',', ' ', '\n').map { it.trim().lowercase() }
                .filter { it.matches(Regex("[a-z0-9.-]+\\.[a-z]{2,}")) }.joinToString(",")
            toast(getString(R.string.owner_done))
        }
    }

    private fun requestHome() {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val rm = getSystemService(RoleManager::class.java)
                if (rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                    @Suppress("DEPRECATION")
                    startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), 1)
                    return
                }
            }
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (e: Exception) {
            toast(getString(R.string.setup_home_manual))
        }
    }

    companion object {
        // Filled by MainActivity just before opening this screen (display only).
        var engineDescription: String? = null
    }
}
