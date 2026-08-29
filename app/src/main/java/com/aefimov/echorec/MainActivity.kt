package com.aefimov.echorec

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.aefimov.echorec.ui.theme.EchoRecTheme

/** Точка входа: запрашивает только те разрешения, которые действительно нужны записи. */
class MainActivity : ComponentActivity() {
    private var microphoneGranted by mutableStateOf(false)

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        // Повторно читаем состояние: пользователь мог разрешить только часть запроса.
        updatePermissionState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updatePermissionState()
        setContent {
            EchoRecTheme {
                RecorderApp(
                    microphoneGranted = microphoneGranted,
                    requestPermissions = ::requestRuntimePermissions,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionState()
    }

    private fun updatePermissionState() {
        microphoneGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestRuntimePermissions() {
        val permissions = buildList {
            if (!microphoneGranted) add(Manifest.permission.RECORD_AUDIO)
            // На Android 13+ разрешение влияет на видимость уведомления foreground-сервиса.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (permissions.isNotEmpty()) permissionsLauncher.launch(permissions.toTypedArray())
    }
}
