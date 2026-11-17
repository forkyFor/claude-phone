package io.github.forkyfor.oldphone

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.Settings
import android.widget.TextView
import java.util.Date

/**
 * Carries out [PhoneCommand]s with the phone's own apps and services.
 *
 * Safety rules, on purpose:
 * - A call is never placed silently. There is always a dialog with the name and the number;
 *   only a contact the owner named by voice may be called after a visible 5 s countdown
 *   (owner menu option). Numbers that come from the page always wait for "Call" or "yes".
 * - Messages are only prepared: the owner presses Send in the messaging app.
 * - Contacts are read on the phone to find a number. They are never typed into the page.
 */
class PhoneActions(private val a: Activity, private val host: Host) {
    interface Host {
        fun permissions(names: List<String>, result: (Boolean) -> Unit)
        fun banner(text: String, action: (() -> Unit)? = null)
        fun say(text: String)
        fun lastReply(result: (String?) -> Unit)
        fun typeIntoPage(text: String)
        /** Runs [then] directly, or after the owner PIN when other apps are meant to be out of reach. */
        fun ownerGate(then: () -> Unit)
        fun dedicated(): Boolean
    }

    private class Contact(val name: String, val number: String, val label: String)

    private val prefs = Prefs(a)
    private val main = Handler(Looper.getMainLooper())
    private var dialog: AlertDialog? = null
    private var pending: (() -> Unit)? = null
    private var tick: Runnable? = null

    private fun s(id: Int, vararg args: Any): String = a.getString(id, *args)

    // ------------------------------------------------------------------ entry points

    fun run(cmd: PhoneCommand, original: String) {
        when (cmd) {
            is PhoneCommand.Call -> call(cmd.who, original)
            PhoneCommand.CallFromReply -> callFromReply()
            is PhoneCommand.Message -> message(cmd.who, cmd.body, original)
            PhoneCommand.VolumeUp -> volume(AudioManager.ADJUST_RAISE)
            PhoneCommand.VolumeDown -> volume(AudioManager.ADJUST_LOWER)
            PhoneCommand.Mute -> volume(if (Build.VERSION.SDK_INT >= 23) AudioManager.ADJUST_MUTE else 0)
            is PhoneCommand.Torch -> torch(cmd.on)
            PhoneCommand.Time -> tell(s(R.string.phone_say_time, android.text.format.DateFormat.getTimeFormat(a).format(Date())))
            PhoneCommand.Battery -> battery()
            PhoneCommand.Settings -> host.ownerGate { start(Intent(Settings.ACTION_SETTINGS)) }
            PhoneCommand.Wifi -> host.ownerGate {
                start(Intent(if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS))
            }
            is PhoneCommand.OpenApp -> openApp(cmd.name)
            PhoneCommand.Help -> help()
        }
    }

    /** A tel: link touched in the page. Always asks, because the number comes from a web page. */
    fun callFromLink(uri: Uri) {
        val number = PhoneCommands.dialable(Uri.decode(uri.schemeSpecificPart ?: "")) ?: return
        confirmCall(null, number, countdown = false, fromPage = true)
    }

    /** A "yes" or "no" while a call is waiting for confirmation. Returns true if it was used. */
    fun handleAnswer(text: String): Boolean {
        val go = pending ?: return false
        return when (PhoneCommands.answer(text)) {
            Answer.YES -> {
                dismiss()
                go()
                true
            }
            Answer.NO -> {
                dismiss()
                host.banner(s(R.string.phone_cancelled))
                true
            }
            null -> false
        }
    }

    fun dismiss() {
        tick?.let { main.removeCallbacks(it) }
        tick = null
        pending = null
        dialog?.let { if (it.isShowing) it.dismiss() }
        dialog = null
    }

    // ------------------------------------------------------------------ calls and messages

    private fun canPhone(): Boolean {
        if (a.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) return true
        host.banner(s(R.string.phone_no_telephony))
        return false
    }

    private fun call(who: String, original: String) {
        if (!canPhone()) return
        PhoneCommands.dialable(who)?.let {
            confirmCall(null, it, countdown = prefs.callCountdown, fromPage = false)
            return
        }
        findContact(who, original) { c, picked ->
            confirmCall(c.name, c.number, countdown = prefs.callCountdown && !picked, fromPage = false, alreadyChosen = picked)
        }
    }

