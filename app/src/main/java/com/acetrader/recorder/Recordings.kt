package com.acetrader.recorder

import android.content.Context
import android.media.MediaPlayer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** File listing / playback helpers used by the UI. Recording itself lives in [RecordingService]. */
object Recordings {

    fun dir(context: Context): File =
        File(context.filesDir, "recordings").apply { mkdirs() }

    fun newFile(context: Context): File {
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        return File(dir(context), "Recording_$stamp.m4a")
    }

    fun list(context: Context): List<File> =
        dir(context).listFiles { f -> f.extension == "m4a" }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

    fun durationMillis(file: File): Long = try {
        MediaPlayer().run {
            setDataSource(file.absolutePath)
            prepare()
            duration.toLong().also { release() }
        }
    } catch (e: Exception) {
        0L
    }
}

/** Simple playback for a single file at a time. */
class Player {
    private var player: MediaPlayer? = null

    /** Returns false if the file could not be played. */
    fun play(file: File, onComplete: () -> Unit): Boolean {
        stop()
        val p = MediaPlayer()
        return try {
            p.setDataSource(file.absolutePath)
            p.setOnCompletionListener {
                stop()
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

    fun stop() {
        player?.release()
        player = null
    }
}
