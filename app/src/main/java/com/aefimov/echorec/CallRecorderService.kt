package com.aefimov.echorec

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.aefimov.echorec.CallRecorderService.Companion.NOTIFICATION_ID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Фоновый сервис, который слушает состояние телефона и автоматически
 * запускает/останавливает запись при начале/окончании звонка.
 * Для записи используется микрофон с принудительным включением громкой связи.
 */
class CallRecorderService : LifecycleService() {

    private lateinit var telephonyManager: TelephonyManager
    private lateinit var audioManager: AudioManager
    private var wasSpeakerphoneOn = false
    private var isInCall = false

    companion object {
        private val _callState = MutableStateFlow(CallState.IDLE)
        val callState: StateFlow<CallState> = _callState

        fun start(context: Context) {
            context.startForegroundService(Intent(context, CallRecorderService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CallRecorderService::class.java))
        }
    }

    enum class CallState { IDLE, RINGING, OFFHOOK }

    override fun onCreate() {
        super.onCreate()
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Ожидание звонка"))

        // Регистрируем слушатель состояния телефона
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    override fun onDestroy() {
        super.onDestroy()
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
        // Возвращаем громкую связь в исходное состояние, если она была изменена
        if (isInCall) {
            audioManager.speakerphoneOn = wasSpeakerphoneOn
        }
        _callState.value = CallState.IDLE
    }

    private val phoneStateListener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            when (state) {
                TelephonyManager.CALL_STATE_IDLE -> {
                    _callState.value = CallState.IDLE
                    if (isInCall) {
                        // Звонок завершён – останавливаем запись
                        isInCall = false
                        audioManager.speakerphoneOn = wasSpeakerphoneOn
                        RecorderViewModel.stopRecordingIfActive(applicationContext)
                        updateNotification("Ожидание звонка")
                    }
                }
                TelephonyManager.CALL_STATE_RINGING -> {
                    _callState.value = CallState.RINGING
                    updateNotification("Входящий звонок...")
                }
                TelephonyManager.CALL_STATE_OFFHOOK -> {
                    _callState.value = CallState.OFFHOOK
                    if (!isInCall) {
                        // Начало разговора – запускаем запись
                        isInCall = true
                        wasSpeakerphoneOn = audioManager.isSpeakerphoneOn
                        // Включаем громкую связь для лучшей записи собеседника
                        audioManager.speakerphoneOn = true
                        RecorderViewModel.startCallRecording(applicationContext)
                        updateNotification("Идёт запись звонка")
                    }
                }
            }
        }
    }

    private fun updateNotification(text: String) {
        val notification = buildNotification(text)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("EchoRec")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_recording)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Служба записи звонков",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "call_recorder_channel"
        private const val NOTIFICATION_ID = 1002
    }
}