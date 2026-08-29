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
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    private var shizukuState by mutableStateOf(ShizukuState.UNAVAILABLE)
    private val shizukuListener = Shizuku.OnBinderReceivedListener { refreshShizuku() }
    private val deadListener = Shizuku.OnBinderDeadListener { shizukuState = ShizukuState.UNAVAILABLE }
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, result -> shizukuState = if (result == PackageManager.PERMISSION_GRANTED) ShizukuState.GRANTED else ShizukuState.DENIED }
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addBinderReceivedListener(shizukuListener); Shizuku.addBinderDeadListener(deadListener); Shizuku.addRequestPermissionResultListener(permissionListener)
        refreshShizuku(); requestRuntimePermissions()
        setContent { EchoRecTheme { RecorderApp(shizukuState, ::requestRuntimePermissions, ::requestShizukuPermission) } }
    }
    override fun onDestroy() { Shizuku.removeBinderReceivedListener(shizukuListener); Shizuku.removeBinderDeadListener(deadListener); Shizuku.removeRequestPermissionResultListener(permissionListener); super.onDestroy() }
    private fun refreshShizuku() { shizukuState = runCatching { if (!Shizuku.pingBinder()) ShizukuState.UNAVAILABLE else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) ShizukuState.GRANTED else ShizukuState.CONNECTED }.getOrDefault(ShizukuState.UNAVAILABLE) }
    private fun requestShizukuPermission() { if (shizukuState == ShizukuState.UNAVAILABLE) return; runCatching { Shizuku.requestPermission(SHIZUKU_REQUEST) }.onFailure { shizukuState = ShizukuState.DENIED } }
    private fun requestRuntimePermissions() { val wanted = buildList { if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.RECORD_AUDIO); if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.POST_NOTIFICATIONS) }; if (wanted.isNotEmpty()) permissions.launch(wanted.toTypedArray()) }
    private companion object { const val SHIZUKU_REQUEST = 741 }
}
enum class ShizukuState { UNAVAILABLE, CONNECTED, GRANTED, DENIED }
