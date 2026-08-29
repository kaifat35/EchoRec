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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun RecorderApp(
    state: ShizukuState,
    requestPermissions: () -> Unit,
    requestShizuku: () -> Unit,
    model: RecorderViewModel = viewModel()
) {
    val context = LocalContext.current
    var showShizukuHelp by remember { mutableStateOf(false) }
    model.message?.let { text -> LaunchedEffect(text) { /* Toast is emitted by model; consume so it is not repeated. */ model.consumeMessage() } }
    Scaffold(
        topBar = { TopAppBar(title = { Text("EchoRec") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (model.isRecording) {
                    if (model.isPaused) model.resumeRecording() else model.pauseRecording()
                } else model.startRecording(context, state == ShizukuState.GRANTED)
            }) {
                Icon(
                    if (model.isRecording && !model.isPaused) Icons.Default.Pause else Icons.Default.Mic,
                    null
                )
            }
        }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ShizukuCard(
                state,
                {
                    if (state == ShizukuState.UNAVAILABLE) showShizukuHelp =
                        true else requestShizuku()
                })
            if (model.isRecording) {
                AudioVisualizer(model.audioLevel, Modifier
                    .fillMaxWidth()
                    .height(100.dp)); Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (model.isPaused) "Пауза ${formatTime(model.recordingTime)}" else "Запись ${
                            formatTime(
                                model.recordingTime
                            )
                        }", style = MaterialTheme.typography.titleMedium
                    ); Button(onClick = { model.stopRecording(context) }) {
                    Icon(
                        Icons.Default.Stop,
                        null
                    ); Spacer(Modifier.width(6.dp)); Text("Остановить")
                }
                }
            } else Card(Modifier.fillMaxWidth()) {
                Text(
                    "Готов к записи с микрофона",
                    Modifier.padding(24.dp)
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Записи (${model.recordings.size})",
                    style = MaterialTheme.typography.titleLarge
                ); if (model.recordings.isNotEmpty()) TextButton(model::deleteAll) { Text("Очистить") }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(
                    model.recordings,
                    key = { it.id }) { record ->
                    RecordingRow(
                        record,
                        model.playingId == record.id,
                        { model.togglePlayback(record) },
                        { model.share(context, record) },
                        { model.delete(record) })
                }
            }
            TextButton(requestPermissions) { Text("Проверить разрешения") }
        }
    }
    if (showShizukuHelp) AlertDialog(
        onDismissRequest = { showShizukuHelp = false },
        confirmButton = { TextButton(onClick = { showShizukuHelp = false }) { Text("Понятно") } },
        title = { Text("Shizuku не запущен") },
        text = { Text("Установите приложение Shizuku, запустите его через беспроводную отладку или ADB и вернитесь сюда. Само приложение не может запускать Shizuku или получать системные привилегии автоматически.") })
}

@Composable
private fun ShizukuCard(state: ShizukuState, action: () -> Unit) {
    val ready = state == ShizukuState.GRANTED; Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (ready) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
        )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                when (state) {
                    ShizukuState.GRANTED -> "Shizuku: доступ предоставлен"; ShizukuState.CONNECTED -> "Shizuku: запросите доступ"; ShizukuState.DENIED -> "Shizuku: доступ отклонён"; ShizukuState.UNAVAILABLE -> "Shizuku не установлен или не запущен"
                }
            ); if (!ready) Button(action) { Text(if (state == ShizukuState.UNAVAILABLE) "Инструкция" else "Разрешить") }
        }
    }
}

@Composable
private fun AudioVisualizer(level: Float, modifier: Modifier) {
    Canvas(modifier) {
        val bars = 24;
        val w = size.width / bars; repeat(bars) { index ->
        val h = size.height * (0.12f + level * (0.25f + (index % 5) * .15f)); drawRoundRect(
        Color(
            0xFF1565C0
        ),
        topLeft = androidx.compose.ui.geometry.Offset(
            index * w + w * .15f,
            (size.height - h) / 2
        ),
        size = androidx.compose.ui.geometry.Size(w * .7f, h),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
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
            Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(play) {
                Icon(
                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    "Воспроизвести"
                )
            }; Column(Modifier.weight(1f)) {
            Text(
                record.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            ); Text(
            "${record.date} • ${record.formattedSize} • ${formatTime(record.duration)}",
            style = MaterialTheme.typography.bodySmall
        )
        }; IconButton(share) { Icon(Icons.Default.Share, "Поделиться") }; IconButton(delete) {
            Icon(
                Icons.Default.Delete,
                "Удалить"
            )
        }
        }
    }
}

fun formatTime(time: Long): String = String.format("%02d:%02d", time / 60_000, (time / 1_000) % 60)
