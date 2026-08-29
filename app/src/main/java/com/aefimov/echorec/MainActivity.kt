package com.aefimov.echorec

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import android.util.Log
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"
    private var microphoneGranted by mutableStateOf(false)
    private var phoneStateGranted by mutableStateOf(false)
    private var shizukuGranted by mutableStateOf(false)

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        updatePermissionState()
        if (microphoneGranted && phoneStateGranted) {
            Log.d(TAG, "All permissions granted, starting service")
            CallRecorderService.start(this)
        }
    }

    // Shizuku listeners
    private val shizukuBinderListener = Shizuku.OnBinderReceivedListener {
        Log.d(TAG, "Shizuku binder received")
        checkShizukuPermission()
    }

    private val shizukuDeadListener = Shizuku.OnBinderDeadListener {
        Log.d(TAG, "Shizuku binder dead")
        shizukuGranted = false
    }

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { _, result ->
        shizukuGranted = result == PackageManager.PERMISSION_GRANTED
        Log.d(TAG, "Shizuku permission result: $shizukuGranted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addBinderReceivedListener(shizukuBinderListener)
        Shizuku.addBinderDeadListener(shizukuDeadListener)
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)

        updatePermissionState()
        checkShizukuPermission()

        setContent {
            EchoRecTheme {
                RecorderApp(
                    microphoneGranted = microphoneGranted,
                    phoneStateGranted = phoneStateGranted,
                    shizukuGranted = shizukuGranted,
                    requestPermissions = ::requestRuntimePermissions,
                    requestShizukuPermission = ::requestShizukuPermission,
                    showShizukuHelp = ::showShizukuHelpDialog,
                )
            }
        }

        if (microphoneGranted && phoneStateGranted) {
            CallRecorderService.start(this)
        } else {
            requestRuntimePermissions()
        }
    }

    override fun onResume() {
        super.onResume()
        val oldMic = microphoneGranted
        val oldPhone = phoneStateGranted
        updatePermissionState()
        checkShizukuPermission()
        if (microphoneGranted && phoneStateGranted && (!oldMic || !oldPhone)) {
            CallRecorderService.start(this)
        }
        if (!microphoneGranted || !phoneStateGranted) {
            CallRecorderService.stop(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeBinderReceivedListener(shizukuBinderListener)
        Shizuku.removeBinderDeadListener(shizukuDeadListener)
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
    }

    private fun updatePermissionState() {
        microphoneGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        phoneStateGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkShizukuPermission() {
        try {
            if (Shizuku.pingBinder()) {
                shizukuGranted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                Log.d(TAG, "Shizuku ping OK, permission: $shizukuGranted")
            } else {
                shizukuGranted = false
                Log.d(TAG, "Shizuku not running")
            }
        } catch (e: Exception) {
            shizukuGranted = false
            Log.e(TAG, "Shizuku error", e)
        }
    }

    private fun requestShizukuPermission() {
        try {
            if (Shizuku.pingBinder()) {
                Shizuku.requestPermission(741)
            } else {
                showShizukuHelpDialog()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request Shizuku permission", e)
            showShizukuHelpDialog()
        }
    }

    private fun showShizukuHelpDialog() {
        AlertDialog.Builder(this)
            .setTitle("Как активировать Shizuku")
            .setMessage("""
                Shizuku — это сервис, который позволяет приложению записывать голос собеседника.

                1. Скачайте Shizuku с GitHub (ссылка ниже).
                2. Установите APK.
                3. Включите «Беспроводную отладку» в настройках разработчика.
                4. Откройте Shizuku, нажмите «Pairing» и введите код с экрана.
                5. Нажмите «Start» и вернитесь в приложение.

                Это нужно сделать один раз — после перезагрузки телефона повторить шаги 4-5.
            """.trimIndent())
            .setPositiveButton("Открыть GitHub") { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/RikkaApps/Shizuku/releases")))
            }
            .setNegativeButton("Позже", null)
            .show()
    }

    private fun requestRuntimePermissions() {
        val permissions = buildList {
            if (!microphoneGranted) add(Manifest.permission.RECORD_AUDIO)
            if (!phoneStateGranted) add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (permissions.isNotEmpty()) {
            permissionsLauncher.launch(permissions.toTypedArray())
        } else {
            if (microphoneGranted && phoneStateGranted) {
                CallRecorderService.start(this)
            }
        }
    }
}