package com.acetrader.recorder

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
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Foreground service that records microphone audio to an .m4a file. Running as a
 * foreground service lets recording continue after the app's screen is closed or the
 * user switches away. Android requires a visible, ongoing notification for this, which is
 * shown here with a Stop action; tapping it (or Stop in the app) ends the recording.
 */
class RecordingService : Service() {

    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start()
            ACTION_STOP -> {
                stop()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun start() {
        if (recorder != null) return
        val file = Recordings.newFile(this)
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioEncodingBitRate(128_000)
        r.setAudioSamplingRate(44_100)
        r.setOutputFile(file.absolutePath)
        try {
            r.prepare()
            r.start()
        } catch (e: Exception) {
            r.release()
            file.delete()
            State.recording.value = false
            stopSelf()
            return
        }
        recorder = r
        currentFile = file

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        State.startedAt.value = System.currentTimeMillis()
        State.recording.value = true
    }

    private fun stop() {
        val r = recorder ?: return
        try {
            r.stop()
        } catch (e: RuntimeException) {
            // stop() throws if nothing was captured yet; drop the empty file.
            currentFile?.delete()
        } finally {
            r.release()
            recorder = null
            currentFile = null
            State.recording.value = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        }
    }

    override fun onDestroy() {
        stop()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Recording",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Shows while a recording is in progress" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Recording in progress")
            .setSmallIcon(R.drawable.ic_mic)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(R.drawable.ic_stop, "Stop", stopIntent)
            .build()
    }

    /** Recording state observable by the UI, whether or not the activity is open. */
    object State {
        val recording = MutableStateFlow(false)
        val startedAt = MutableStateFlow(0L)
        val isRecording: StateFlow<Boolean> get() = recording
        val startTime: StateFlow<Long> get() = startedAt
    }

    companion object {
        const val ACTION_START = "com.acetrader.recorder.START"
        const val ACTION_STOP = "com.acetrader.recorder.STOP"
        private const val CHANNEL_ID = "recording"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            val intent = Intent(context, RecordingService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, RecordingService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
