package com.aefimov.echorec

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/** Главный экран: запись с микрофона и локальный список завершённых файлов. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecorderApp(
    microphoneGranted: Boolean,
    requestPermissions: () -> Unit,
    model: RecorderViewModel = viewModel(),
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val message by model.messages.collectAsState(initial = "")

    LaunchedEffect(message) {
        if (message.isNotEmpty()) snackbar.showSnackbar(message)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("EchoRec") }) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    when {
                        !microphoneGranted -> requestPermissions()
                        !model.isRecording -> model.startRecording(context)
                        model.isPaused -> model.resumeRecording()
                        else -> model.pauseRecording()
                    }
                },
            ) {
                Icon(
                    imageVector = if (model.isRecording && !model.isPaused) Icons.Default.Pause else Icons.Default.Mic,
                    contentDescription = if (model.isRecording) "Поставить запись на паузу" else "Начать запись",
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PermissionCard(microphoneGranted, requestPermissions)
            RecordingPanel(model, context)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Записи (${model.recordings.size})", style = MaterialTheme.typography.titleLarge)
                if (model.recordings.isNotEmpty()) TextButton(model::deleteAll) { Text("Очистить") }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(model.recordings, key = Recording::id) { recording ->
                    RecordingRow(
                        record = recording,
                        playing = model.playingId == recording.id,
                        play = { model.togglePlayback(recording) },
                        share = { model.share(context, recording) },
                        delete = { model.delete(recording) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(granted: Boolean, request: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (granted) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (granted) "Микрофон: разрешён" else "Для записи нужно разрешение на микрофон",
                modifier = Modifier.weight(1f),
            )
            if (!granted) Button(request) { Text("Разрешить") }
        }
    }
}

@Composable
private fun RecordingPanel(model: RecorderViewModel, context: android.content.Context) {
    if (!model.isRecording) {
        Card(Modifier.fillMaxWidth()) { Text("Готов к записи с микрофона", Modifier.padding(24.dp)) }
        return
    }
    AudioVisualizer(model.audioLevel, Modifier.fillMaxWidth().height(100.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(if (model.isPaused) "Пауза ${formatTime(model.recordingTime)}" else "Запись ${formatTime(model.recordingTime)}")
        Button(onClick = { model.stopRecording(context) }) {
            Icon(Icons.Default.Stop, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Остановить")
        }
    }
}

@Composable
private fun AudioVisualizer(level: Float, modifier: Modifier) {
    Canvas(modifier) {
        val bars = 24
        val width = size.width / bars
        repeat(bars) { index ->
            val height = size.height * (0.12f + level * (0.25f + (index % 5) * .15f))
            drawRoundRect(Color(0xFF1565C0), Offset(index * width + width * .15f, (size.height - height) / 2), Size(width * .7f, height), CornerRadius(8f, 8f))
        }
    }
}

@Composable
private fun RecordingRow(record: Recording, playing: Boolean, play: () -> Unit, share: () -> Unit, delete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(play) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Воспроизвести") }
            Column(Modifier.weight(1f)) {
                Text(record.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${record.date} • ${record.formattedSize} • ${formatTime(record.duration)}", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(share) { Icon(Icons.Default.Share, "Поделиться") }
            IconButton(delete) { Icon(Icons.Default.Delete, "Удалить") }
        }
    }
}

fun formatTime(time: Long): String = String.format("%02d:%02d", time / 60_000, (time / 1_000) % 60)
