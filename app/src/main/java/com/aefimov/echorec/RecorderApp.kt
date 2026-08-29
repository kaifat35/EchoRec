package com.aefimov.echorec

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.*
import java.util.*
import kotlin.math.sin

@Composable
fun RecorderApp(
    isShizukuReady: Boolean,
    shizukuStatus: String,
    onRequestPermissions: () -> Unit,
    onStartShizuku: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: RecorderViewModel = viewModel()
    val recordings by viewModel.recordings.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()
    val recordingTime by viewModel.recordingTime.collectAsState()
    val audioLevel by viewModel.audioLevel.collectAsState()
    var isPlaying by remember { mutableStateOf(false) }
    var currentPlayingId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (!isShizukuReady) {
                        Toast.makeText(context, "⚠️ Shizuku не активен!\nНажмите кнопку '▶️ Запустить Shizuku'", Toast.LENGTH_LONG).show()
                        return@FloatingActionButton
                    }

                    if (isRecording) {
                        if (isPaused) {
                            viewModel.resumeRecording()
                        } else {
                            viewModel.pauseRecording()
                        }
                    } else {
                        viewModel.startRecording(context, useShizuku = isShizukuReady)
                    }
                },
                containerColor = when {
                    isRecording && !isPaused -> Color.Red
                    isRecording && isPaused -> Color(0xFFFFA500)
                    !isShizukuReady -> Color.Gray
                    else -> MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(80.dp)
            ) {
                Icon(
                    when {
                        isRecording && !isPaused -> Icons.Default.Pause
                        isRecording && isPaused -> Icons.Default.PlayArrow
                        else -> Icons.Default.Call
                    },
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = Color.White
                )
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Панель Shizuku
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isShizukuReady) 48.dp else 80.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isShizukuReady)
                        Color(0xFF4CAF50).copy(alpha = 0.15f)
                    else
                        Color(0xFFFF9800).copy(alpha = 0.15f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            if (isShizukuReady) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isShizukuReady) Color(0xFF4CAF50) else Color(0xFFFF9800)
                        )
                        Column {
                            Text(
                                text = if (isShizukuReady) "Shizuku: Активен" else "Shizuku: Не активен",
                                fontWeight = FontWeight.Bold,
                                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                color = if (isShizukuReady) Color(0xFF4CAF50) else Color(0xFFFF9800)
                            )
                            if (!isShizukuReady) {
                                Text(
                                    text = "Нажмите кнопку для активации",
                                    fontSize = MaterialTheme.typography.bodySmall.fontSize,
                                    color = Color.Gray
                                )
                            }
                        }
                    }

                    if (!isShizukuReady) {
                        Button(
                            onClick = onStartShizuku,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2196F3)
                            ),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text("Запустить Shizuku", fontSize = MaterialTheme.typography.labelMedium.fontSize)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Визуализатор звука
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1A1A2E).copy(alpha = 0.1f)
                )
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (isRecording) {
                        AudioVisualizer(
                            level = audioLevel,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = "🎵 Готов к записи",
                            modifier = Modifier.align(Alignment.Center),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.Gray
                        )
                    }

                    // Время записи
                    if (isRecording) {
                        Text(
                            text = formatTime(recordingTime),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(8.dp),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isRecording && !isPaused) Color.Red else Color(0xFFFFA500)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Статус и кнопка остановки
            if (isRecording) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isPaused) "⏸ ПАУЗА" else "🔴 ЗАПИСЬ",
                        color = if (isPaused) Color(0xFFFFA500) else Color.Red,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )

                    Button(
                        onClick = {
                            viewModel.stopRecording(context)
                            isPlaying = false
                            currentPlayingId = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE74C3C)),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Остановить")
                        Text("Остановить")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Совет
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFF3CD)
                    )
                ) {
                    Text(
                        text = if (isShizukuReady)
                            "✅ Shizuku активен - запись двух голосов"
                        else
                            "💡 Включите громкую связь для записи собеседника",
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isShizukuReady) Color(0xFF004085) else Color(0xFF856404)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Заголовок списка
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📁 Записи (${recordings.size})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                if (recordings.isNotEmpty()) {
                    TextButton(onClick = { viewModel.deleteAllRecordings(context) }) {
                        Text("Очистить всё", color = Color.Red)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Список записей
            if (recordings.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Нет записей",
                            color = Color.Gray,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Активируйте Shizuku и нажмите на кнопку 🟢",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(recordings) { record ->
                        RecordingItem(
                            record = record,
                            isPlaying = isPlaying && currentPlayingId == record.id,
                            onPlay = {
                                if (isPlaying && currentPlayingId == record.id) {
                                    viewModel.stopPlayback()
                                    isPlaying = false
                                    currentPlayingId = null
                                } else {
                                    viewModel.playRecording(context, record)
                                    isPlaying = true
                                    currentPlayingId = record.id
                                }
                            },
                            onDelete = {
                                viewModel.deleteRecording(context, record)
                                if (currentPlayingId == record.id) {
                                    isPlaying = false
                                    currentPlayingId = null
                                }
                            },
                            onShare = { viewModel.shareRecording(context, record) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AudioVisualizer(level: Float, modifier: Modifier = Modifier) {
    val barsCount = 30
    val density = LocalDensity.current
    var width by remember { mutableStateOf(0f) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { width = it.size.width.toFloat() }
    ) {
        val barWidth = width / barsCount * 0.6f
        val spacing = width / barsCount * 0.4f
        val maxHeight = size.height * 0.7f
        val centerY = size.height / 2

        for (i in 0 until barsCount) {
            val x = i * (barWidth + spacing) + spacing / 2
            val height = maxHeight * (0.2f + 0.8f * level * (1 + sin(i * 0.5f + level * 10)) / 2)

            val alpha = 0.3f + 0.7f * (height / maxHeight)
            val color = Color(
                red = 0.2f + 0.8f * (height / maxHeight),
                green = 0.2f + 0.8f * (1 - height / maxHeight),
                blue = 0.8f - 0.6f * (height / maxHeight),
                alpha = alpha
            )

            drawRoundRect(
                color = color,
                topLeft = Offset(x, centerY - height / 2),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )
        }
    }
}

@Composable
fun RecordingItem(
    record: Recording,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() },
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) Color(0xFFE3F2FD) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (isPlaying) Color(0xFF2196F3) else Color(0xFFE0E0E0)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = if (isPlaying) Color.White else Color.Gray
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = record.name,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = record.formattedSize,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                        Text(
                            text = "•",
                            color = Color.Gray
                        )
                        Text(
                            text = record.date,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                        if (record.duration > 0) {
                            Text(
                                text = "•",
                                color = Color.Gray
                            )
                            Text(
                                text = formatTime(record.duration),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }

            Row {
                IconButton(
                    onClick = onShare,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Поделиться",
                        modifier = Modifier.size(18.dp),
                        tint = Color.Gray
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Удалить",
                        modifier = Modifier.size(18.dp),
                        tint = Color.Red.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

fun formatTime(milliseconds: Long): String {
    val seconds = (milliseconds / 1000) % 60
    val minutes = (milliseconds / (1000 * 60)) % 60
    val hours = milliseconds / (1000 * 60 * 60)
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}