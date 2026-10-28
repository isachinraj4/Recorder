package com.acetrader.recorder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import java.io.File
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val dark = isSystemInDarkTheme()
            val accent = Color(0xFFD32F2F)
            MaterialTheme(
                colorScheme = if (dark) darkColorScheme(primary = accent) else lightColorScheme(primary = accent)
            ) {
                RecorderScreen()
            }
        }
    }
}

@Composable
fun RecorderScreen() {
    val context = LocalContext.current
    val player = remember { Player() }
    DisposableEffect(Unit) { onDispose { player.stop() } }

    val isRecording by RecordingService.State.isRecording.collectAsStateWithLifecycle()
    val startedAt by RecordingService.State.startTime.collectAsStateWithLifecycle()

    var recordings by remember { mutableStateOf(Recordings.list(context)) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf<File?>(null) }
    var pendingDelete by remember { mutableStateOf<File?>(null) }

    // Refresh the list whenever a recording finishes.
    LaunchedEffect(isRecording) {
        if (!isRecording) recordings = Recordings.list(context)
    }

    // Tick the timer from the service's start time, so it stays correct after reopening the app.
    LaunchedEffect(isRecording, startedAt) {
        while (isRecording) {
            elapsed = System.currentTimeMillis() - startedAt
            delay(200)
        }
        if (!isRecording) elapsed = 0L
    }

    fun startRecording() {
        player.stop()
        playing = null
        RecordingService.start(context)
    }

    val recordPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecording()
        else Toast.makeText(context, "Microphone permission is needed to record", Toast.LENGTH_LONG).show()
    }

    // On Android 13+ the recording notification needs this permission; recording still works
    // without it, but the notification (and its Stop button) won't appear.
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result ignored; handled before starting */ }

    fun ensureNotifPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun onRecordButton() {
        if (isRecording) {
            RecordingService.stop(context)
            return
        }
        ensureNotifPermission()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startRecording()
        } else {
            recordPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Scaffold(
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onRecordButton() },
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(80.dp)
            ) {
                Icon(
                    painter = painterResource(if (isRecording) R.drawable.ic_stop else R.drawable.ic_mic),
                    contentDescription = if (isRecording) "Stop recording" else "Start recording",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formatDuration(elapsed),
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Light,
                        color = if (isRecording) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (isRecording) "Recording… (continues in the background)"
                        else "Tap the mic to record",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text("Recordings", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            if (recordings.isEmpty()) {
                Text(
                    "No recordings yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(recordings, key = { it.name }) { file ->
                    RecordingRow(
                        file = file,
                        durationMillis = remember(file) { Recordings.durationMillis(file) },
                        isPlaying = playing == file,
                        onPlayToggle = {
                            if (playing == file) {
                                player.stop()
                                playing = null
                            } else if (!isRecording) {
                                if (player.play(file) { playing = null }) {
                                    playing = file
                                } else {
                                    Toast.makeText(context, "Could not play recording", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onShare = { share(context, file) },
                        onDelete = { pendingDelete = file }
                    )
                }
                // Keep the last row clear of the record button.
                item { Spacer(Modifier.height(100.dp)) }
            }
        }
    }

    pendingDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete recording?") },
            text = { Text(file.nameWithoutExtension) },
            confirmButton = {
                TextButton(onClick = {
                    if (playing == file) {
                        player.stop()
                        playing = null
                    }
                    file.delete()
                    recordings = Recordings.list(context)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun RecordingRow(
    file: File,
    durationMillis: Long,
    isPlaying: Boolean,
    onPlayToggle: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
        ) {
            IconButton(onClick = onPlayToggle) {
                if (isPlaying) {
                    Icon(painterResource(R.drawable.ic_stop), "Stop", tint = MaterialTheme.colorScheme.primary)
                } else {
                    Icon(Icons.Filled.PlayArrow, "Play")
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(Date(file.lastModified())),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    formatDuration(durationMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onShare) { Icon(Icons.Filled.Share, "Share") }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete") }
        }
    }
}

private fun share(context: android.content.Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "audio/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share recording"))
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
