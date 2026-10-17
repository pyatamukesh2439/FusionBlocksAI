package com.fusion.aicontroller

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.fusion.aicontroller.model.ControllerStatus
import com.fusion.aicontroller.service.OverlayService
import com.fusion.aicontroller.state.ControllerStateRepository
import com.fusion.aicontroller.ui.MainScreen
import com.fusion.aicontroller.ui.theme.FusionTheme

class MainActivity : ComponentActivity() {

    private var hasOverlayPermission by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            hasOverlayPermission = Settings.canDrawOverlays(this)
        } catch (_: Exception) {}

        setContent {
            FusionTheme {
                val status by ControllerStateRepository.status.collectAsState()
                val placements by ControllerStateRepository.placementsMade.collectAsState()
                val logs by ControllerStateRepository.logs.collectAsState()

                MainScreen(
                    status = status,
                    placements = placements,
                    logs = logs,
                    hasOverlayPermission = hasOverlayPermission,
                    onGrantOverlayClick = { requestOverlayPermission() },
                    onStartClick = { startOverlayService() },
                    onStopClick = { stopOverlayService() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            hasOverlayPermission = Settings.canDrawOverlays(this)
        } catch (_: Exception) {}
    }

    private fun requestOverlayPermission() {
        try {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open settings: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startOverlayService() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Please grant overlay permission first.", Toast.LENGTH_LONG).show()
            requestOverlayPermission()
            return
        }

        try {
            val serviceIntent = Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Could not start service: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopOverlayService() {
        try {
            val stopIntent = Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_STOP
            }
            startService(stopIntent)
        } catch (_: Exception) {}
    }
}
