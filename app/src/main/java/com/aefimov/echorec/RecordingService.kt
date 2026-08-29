package com.aefimov.echorec

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/** Keeps the microphone recording visible and less likely to be killed in background. */
class RecordingService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() { super.onCreate(); if (Build.VERSION.SDK_INT >= 26) (getSystemService(NotificationManager::class.java)).createNotificationChannel(NotificationChannel(CHANNEL, "Запись звонков", NotificationManager.IMPORTANCE_LOW)) }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int { startForeground(ID, NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_stat_recording).setContentTitle("EchoRec").setContentText("Идёт запись с микрофона").setOngoing(true).build()); return START_NOT_STICKY }
    companion object { const val CHANNEL = "recording"; const val ID = 1001 }
}
