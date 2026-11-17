package io.github.forkyfor.oldphone

import android.Manifest
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import io.github.forkyfor.oldphone.engine.UrlPolicy
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** State shared by the meetings screen, the main screen and the recording service. */
object MeetingFlow {
    fun dir(ctx: Context) = File(ctx.applicationContext.filesDir, "meetings")
    fun store(ctx: Context) = MeetingStore(dir(ctx))

    /** Text copied to the clipboard that looks like NotebookLM's answer for the meeting waiting for one. */
    fun clipboardResult(ctx: Context): String? {
        val prefs = Prefs(ctx)
        if (!prefs.meetingsEnabled || prefs.meetingWaiting.isEmpty()) return null
        val clip = try {
            (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
        } catch (e: Exception) {
            null
        } ?: return null
        val text = clip.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()?.trim() ?: return null
        if (text.length < 20 || text == prefs.meetingPrompt.trim()) return null
        if (text.hashCode().toString() == prefs.meetingConsumed) return null
        return text
    }

    fun copy(ctx: Context, label: String, text: String) {
        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, text))
    }
}

/**
 * The "Meetings" screen: record a meeting held around the phone, hand the recording to
 * NotebookLM, turn its answer into a title, description and summary the owner accepts or edits,
 * delete the audio, and add the note to the owner's "Meetings" project in Claude.
 */
class MeetingsActivity : BaseActivity() {
    private lateinit var prefs: Prefs
    private lateinit var store: MeetingStore
    private val handler = Handler(Looper.getMainLooper())
    private var timer: TextView? = null
    private var dialogOpen = false

    private val tick = object : Runnable {
        override fun run() {
            MeetingRecorderService.finishedId?.let {
                MeetingRecorderService.finishedId = null
                render()
                askSave(it)
            }
            MeetingRecorderService.error?.let {
                MeetingRecorderService.error = null
                render()
                info(getString(if (it == "empty") R.string.meeting_empty else R.string.meeting_failed))
            }
            if (MeetingRecorderService.recording) {
                val s = (SystemClock.elapsedRealtime() - MeetingRecorderService.startedAt) / 1000
                timer?.text = getString(R.string.meeting_recording_now, String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60))
            }
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        store = MeetingFlow.store(this)
        render()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        render()
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Back from NotebookLM with its answer copied: offer to use it.
        if (hasFocus && !dialogOpen) {
            val text = MeetingFlow.clipboardResult(this) ?: return
            val m = store.load(prefs.meetingWaiting) ?: return
            askUseResult(m, text)
        }
    }

    private fun handleIntent(intent: Intent) {
        when (intent.getStringExtra(EXTRA_ACTION)) {
            ACTION_START -> if (!MeetingRecorderService.recording) startRecording()
            ACTION_STOP -> stopRecording()
        }
        if (BuildConfig.DEBUG && intent.getBooleanExtra(MainActivity.EXTRA_PROBE, false)) {
            // Emulator tests only: CI emulators have no audio input and no NotebookLM.
            if (intent.getBooleanExtra(EXTRA_PROBE_FAKE, false)) {
                val m = Meeting(store.newId()).apply {
                    created = System.currentTimeMillis()
                    durationMs = 5000L
                    title = "Probe meeting"
                }
                store.audioFile(m.id).writeText("not really audio")
                store.save(m)
                prefs.meetingWaiting = m.id
                render()
            }
            intent.getStringExtra(EXTRA_PROBE_RESULT)?.let { text ->
                store.load(prefs.meetingWaiting)?.let { editor(it, MeetingText.parse(text)) }
            }
        }
    }

    // ------------------------------------------------------------------ screen

