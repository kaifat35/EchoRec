package com.aefimov.echorec

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecorderApp(
    microphoneGranted: Boolean,
    phoneStateGranted: Boolean,
    requestPermissions: () -> Unit,
    model: RecorderViewModel = viewModel()
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
                }
            ) {
                Icon(
                    imageVector = if (model.isRecording && !model.isPaused) Icons.Default.Pause else Icons.Default.Mic,
                    contentDescription = if (model.isRecording) "Пауза" else "Запись"
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Карточка состояния разрешений и автоматической записи
            StatusCard(microphoneGranted, phoneStateGranted, requestPermissions)

            // Панель записи (визуализатор, время, кнопка остановки)
            RecordingPanel(model, context)

            // Список записей
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Записи (${model.recordings.size})", style = MaterialTheme.typography.titleLarge)
                if (model.recordings.isNotEmpty()) TextButton(model::deleteAll) { Text("Очистить") }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(model.recordings, key = Recording::id) { record ->
                    RecordingRow(
                        record = record,
                        playing = model.playingId == record.id,
                        play = { model.togglePlayback(record) },
                        share = { model.share(context, record) },
                        delete = { model.delete(record) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    microphoneGranted: Boolean,
    phoneStateGranted: Boolean,
    requestPermissions: () -> Unit
) {
    val allGranted = microphoneGranted && phoneStateGranted
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (allGranted) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (allGranted) "✅ Все разрешения получены" else "⚠️ Требуются разрешения",
                modifier = Modifier.weight(1f)
            )
            if (!allGranted) Button(requestPermissions) { Text("Разрешить") }
        }
    }
}

@Composable
private fun RecordingPanel(model: RecorderViewModel, context: android.content.Context) {
    if (!model.isRecording) {
        Card(Modifier.fillMaxWidth()) {
            Text("Готов к записи", Modifier.padding(24.dp))
        }
        return
    }
    AudioVisualizer(model.audioLevel, Modifier.fillMaxWidth().height(100.dp))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (model.isPaused) "Пауза ${formatTime(model.recordingTime)}" else "Запись ${formatTime(model.recordingTime)}"
        )
        Button(onClick = { model.stopRecording(context) }) {
            Icon(Icons.Default.Stop, null)
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
            drawRoundRect(
                Color(0xFF1565C0),
                Offset(index * width + width * .15f, (size.height - height) / 2),
                Size(width * .7f, height),
                CornerRadius(8f, 8f)
            )
        }
    }
}

@Composable
private fun RecordingRow(
    record: Recording,
    playing: Boolean,
    play: () -> Unit,
    share: () -> Unit,
    delete: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(play) {
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Воспроизвести")
            }
            Column(Modifier.weight(1f)) {
                Text(record.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${record.date} • ${record.formattedSize} • ${formatTime(record.duration)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(share) { Icon(Icons.Default.Share, "Поделиться") }
            IconButton(delete) { Icon(Icons.Default.Delete, "Удалить") }
        }
    }
}

fun formatTime(time: Long): String =
    String.format(Locale.US, "%02d:%02d", time / 60_000, (time / 1_000) % 60)