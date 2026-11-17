package io.github.forkyfor.oldphone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import java.text.DateFormat
import java.util.Date

/**
 * Records a meeting held around the phone with its microphone. Runs as a foreground service so
 * it keeps going with the screen off. The audio stays in the app's private storage and is
 * deleted as soon as NotebookLM's result has been accepted.
 */
class MeetingRecorderService : Service() {
    private var recorder: MediaRecorder? = null
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start(intent.getStringExtra(EXTRA_ID))
            ACTION_STOP -> stop()
        }
        return START_NOT_STICKY
    }

    private fun localized(): Context = BaseActivity.wrap(this)

    // A plain free-space check is enough to keep a margin; clearing caches is not this app's job.
    @android.annotation.SuppressLint("UsableSpace")
    private fun start(id: String?) {
        if (id == null || recordingId != null) return
        val store = MeetingFlow.store(this)
        val n = notification()
        // The "microphone" service type exists from Android 11; before that a plain foreground service records.
        if (Build.VERSION.SDK_INT >= 30) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
        try {
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(16000)
            r.setAudioEncodingBitRate(48000)
            // Never fill the phone: stop 50 MB before the storage is full (about 22 MB per hour).
            val room = (store.dir.usableSpace - 50L * 1024 * 1024).coerceAtLeast(1024L * 1024)
            r.setMaxFileSize(room.coerceAtMost(1500L * 1024 * 1024))
            r.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) stop()
            }
            r.setOutputFile(store.audioFile(id).absolutePath)
            r.prepare()
            r.start()
            recorder = r
            recordingId = id
            startedAt = SystemClock.elapsedRealtime()
            error = null
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OldPhoneClaude:meeting").apply { acquire(6 * 3600_000L) }
        } catch (e: Exception) {
            error = e.javaClass.simpleName
            try { recorder?.release() } catch (x: Exception) { }
            recorder = null
            store.audioFile(id).delete()
            finishService()
        }
    }

    private fun stop() {
        val id = recordingId
        if (id == null) {
            finishService()
            return
        }
        val store = MeetingFlow.store(this)
        val duration = SystemClock.elapsedRealtime() - startedAt
        var ok = true
        try {
            recorder?.stop()
        } catch (e: RuntimeException) {
            ok = false // stopped right after starting: nothing usable was recorded
        }
        try { recorder?.release() } catch (e: Exception) { }
        recorder = null
        recordingId = null
        try { wake?.release() } catch (e: Exception) { }
        wake = null
        val audio = store.audioFile(id)
        if (ok && audio.length() > 0) {
            val created = System.currentTimeMillis() - duration
            val m = Meeting(id).apply {
                this.created = created
                durationMs = duration
                state = Meeting.RECORDED
                title = localized().getString(
                    R.string.meeting_default_title,
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(created))
                )
            }
            store.save(m)
            finishedId = id
        } else {
            audio.delete()
            error = "empty"
        }
        finishService()
    }

    private fun finishService() {
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }

    private fun notification(): Notification {
        val c = localized()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, c.getString(R.string.meeting_channel), NotificationManager.IMPORTANCE_LOW)
            )
            Notification.Builder(this, CHANNEL)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }
        val immutable = if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        val open = PendingIntent.getActivity(
            this, 10, Intent(this, MeetingsActivity::class.java), immutable or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getActivity(
            this, 11, Intent(this, MeetingsActivity::class.java).putExtra(MeetingsActivity.EXTRA_ACTION, MeetingsActivity.ACTION_STOP),
            immutable or PendingIntent.FLAG_UPDATE_CURRENT
        )
        @Suppress("DEPRECATION")
        return builder.setSmallIcon(R.drawable.ic_meeting)
            .setContentTitle(c.getString(R.string.meeting_recording_title))
            .setContentText(c.getString(R.string.meeting_recording_text))
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, c.getString(R.string.meeting_stop), stop)
            .build()
    }

    companion object {
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        const val EXTRA_ID = "id"
        private const val CHANNEL = "meetings"
        private const val NOTIFICATION_ID = 7

        /** Shared with the screens, which poll it: the service and the UI live in one process. */
        @Volatile var recordingId: String? = null
        @Volatile var startedAt = 0L
        @Volatile var error: String? = null
        @Volatile var finishedId: String? = null

        val recording get() = recordingId != null

        fun start(ctx: Context, id: String) {
            val i = Intent(ctx, MeetingRecorderService::class.java).setAction(ACTION_START).putExtra(EXTRA_ID, id)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, MeetingRecorderService::class.java).setAction(ACTION_STOP))
        }
    }
}
