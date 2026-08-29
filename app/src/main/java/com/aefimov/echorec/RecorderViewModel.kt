package com.aefimov.echorec

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.app.Application
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Recording(val id: String, val file: File, val name: String, val date: String, val formattedSize: String, val duration: Long)

/** Owns MediaRecorder/MediaPlayer so a configuration change does not leak either object. */
class RecorderViewModel(application: Application) : AndroidViewModel(application) {
    private val directory get() = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_MUSIC)
    var recordings by androidx.compose.runtime.mutableStateOf<List<Recording>>(emptyList()); private set
    var isRecording by androidx.compose.runtime.mutableStateOf(false); private set
    var isPaused by androidx.compose.runtime.mutableStateOf(false); private set
    var recordingTime by androidx.compose.runtime.mutableLongStateOf(0L); private set
    var audioLevel by androidx.compose.runtime.mutableFloatStateOf(0f); private set
    var message by androidx.compose.runtime.mutableStateOf<String?>(null); private set
    var playingId by androidx.compose.runtime.mutableStateOf<String?>(null); private set

    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var currentFile: File? = null
    private var startedAt = 0L
    private var pauseStartedAt = 0L
    private var ticker: Job? = null

    init { reload() }

    /** Starts AAC/M4A recording. VOICE_CALL needs a separate privileged capture service; Shizuku alone is not that service. */
    fun startRecording(context: Context, shizukuGranted: Boolean) {
        if (isRecording) return
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            notify("Нужно разрешение на запись аудио")
            return
        }
        val outputDir = directory ?: run { notify("Не удалось открыть папку для записей"); return }
        if (!outputDir.exists() && !outputDir.mkdirs()) { notify("Не удалось создать папку для записей"); return }
        val file = File(outputDir, "Call_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.m4a")
        try {
            // A normal app is deliberately limited to MIC on Android 10+. Shizuku permission only permits Binder IPC.
            val source = MediaRecorder.AudioSource.MIC
            recorder = createRecorder(source, file).also { it.prepare(); it.start() }
            currentFile = file
            isRecording = true; isPaused = false; recordingTime = 0; startedAt = System.currentTimeMillis()
            ContextCompat.startForegroundService(context, Intent(context, RecordingService::class.java))
            startTicker()
            if (shizukuGranted) notify("Shizuku подключён. Для VOICE_CALL нужен отдельный привилегированный сервис захвата; записывается микрофон.")
        } catch (error: Exception) {
            recorder?.release(); recorder = null; file.delete()
            notify("Не удалось начать запись: ${error.message ?: "неизвестная ошибка"}")
        }
    }

    @Suppress("DEPRECATION")
    private fun createRecorder(source: Int, file: File): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(getApplication()) else MediaRecorder()
    .apply { setAudioSource(source); setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); setAudioEncoder(MediaRecorder.AudioEncoder.AAC); setAudioSamplingRate(44_100); setAudioEncodingBitRate(128_000); setOutputFile(file.absolutePath) }

    fun pauseRecording() = runCatching { recorder?.pause() }.onSuccess {
        if (isRecording) { isPaused = true; pauseStartedAt = System.currentTimeMillis() }
    }.onFailure { notify("Не удалось поставить запись на паузу: ${it.message}") }

    fun resumeRecording() = runCatching { recorder?.resume() }.onSuccess {
        if (isRecording && isPaused) { startedAt += System.currentTimeMillis() - pauseStartedAt; isPaused = false }
    }.onFailure { notify("Не удалось возобновить запись: ${it.message}") }

    fun stopRecording(context: Context) {
        if (!isRecording) return
        ticker?.cancel(); audioLevel = 0f
        val file = currentFile
        runCatching { recorder?.stop() }.onFailure { notify("Запись слишком короткая или повреждена: ${it.message}") }
        recorder?.release(); recorder = null; currentFile = null
        isRecording = false; isPaused = false; recordingTime = 0
        context.stopService(Intent(context, RecordingService::class.java))
        if (file != null && file.length() > 0) { reload(); notify("Запись сохранена") } else file?.delete()
    }

    fun togglePlayback(record: Recording) {
        if (playingId == record.id) { stopPlayback(); return }
        stopPlayback()
        runCatching {
            MediaPlayer().apply { setDataSource(record.file.absolutePath); prepare(); setOnCompletionListener { stopPlayback() }; start() }
        }.onSuccess { player = it; playingId = record.id }.onFailure { notify("Не удалось воспроизвести запись: ${it.message}") }
    }
    fun stopPlayback() { player?.runCatching { stop() }; player?.release(); player = null; playingId = null }
    fun delete(record: Recording) { stopPlayback(); if (!record.file.delete()) notify("Не удалось удалить файл") else reload() }
    fun deleteAll() { recordings.forEach { it.file.delete() }; stopPlayback(); reload() }
    fun share(context: Context, record: Recording) = runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", record.file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("audio/mp4").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Поделиться записью"))
    }.onFailure { notify("Не удалось отправить запись: ${it.message}") }
    fun consumeMessage() { message = null }

    private fun startTicker() { ticker?.cancel(); ticker = viewModelScope.launch { while (isActive && isRecording) { if (!isPaused) { recordingTime = System.currentTimeMillis() - startedAt; audioLevel = (recorder?.maxAmplitude ?: 0).coerceIn(0, 32_767) / 32_767f }; delay(75) } } }
    private fun reload() { recordings = directory?.listFiles { file -> file.extension.equals("m4a", true) }?.sortedByDescending { it.lastModified() }?.map { file -> Recording(file.absolutePath, file, file.nameWithoutExtension, SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(file.lastModified())), size(file.length()), duration(file)) }.orEmpty() }
    private fun duration(file: File): Long = runCatching { MediaMetadataRetriever().use { it.setDataSource(file.absolutePath); it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L } }.getOrDefault(0)
    private fun size(bytes: Long) = if (bytes < 1_048_576) String.format(Locale.US, "%.1f KB", bytes / 1024.0) else String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
    private fun notify(text: String) { message = text; Toast.makeText(getApplication(), text, Toast.LENGTH_LONG).show() }
    override fun onCleared() { ticker?.cancel(); recorder?.release(); stopPlayback() }
}
