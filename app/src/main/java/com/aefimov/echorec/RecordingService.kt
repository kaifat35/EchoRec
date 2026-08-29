package com.aefimov.echorec

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class RecordingService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Запись с микрофона",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_recording)
            .setContentTitle("EchoRec")
            .setContentText("Идёт запись с микрофона")
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL_ID = "recording_channel"
        private const val NOTIFICATION_ID = 1001
    }
}