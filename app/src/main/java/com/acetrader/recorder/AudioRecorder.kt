package com.acetrader.recorder

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Records microphone audio to .m4a files and plays them back. */
class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var currentFile: File? = null

    val recordingsDir: File = File(context.filesDir, "recordings").apply { mkdirs() }

    fun recordings(): List<File> =
        recordingsDir.listFiles { f -> f.extension == "m4a" }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

    fun startRecording() {
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val file = File(recordingsDir, "Recording_$stamp.m4a")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
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
            throw e
        }
        recorder = r
        currentFile = file
    }

    fun stopRecording() {
        val r = recorder ?: return
        try {
            r.stop()
        } catch (e: RuntimeException) {
            // stop() throws if nothing was captured (e.g. stopped instantly); drop the empty file.
            currentFile?.delete()
        } finally {
            r.release()
            recorder = null
            currentFile = null
        }
    }

    /** Returns false if the file could not be played. */
    fun play(file: File, onComplete: () -> Unit): Boolean {
        stopPlayback()
        val p = MediaPlayer()
        return try {
            p.setDataSource(file.absolutePath)
            p.setOnCompletionListener {
                stopPlayback()
                onComplete()
            }
            p.prepare()
            p.start()
            player = p
            true
        } catch (e: Exception) {
            p.release()
            false
        }
    }

    fun stopPlayback() {
        player?.release()
        player = null
    }

    fun durationMillis(file: File): Long = try {
        MediaPlayer().run {
            setDataSource(file.absolutePath)
            prepare()
            duration.toLong().also { release() }
        }
    } catch (e: Exception) {
        0L
    }

    fun release() {
        stopRecording()
        stopPlayback()
    }
}