    private fun render() {
        val c = this
        val col = Ui.column(c)
        col.addView(Ui.title(c, getString(R.string.meeting_title)))
        if (MeetingRecorderService.recording) {
            timer = Ui.body(c, "").apply { setTextColor(Ui.color(c, R.color.danger)) }
            col.addView(timer)
            col.addView(Ui.primaryButton(c, getString(R.string.meeting_stop)) { stopRecording() })
        } else {
            timer = null
            col.addView(Ui.primaryButton(c, getString(R.string.meeting_record)) { startRecording() })
            col.addView(Ui.body(c, getString(R.string.meeting_record_help), muted = true))
        }
        val title = prefs.meetingsProjectTitle.ifBlank { getString(R.string.meeting_project_default) }
        if (prefs.meetingsProjectUrl.isNotEmpty()) {
            col.addView(Ui.secondaryButton(c, getString(R.string.meeting_open_project, title)) {
                MainActivity.pendingOwnerAction = MainActivity.ACTION_LOAD + prefs.meetingsProjectUrl
                finish()
            })
        } else {
            col.addView(Ui.secondaryButton(c, getString(R.string.meeting_link_project, title)) { linkProject() })
        }
        val pending = store.list()
        if (pending.isNotEmpty()) {
            col.addView(Ui.section(c, getString(R.string.meeting_pending)))
            for (m in pending) {
                val state = getString(if (m.state == Meeting.READY) R.string.meeting_state_ready else R.string.meeting_state_recorded)
                col.addView(Ui.secondaryButton(c, "${m.title}\n$state · ${MeetingText.duration(m.durationMs)}") { actions(m) })
            }
        }
        col.addView(Ui.body(c, getString(R.string.meeting_privacy), muted = true))
        col.addView(Ui.secondaryButton(c, getString(R.string.owner_back)) {
            finish()
            noTransition()
        })
        setContentView(Ui.scroll(c, col))
    }

    private fun info(text: String) {
        AlertDialog.Builder(this).setMessage(text).setPositiveButton(android.R.string.ok, null).show()
    }

    private fun dialog(b: AlertDialog.Builder): AlertDialog {
        dialogOpen = true
        return b.setOnDismissListener { dialogOpen = false }.show()
    }

    // ------------------------------------------------------------------ recording

    private val permissionCode = 31

