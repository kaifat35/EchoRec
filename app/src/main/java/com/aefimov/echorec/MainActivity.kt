package com.aefimov.echorec

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.aefimov.echorec.ui.theme.EchoRecTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    private val permissionRequestCode = 100
    private var isShizukuReady by mutableStateOf(false)
    private var shizukuStatus by mutableStateOf("Проверка...")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkPermissions()
        initShizuku()

        setContent {
            EchoRecTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RecorderApp(
                        isShizukuReady = isShizukuReady,
                        shizukuStatus = shizukuStatus,
                        onRequestPermissions = { checkPermissions() },
                        onStartShizuku = { startShizukuService() }
                    )
                }
            }
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissions.toTypedArray(),
                permissionRequestCode
            )
        }
    }

    private fun initShizuku() {
        // Проверяем, запущен ли Shizuku
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (Shizuku.pingBinder()) {
                    isShizukuReady = true
                    shizukuStatus = "✅ Shizuku активен"
                    checkShizukuPermission()
                } else {
                    isShizukuReady = false
                    shizukuStatus = "❌ Shizuku не запущен"
                }
            } catch (e: Exception) {
                isShizukuReady = false
                shizukuStatus = "❌ Ошибка: ${e.message}"
            }
        }

        // Слушаем изменения статуса Shizuku
        Shizuku.addBinderReceivedListener {
            isShizukuReady = true
            shizukuStatus = "✅ Shizuku подключен"
            checkShizukuPermission()
            runOnUiThread {
                Toast.makeText(this, "Shizuku успешно подключен!", Toast.LENGTH_SHORT).show()
            }
        }

        Shizuku.addBinderDeadListener {
            isShizukuReady = false
            shizukuStatus = "❌ Shizuku отключен"
            runOnUiThread {
                Toast.makeText(this, "Shizuku отключен!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkShizukuPermission() {
        try {
            if (Shizuku.pingBinder()) {
                val permission = Shizuku.checkSelfPermission()
                if (permission == PackageManager.PERMISSION_GRANTED) {
                    shizukuStatus = "✅ Shizuku готов к работе"
                } else {
                    shizukuStatus = "⚠️ Требуется разрешение Shizuku"
                    // Запрашиваем разрешение
                    Shizuku.requestPermission(0)
                }
            }
        } catch (e: Exception) {
            shizukuStatus = "❌ Ошибка: ${e.message}"
        }
    }

    private fun startShizukuService() {
        Toast.makeText(this, "Shizuku активируется...", Toast.LENGTH_SHORT).show()
        // Здесь можно добавить автоматический запуск через ADB или показать инструкцию
        shizukuStatus = "⚠️ Для активации Shizuku выполните: adb shell sh /data/local/tmp/start.sh"
    }
}