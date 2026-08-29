package com.aefimov.echorec

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.*
import rikka.shizuku.Shizuku
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class Recording(
    val id: String,
    val file: File,
    val name: String,
    val date: String,
    val formattedSize: String,
    val duration: Long = 0
)

class RecorderViewModel : ViewModel() {
    private val _recordings = mutableStateListOf<Recording>()
    val recordings: List<Recording> = _recordings

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null

    private var _isRecording = mutableStateOf(false)
    val isRecording: State<Boolean> = _isRecording

    private var _isPaused = mutableStateOf(false)
    val isPaused: State<Boolean> = _isPaused

    private var _recordingTime = mutableStateOf(0L)
    val recordingTime: State<Long> = _recordingTime

    private var _audioLevel = mutableStateOf(0f)
    val audioLevel: State<Float> = _audioLevel

    private var timerJob: Job? = null
    private var levelJob: Job? = null
    private var currentFile: File? = null
    private var startTime = 0L
    private var pausedTime = 0L

    init {
        loadRecordings()
    }

    fun startRecording(context: Context, useShizuku: Boolean = false) {
        if (_isRecording.value) return

        try {
            val fileName = "Call_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.m4a"
            val musicDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            if (musicDir != null && !musicDir.exists()) {
                musicDir.mkdirs()
            }
            currentFile = File(musicDir, fileName)

            // Выбираем источник аудио
            val audioSource = if (useShizuku && Shizuku.pingBinder()) {
                try {
                    // Пытаемся использовать системный аудио поток через Shizuku
                    Log.d("Recorder", "Используем Shizuku для записи")
                    MediaRecorder.AudioSource.VOICE_CALL
                } catch (e: Exception) {
                    Log.w("Recorder", "Shizuku не может использовать VOICE_CALL, падаем на MIC")
                    MediaRecorder.AudioSource.MIC
                }
            } else {
                MediaRecorder.AudioSource.MIC
            }

            mediaRecorder = MediaRecorder().apply {
                setAudioSource(audioSource)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioBitRate(128000)
                setOutputFile(currentFile?.absolutePath)

                prepare()
                start()
            }

            _isRecording.value = true
            _isPaused.value = false
            startTime = System.currentTimeMillis()
            pausedTime = 0L

            startTimer()
            startLevelMonitoring()

            Log.d("Recorder", "Запись начата, источник: ${if (useShizuku) "VOICE_CALL" else "MIC"}")

        } catch (e: Exception) {
            Log.e("Recorder", "Ошибка записи: ${e.message}", e)
            _isRecording.value = false
            Toast.makeText(context, "Ошибка записи: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun pauseRecording() {
        if (!_isRecording.value || _isPaused.value) return

        try {
            mediaRecorder?.pause()
            _isPaused.value = true
            pausedTime = System.currentTimeMillis()
            timerJob?.cancel()
            levelJob?.cancel()
            Log.d("Recorder", "Запись на паузе")
        } catch (e: Exception) {
            Log.e("Recorder", "Ошибка паузы: ${e.message}", e)
        }
    }

    fun resumeRecording() {
        if (!_isRecording.value || !_isPaused.value) return

        try {
            mediaRecorder?.resume()
            _isPaused.value = false
            startTime += System.currentTimeMillis() - pausedTime
            startTimer()
            startLevelMonitoring()
            Log.d("Recorder", "Запись возобновлена")
        } catch (e: Exception) {
            Log.e("Recorder", "Ошибка возобновления: ${e.message}", e)
        }
    }

    fun stopRecording(context: Context) {
        if (!_isRecording.value) return

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null

            _isRecording.value = false
            _isPaused.value = false
            _recordingTime.value = 0

            timerJob?.cancel()
            levelJob?.cancel()

            currentFile?.let { file ->
                if (file.exists() && file.length() > 0) {
                    loadRecordings()
                    Log.d("Recorder", "Запись сохранена: ${file.name}")
                } else {
                    file.delete()
                    Log.w("Recorder", "Запись пустая, удалена")
                }
            }

            currentFile = null

        } catch (e: Exception) {
            Log.e("Recorder", "Ошибка остановки записи: ${e.message}", e)
        }
    }

    fun playRecording(context: Context, record: Recording) {
        try {
            stopPlayback()

            val player = MediaPlayer().apply {
                setDataSource(record.file.absolutePath)
                prepare()
                start()
            }

            mediaPlayer = player

            player.setOnCompletionListener {
                stopPlayback()
            }

        } catch (e: Exception) {
            Log.e("Recorder", "Ошибка воспроизведения: ${e.message}", e)
            Toast.makeText(context, "Ошибка воспроизведения", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopPlayback() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null
    }

    fun deleteRecording(context: Context, record: Recording) {
        try {
            if (record.file.exists()) {
                record.file.delete()
                _recordings.remove(record)
                Log.d("Recorder", "Запись удалена: ${record.file.name}")
            }
        } catch (e: Exception) {
            Log.e("Recorder", "Ошибка удаления: ${e.message}", e)
        }
    }

    fun deleteAllRecordings(context: Context) {
        _recordings.forEach { record ->
            try {
                if (record.file.exists()) {
                    record.file.delete()
                }
            } catch (e: Exception) {
                Log.e("Recorder", "Ошибка удаления: ${e.message}", e)
            }
        }
        _recordings.clear()
    }

    fun shareRecording(context: Context, record: Recording) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                record.file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/m4a"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Поделиться записью"))

        } catch (e: Exception) {
            Log.e("Recorder", "Ошибка шаринга: ${e.message}", e)
            Toast.makeText(context, "Ошибка шаринга: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = CoroutineScope(Dispatchers.Main).launch {
            while (_isRecording.value) {
                _recordingTime.value = if (!_isPaused.value) {
                    System.currentTimeMillis() - startTime
                } else {
                    _recordingTime.value
                }
                delay(100)
            }
        }
    }

    private fun startLevelMonitoring() {
        levelJob?.cancel()
        levelJob = CoroutineScope(Dispatchers.Main).launch {
            while (_isRecording.value && !_isPaused.value) {
                // Эмулируем уровень звука для визуализации
                _audioLevel.value = (0.3f + 0.7f * kotlin.math.random()).toFloat()
                delay(50)
            }
        }
    }

    private fun loadRecordings() {
        val context = getApplicationContext() ?: return
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        val files = dir?.listFiles { file ->
            file.extension in listOf("m4a", "mp3", "wav", "aac")
        } ?: emptyArray()

        _recordings.clear()
        _recordings.addAll(files.map { file ->
            Recording(
                id = file.absolutePath,
                file = file,
                name = file.nameWithoutExtension,
                date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                    .format(Date(file.lastModified())),
                formattedSize = formatFileSize(file.length()),
                duration = getAudioDuration(file)
            )
        }.sortedByDescending { it.file.lastModified() })
    }

    private fun getAudioDuration(file: File): Long {
        return try {
            val player = MediaPlayer()
            player.setDataSource(file.absolutePath)
            player.prepare()
            val duration = player.duration.toLong()
            player.release()
            duration
        } catch (e: Exception) {
            0
        }
    }

    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> String.format("%.1f KB", size / 1024.0)
            size < 1024 * 1024 * 1024 -> String.format("%.1f MB", size / (1024.0 * 1024.0))
            else -> String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0))
        }
    }

    private fun getApplicationContext(): Context? {
        return try {
            android.app.ActivityThread.currentApplication().applicationContext
        } catch (e: Exception) {
            null
        }
    }
}