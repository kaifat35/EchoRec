package com.aefimov.echorec

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Recording(val id: String, val file: File, val name: String, val date: String, val formattedSize: String, val duration: Long)

/**
 * Управляет ресурсами записи и воспроизведения. Файлы находятся в app-specific Music,
 * поэтому не требуют разрешений на хранилище и удаляются вместе с приложением.
 */
class RecorderViewModel(application: Application) : AndroidViewModel(application) {
    private val directory: File?
        get() = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_MUSIC)

    var recordings by mutableStateOf<List<Recording>>(emptyList()); private set
    var isRecording by mutableStateOf(false); private set
    var isPaused by mutableStateOf(false); private set
    var recordingTime by mutableLongStateOf(0L); private set
    var audioLevel by mutableFloatStateOf(0f); private set
    var playingId by mutableStateOf<String?>(null); private set

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val messages: SharedFlow<String> = _messages
    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var currentFile: File? = null
    private var startedAt = 0L
    private var pauseStartedAt = 0L
    private var ticker: Job? = null

    init { reload() }

    /** Создаёт M4A/AAC из [MediaRecorder.AudioSource.MIC] после явного действия пользователя. */
    fun startRecording(context: Context) {
        if (isRecording) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            message("Нужно разрешение на запись аудио")
            return
        }
        val outputDir = directory ?: return message("Не удалось открыть папку для записей")
        if (!outputDir.exists() && !outputDir.mkdirs()) return message("Не удалось создать папку для записей")
        val file = File(outputDir, "Recording_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.m4a")
        try {
            recorder = newRecorder(file).also { it.prepare(); it.start() }
            currentFile = file
            isRecording = true
            isPaused = false
            recordingTime = 0
            startedAt = System.currentTimeMillis()
            // Сервис запускается сразу после старта, чтобы Android не остановил микрофон в фоне.
            ContextCompat.startForegroundService(context, Intent(context, RecordingService::class.java))
            startTicker()
        } catch (error: Exception) {
            recorder?.release()
            recorder = null
            currentFile = null
            file.delete()
            message("Не удалось начать запись: ${error.message ?: "неизвестная ошибка"}")
        }
    }

    @Suppress("DEPRECATION")
    private fun newRecorder(file: File): MediaRecorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(getApplication()) else MediaRecorder()).apply {
        setAudioSource(MediaRecorder.AudioSource.MIC)
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        setAudioSamplingRate(44_100)
        setAudioEncodingBitRate(128_000)
        setOutputFile(file.absolutePath)
    }

    fun pauseRecording() = runCatching { recorder?.pause() }.onSuccess {
        if (isRecording) { isPaused = true; pauseStartedAt = System.currentTimeMillis() }
    }.onFailure { message("Не удалось поставить запись на паузу: ${it.message}") }

    fun resumeRecording() = runCatching { recorder?.resume() }.onSuccess {
        if (isRecording && isPaused) {
            startedAt += System.currentTimeMillis() - pauseStartedAt
            isPaused = false
        }
    }.onFailure { message("Не удалось возобновить запись: ${it.message}") }

    /** Останавливает контейнер до освобождения recorder: иначе M4A не будет пригоден к воспроизведению. */
    fun stopRecording(context: Context) {
        if (!isRecording) return
        ticker?.cancel()
        val file = currentFile
        val stopped = runCatching { recorder?.stop() }.isSuccess
        recorder?.release()
        recorder = null
        currentFile = null
        isRecording = false
        isPaused = false
        recordingTime = 0
        audioLevel = 0f
        context.stopService(Intent(context, RecordingService::class.java))
        if (stopped && file != null && file.length() > 0L) {
            reload()
            message("Запись сохранена")
        } else {
            file?.delete()
            message("Запись слишком короткая или повреждена")
        }
    }

    fun togglePlayback(record: Recording) {
        if (playingId == record.id) return stopPlayback()
        stopPlayback()
        runCatching {
            MediaPlayer().apply {
                setDataSource(record.file.absolutePath)
                setOnCompletionListener { stopPlayback() }
                prepare()
                start()
            }
        }.onSuccess { player = it; playingId = record.id }
            .onFailure { message("Не удалось воспроизвести запись: ${it.message}") }
    }

    fun stopPlayback() { player?.runCatching { stop() }; player?.release(); player = null; playingId = null }
    fun delete(record: Recording) { if (playingId == record.id) stopPlayback(); if (record.file.delete()) reload() else message("Не удалось удалить файл") }
    fun deleteAll() { stopPlayback(); recordings.forEach { it.file.delete() }; reload() }

    /** Передаёт безопасный content:// URI вместо пути к локальному файлу. */
    fun share(context: Context, record: Recording) = runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", record.file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("audio/mp4").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Поделиться записью"))
    }.onFailure { message("Не удалось отправить запись: ${it.message}") }

    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isRecording) {
                if (!isPaused) {
                    recordingTime = System.currentTimeMillis() - startedAt
                    audioLevel = (recorder?.maxAmplitude ?: 0).coerceIn(0, 32_767) / 32_767f
                }
                kotlinx.coroutines.delay(75)
            }
        }
    }

    private fun reload() {
        recordings = directory?.listFiles { file -> file.extension.equals("m4a", ignoreCase = true) }
            ?.sortedByDescending(File::lastModified)
            ?.map { file -> Recording(file.absolutePath, file, file.nameWithoutExtension, SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(file.lastModified())), size(file.length()), duration(file)) }
            .orEmpty()
    }

    private fun duration(file: File): Long = runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
        }
    }.getOrDefault(0L)

    private fun size(bytes: Long): String = if (bytes < 1_048_576) String.format(Locale.US, "%.1f KB", bytes / 1024.0) else String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
    private fun message(text: String) { _messages.tryEmit(text) }

    override fun onCleared() {
        ticker?.cancel()
        if (isRecording) stopRecording(getApplication())
        stopPlayback()
    }
}