    @android.annotation.SuppressLint("UsableSpace")
    private fun startRecording() {
        val needed = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isNotEmpty() && Build.VERSION.SDK_INT >= 23) {
            requestPermissions(needed.toTypedArray(), permissionCode)
            return
        }
        if (store.dir.usableSpace < 100L * 1024 * 1024) {
            info(getString(R.string.meeting_no_space))
            return
        }
        MeetingRecorderService.start(this, store.newId())
        handler.postDelayed({ render() }, 600L)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode != permissionCode) return
        val mic = Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (mic) startRecording() else info(getString(R.string.perm_mic_denied))
    }

    private fun stopRecording() {
        if (MeetingRecorderService.recording) MeetingRecorderService.stop(this)
    }

    private fun askSave(id: String) {
        val m = store.load(id) ?: return
        dialog(
            AlertDialog.Builder(this)
                .setTitle(R.string.meeting_save_title)
                .setMessage(getString(R.string.meeting_save_body, MeetingText.duration(m.durationMs)))
                .setPositiveButton(R.string.meeting_save) { _, _ -> afterSave(m) }
                .setNegativeButton(R.string.meeting_discard) { _, _ -> confirmDelete(m) }
                .setCancelable(false)
        )
    }

    private fun afterSave(m: Meeting) {
        dialog(
            AlertDialog.Builder(this)
                .setMessage(R.string.meeting_process_now)
                .setPositiveButton(R.string.meeting_process) { _, _ -> process(m) }
                .setNegativeButton(R.string.meeting_later, null)
        )
    }

    private fun confirmDelete(m: Meeting) {
        dialog(
            AlertDialog.Builder(this)
                .setMessage(R.string.meeting_delete_confirm)
                .setPositiveButton(R.string.meeting_delete) { _, _ ->
                    deleteExported(m)
                    store.delete(m)
                    if (prefs.meetingWaiting == m.id) prefs.meetingWaiting = ""
                    render()
                }
                .setNegativeButton(android.R.string.cancel) { _, _ -> render() }
        )
    }

    private fun actions(m: Meeting) {
        val labels = ArrayList<String>()
        val acts = ArrayList<() -> Unit>()
        fun add(label: Int, act: () -> Unit) {
            labels.add(getString(label))
            acts.add(act)
        }
        if (m.state == Meeting.RECORDED) {
            add(R.string.meeting_process) { process(m) }
            add(R.string.meeting_paste) { pasteResult(m) }
            if (Build.VERSION.SDK_INT >= 29) add(R.string.meeting_export) { exportAudio(m) }
        } else {
            add(R.string.meeting_add_to_project) { addToProject(m) }
            add(R.string.meeting_edit) { editor(m, MeetingText.Result(m.title, m.description, m.summary)) }
            add(R.string.meeting_done) { store.delete(m); render() }
        }
        add(R.string.meeting_delete) { confirmDelete(m) }
        dialog(AlertDialog.Builder(this).setTitle(m.title).setItems(labels.toTypedArray()) { _, i -> acts[i]() })
    }

    // ------------------------------------------------------------------ NotebookLM

    private fun process(m: Meeting) {
        if (NotebookLm.find(this) == null) {
            dialog(
                AlertDialog.Builder(this).setMessage(R.string.meeting_need_notebooklm)
                    .setPositiveButton(R.string.meeting_install_notebooklm) { _, _ ->
                        try { startActivity(NotebookLm.storePage()) } catch (e: Exception) { info(getString(R.string.no_store)) }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
            )
            return
        }
        val prompt = getString(R.string.meeting_prompt)
        prefs.meetingPrompt = prompt
        prefs.meetingWaiting = m.id
        MeetingFlow.copy(this, "prompt", prompt)
        dialog(
            AlertDialog.Builder(this).setTitle(R.string.meeting_process).setMessage(R.string.meeting_steps)
                .setPositiveButton(R.string.meeting_open_notebooklm) { _, _ ->
                    val uri = FilesProvider.uriFor(this, store.audioFile(m.id))
                    if (!NotebookLm.share(this, uri, m.title)) info(getString(R.string.owner_not_available))
                }
                .setNeutralButton(R.string.meeting_export) { _, _ -> exportAudio(m) }
                .setNegativeButton(android.R.string.cancel, null)
        )
    }

    /** Fallback when NotebookLM does not take audio from the Share menu: a copy in Downloads. */
    private fun exportAudio(m: Meeting) {
        if (Build.VERSION.SDK_INT < 29) return
        try {
            val name = MeetingText.fileName(SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(m.created)), m.title).removeSuffix(".md") + ".m4a"
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "audio/mp4")
            }
            val target = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IllegalStateException()
            contentResolver.openOutputStream(target)?.use { out -> store.audioFile(m.id).inputStream().use { it.copyTo(out) } }
            m.exportedAudio = target.toString()
            store.save(m)
            prefs.meetingWaiting = m.id
            info(getString(R.string.meeting_exported, name))
        } catch (e: Exception) {
            info(getString(R.string.owner_not_available))
        }
    }

    private fun deleteExported(m: Meeting) {
        if (m.exportedAudio.isEmpty()) return
        try { contentResolver.delete(Uri.parse(m.exportedAudio), null, null) } catch (e: Exception) { }
        m.exportedAudio = ""
    }

    private fun askUseResult(m: Meeting, text: String) {
        dialog(
            AlertDialog.Builder(this).setTitle(R.string.meeting_result_title)
                .setMessage(text.take(300) + if (text.length > 300) "…" else "")
                .setPositiveButton(R.string.meeting_use_result) { _, _ ->
                    prefs.meetingConsumed = text.hashCode().toString()
                    editor(m, MeetingText.parse(text))
                }
                .setNegativeButton(android.R.string.cancel) { _, _ -> prefs.meetingConsumed = text.hashCode().toString() }
        )
    }

    private fun pasteResult(m: Meeting) {
        prefs.meetingWaiting = m.id
        val clip = MeetingFlow.clipboardResult(this)
        editor(m, if (clip != null) MeetingText.parse(clip) else MeetingText.Result(m.title, "", ""))
    }

    /** The suggested title, description and summary, which the owner accepts or edits. */
    private fun editor(m: Meeting, r: MeetingText.Result) {
        fun field(label: Int, value: String, lines: Int): Pair<TextView, EditText> {
            val l = Ui.body(this, getString(label), muted = true)
            val e = EditText(this).apply {
                setText(value)
                textSize = 18f
                if (lines > 1) {
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                    minLines = 2
                    maxLines = lines
                } else {
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                }
            }
            return l to e
        }
        val (tl, t) = field(R.string.meeting_field_title, r.title, 1)
        val (dl, d) = field(R.string.meeting_field_description, r.description, 4)
        val (sl, s) = field(R.string.meeting_field_summary, r.summary, 12)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = Ui.dp(this@MeetingsActivity, 20)
            setPadding(p, 0, p, 0)
            listOf(tl, t, dl, d, sl, s).forEach { addView(it) }
        }
        val scroll = ScrollView(this).apply { addView(box, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)) }
        dialog(
            AlertDialog.Builder(this).setTitle(R.string.meeting_suggestion)
                .setView(scroll)
                .setPositiveButton(R.string.meeting_accept) { _, _ ->
                    val result = MeetingText.Result(
                        t.text.toString().trim().ifEmpty { m.title }, d.text.toString().trim(), s.text.toString().trim()
                    )
                    finishResult(m, result)
                }
                .setNegativeButton(android.R.string.cancel, null)
        )
    }

    /** Result accepted: keep the note, delete the recording everywhere on the phone. */
    private fun finishResult(m: Meeting, r: MeetingText.Result) {
        val date = Date(m.created)
        val md = MeetingText.toMarkdown(
            r, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(date), MeetingText.duration(m.durationMs),
            MeetingText.Labels(
                getString(R.string.meeting_md_date), getString(R.string.meeting_md_duration),
                getString(R.string.meeting_field_description), getString(R.string.meeting_field_summary)
            )
        )
        store.noteFile(m)?.delete()
        val base = MeetingText.fileName(SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(date), r.title).removeSuffix(".md")
        var name = "$base.md"
        var n = 2
        while (File(store.dir, name).exists()) name = "$base (${n++}).md"
        File(store.dir, name).writeText(md)
        m.title = r.title
        m.description = r.description
        m.summary = r.summary
        m.noteFile = name
        m.state = Meeting.READY
        store.deleteAudio(m)
        deleteExported(m)
        store.save(m)
        if (prefs.meetingWaiting == m.id) prefs.meetingWaiting = ""
        if (Build.VERSION.SDK_INT >= 28) {
            try { (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).clearPrimaryClip() } catch (e: Exception) { }
        }
        render()
        Toast.makeText(this, R.string.meeting_audio_deleted, Toast.LENGTH_LONG).show()
        dialog(
            AlertDialog.Builder(this)
                .setMessage(getString(R.string.meeting_add_now, prefs.meetingsProjectTitle.ifBlank { getString(R.string.meeting_project_default) }))
                .setPositiveButton(R.string.meeting_add_to_project) { _, _ -> addToProject(m) }
                .setNegativeButton(R.string.meeting_later, null)
        )
    }

    // ------------------------------------------------------------------ Claude project

    private fun addToProject(m: Meeting) {
        if (prefs.meetingsProjectUrl.isEmpty()) {
            linkProject()
            return
        }
        MainActivity.pendingMeetingId = m.id
        finish()
    }

    /** The owner creates (or opens) the project in the page; the main screen recognises it. */
    private fun linkProject() {
        val title = prefs.meetingsProjectTitle.ifBlank { getString(R.string.meeting_project_default) }
        prefs.meetingsProjectTitle = title
        prefs.meetingsLinkPending = true
        MeetingFlow.copy(this, "project", title)
        MainActivity.pendingOwnerAction = MainActivity.ACTION_LOAD + UrlPolicy.PROJECTS_URL
        finish()
    }

    companion object {
        const val EXTRA_ACTION = "meeting_action"
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        const val EXTRA_PROBE_FAKE = "probe_fake_meeting"
        const val EXTRA_PROBE_RESULT = "probe_result"
    }
}