    private fun callFromReply() {
        if (!canPhone()) return
        host.lastReply { text ->
            val numbers = PhoneCommands.findPhoneNumbers(text ?: "")
            when {
                numbers.isEmpty() -> host.banner(s(R.string.phone_no_number_in_reply))
                numbers.size == 1 -> confirmCall(null, numbers[0], countdown = false, fromPage = true)
                else -> AlertDialog.Builder(a).setTitle(R.string.phone_pick_title)
                    .setItems(numbers.toTypedArray()) { _, i -> confirmCall(null, numbers[i], countdown = false, fromPage = true) }
                    .setNegativeButton(android.R.string.cancel, null).show()
            }
        }
    }

    private fun message(who: String, body: String?, original: String) {
        if (!canPhone()) return
        val send = { number: String, name: String ->
            val intent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null))
            if (!body.isNullOrBlank()) intent.putExtra("sms_body", body)
            if (start(intent, R.string.phone_no_sms_app)) host.say(s(R.string.phone_message_ready, name))
        }
        val number = PhoneCommands.dialable(who)
        if (number != null) send(number, number) else findContact(who, original) { c, _ -> send(c.number, c.name) }
    }

    private fun confirmCall(name: String?, number: String, countdown: Boolean, fromPage: Boolean, alreadyChosen: Boolean = false) {
        dismiss()
        val who = name ?: number
        val message = if (fromPage) s(R.string.phone_confirm_page, number) else s(R.string.phone_confirm, who, number)
        val d = AlertDialog.Builder(a)
            .setTitle(R.string.phone_call_title)
            .setMessage(message)
            .setPositiveButton(R.string.phone_call_button) { _, _ -> placeCall(number) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> host.banner(s(R.string.phone_cancelled)) }
            .setOnDismissListener {
                tick?.let { main.removeCallbacks(it) }
                tick = null
                pending = null
                dialog = null
            }
            .create()
        dialog = d
        d.show()
        pending = { placeCall(number) }
        d.findViewById<TextView>(android.R.id.message)?.textSize = 22f
        if (countdown) {
            var left = COUNTDOWN_SECONDS
            val button = d.getButton(AlertDialog.BUTTON_POSITIVE)
            val r = object : Runnable {
                override fun run() {
                    if (dialog !== d) return
                    if (left <= 0) {
                        d.dismiss()
                        placeCall(number)
                        return
                    }
                    button.text = s(R.string.phone_call_button_countdown, left)
                    left--
                    main.postDelayed(this, 1000L)
                }
            }
            tick = r
            r.run()
            host.say(s(R.string.phone_say_calling, who))
        } else if (!alreadyChosen) {
            host.say(s(R.string.phone_say_confirm, who))
        }
    }

    private fun placeCall(number: String) {
        host.permissions(listOf(Manifest.permission.CALL_PHONE)) { granted ->
            val uri = Uri.fromParts("tel", number, null)
            val direct = Intent(if (granted) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri)
            try {
                a.startActivity(direct)
            } catch (e: Exception) {
                // No permission after all, or an emergency number: let the dialer handle it.
                start(Intent(Intent.ACTION_DIAL, uri), R.string.phone_no_dialer)
            }
        }
    }

    // ------------------------------------------------------------------ contacts

    private fun findContact(who: String, original: String, then: (Contact, Boolean) -> Unit) {
        host.permissions(listOf(Manifest.permission.READ_CONTACTS)) { granted ->
            if (!granted) {
                host.banner(s(R.string.phone_contacts_denied))
                return@permissions
            }
            val app = a.applicationContext
            Thread {
                val ranked = PhoneCommands.rank(who, readContacts(app)) { it.name }
                main.post { if (!a.isFinishing) choose(who, original, ranked, then) }
            }.start()
        }
    }

    private fun choose(who: String, original: String, ranked: List<Pair<Contact, Int>>, then: (Contact, Boolean) -> Unit) {
        if (ranked.isEmpty()) {
            // Probably not a call after all: offer to give the sentence to Claude.
            host.banner(s(R.string.phone_contact_not_found, who)) { host.typeIntoPage(original) }
            return
        }
        val top = ranked[0].second
        val best = ranked.filter { it.second == top }
        if (top >= PhoneCommands.SURE_MATCH && best.size == 1) {
            then(best[0].first, false)
            return
        }
        val options = ranked.take(6).map { it.first }
        AlertDialog.Builder(a).setTitle(R.string.phone_pick_title)
            .setItems(options.map { "${it.name}\n${it.label} · ${it.number}" }.toTypedArray()) { _, i -> then(options[i], true) }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun readContacts(ctx: Context): List<Contact> {
        val out = ArrayList<Contact>()
        val seen = HashSet<String>()
        try {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.TYPE,
                    ContactsContract.CommonDataKinds.Phone.LABEL,
                ), null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(0) ?: continue
                    val number = c.getString(1) ?: continue
                    val key = name + "|" + number.filter { it.isDigit() }.takeLast(9)
                    if (!seen.add(key)) continue
                    val label = ContactsContract.CommonDataKinds.Phone.getTypeLabel(ctx.resources, c.getInt(2), c.getString(3)).toString()
                    out.add(Contact(name, number, label))
                }
            }
        } catch (e: Exception) {
            // Unreadable contacts provider: behave as if there were no match.
        }
        return out
    }

    // ------------------------------------------------------------------ device

    private fun volume(direction: Int) {
        val am = a.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        try {
            if (direction == 0) am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
            else am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        } catch (e: Exception) {
            host.banner(s(R.string.owner_not_available))
        }
    }

    private fun torch(on: Boolean) {
        if (Build.VERSION.SDK_INT < 23) {
            host.banner(s(R.string.phone_torch_unsupported))
            return
        }
        try {
            val cm = a.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (id == null) {
                host.banner(s(R.string.phone_torch_unsupported))
                return
            }
            cm.setTorchMode(id, on)
            host.banner(s(if (on) R.string.phone_torch_on else R.string.phone_torch_off))
        } catch (e: Exception) {
            host.banner(s(R.string.phone_torch_busy))
        }
    }

    private fun battery() {
        val i = a.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        if (level < 0 || scale <= 0) {
            host.banner(s(R.string.owner_not_available))
            return
        }
        val percent = level * 100 / scale
        val charging = (i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        tell(s(if (charging) R.string.phone_say_battery_charging else R.string.phone_say_battery, percent))
    }

    private fun openApp(name: String) {
        if (host.dedicated()) {
            host.banner(s(R.string.phone_dedicated_blocked))
            return
        }
        val pm = a.packageManager
        val own = a.packageName
        Thread {
            val apps = try {
                pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                    .filter { it.activityInfo.packageName != own }
                    .map { it.loadLabel(pm).toString() to it.activityInfo }
            } catch (e: Exception) {
                emptyList()
            }
            val ranked = PhoneCommands.rank(name, apps) { it.first }.filter { it.second >= 40 }
            main.post {
                if (a.isFinishing) return@post
                val best = ranked.firstOrNull()
                if (best == null) {
                    host.banner(s(R.string.phone_app_not_found, name))
                    return@post
                }
                val info = best.first.second
                host.ownerGate {
                    start(
                        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                            .setClassName(info.packageName, info.name)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    )
                }
            }
        }.start()
    }

    private fun help() {
        val d = AlertDialog.Builder(a).setTitle(R.string.phone_help_title).setMessage(R.string.phone_help)
            .setPositiveButton(android.R.string.ok, null).show()
        d.findViewById<TextView>(android.R.id.message)?.textSize = 18f
    }

    // ------------------------------------------------------------------ helpers

    private fun tell(text: String) {
        host.banner(text)
        host.say(text)
    }

    private fun start(intent: Intent, failure: Int = R.string.owner_not_available): Boolean =
        try {
            a.startActivity(intent)
            true
        } catch (e: Exception) {
            host.banner(s(failure))
            false
        }

    companion object {
        const val COUNTDOWN_SECONDS = 5
    }
}
