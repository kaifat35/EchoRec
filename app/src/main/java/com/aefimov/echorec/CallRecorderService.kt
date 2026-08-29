package com.aefimov.echorec

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import rikka.shizuku.Shizuku

class CallRecorderService : LifecycleService() {
    private val TAG = "CallRecorderService"
    private lateinit var telephonyManager: TelephonyManager
    private lateinit var audioManager: AudioManager
    private var wasSpeakerphoneOn = false
    private var isInCall = false

    companion object {
        private const val CHANNEL_ID = "call_recorder_channel"
        private const val NOTIFICATION_ID = 1002
        private val _callState = MutableStateFlow(CallState.IDLE)
        val callState: StateFlow<CallState> = _callState

        fun start(context: Context) {
            Log.d("CallRecorderService", "start() called")
            context.startForegroundService(Intent(context, CallRecorderService::class.java))
        }

        fun stop(context: Context) {
            Log.d("CallRecorderService", "stop() called")
            context.stopService(Intent(context, CallRecorderService::class.java))
        }
    }

    enum class CallState { IDLE, RINGING, OFFHOOK }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Ожидание звонка"))

        try {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
            Log.d(TAG, "PhoneStateListener registered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register PhoneStateListener", e)
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        super.onDestroy()
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
        if (isInCall) {
            try {
                audioManager.setSpeakerphoneOn(wasSpeakerphoneOn)
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring speakerphone", e)
            }
        }
        _callState.value = CallState.IDLE
    }

    private val phoneStateListener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            Log.d(TAG, "onCallStateChanged: state=$state, phoneNumber=$phoneNumber")
            when (state) {
                TelephonyManager.CALL_STATE_IDLE -> {
                    _callState.value = CallState.IDLE
                    if (isInCall) {
                        isInCall = false
                        try {
                            audioManager.setSpeakerphoneOn(wasSpeakerphoneOn)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error restoring speakerphone", e)
                        }
                        RecorderViewModel.stopRecordingIfActive(applicationContext)
                        updateNotification("Ожидание звонка")
                        Log.d(TAG, "Call ended, recording stopped")
                    }
                }
                TelephonyManager.CALL_STATE_RINGING -> {
                    _callState.value = CallState.RINGING
                    updateNotification("Входящий звонок...")
                    Log.d(TAG, "Ringing")
                }
                TelephonyManager.CALL_STATE_OFFHOOK -> {
                    _callState.value = CallState.OFFHOOK
                    if (!isInCall) {
                        isInCall = true
                        wasSpeakerphoneOn = audioManager.isSpeakerphoneOn
                        try {
                            audioManager.setSpeakerphoneOn(true)
                            Log.d(TAG, "Speakerphone forced on")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error enabling speakerphone", e)
                        }

                        // Проверяем доступность Shizuku
                        val useShizuku = Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                        Log.d(TAG, "Shizuku available: $useShizuku")
                        RecorderViewModel.startCallRecording(applicationContext, useShizuku)

                        updateNotification("Идёт запись звонка")
                        Log.d(TAG, "Call started, recording started")
                    }
                }
            }
        }
    }

    private fun updateNotification(text: String) {
        val notification = buildNotification(text)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update notification", e)
        }
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
            try {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Служба записи звонков",
                    NotificationManager.IMPORTANCE_LOW
                )
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.createNotificationChannel(channel)
                Log.d(TAG, "Notification channel created")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create notification channel", e)
            }
        }
    }
}