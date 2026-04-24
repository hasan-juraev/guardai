package com.guardai

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.guardai.lock.LockConfig
import com.guardai.lock.LockManager
import com.guardai.service.ScreenCaptureService
import com.guardai.session.CaptureSessionState
import com.guardai.tracking.CurrentAppTracker
import com.guardai.ui.setup.SetupScreen
import com.guardai.ui.theme.GuardAITheme
import com.guardai.util.PermissionHelper

class MainActivity : ComponentActivity() {
    private val lockManager by lazy { LockManager(this) }

    private val projectionManager by lazy {
        getSystemService(MediaProjectionManager::class.java)
    }

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            ScreenCaptureService.startWithProjection(
                context = this,
                resultCode = result.resultCode,
                data = result.data!!,
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            GuardAITheme {
                val sessionState by CaptureSessionState.state.collectAsState()

                SetupScreen(
                    overlayGranted = sessionState.overlayGranted,
                    accessibilityGranted = sessionState.accessibilityGranted,
                    captureActive = sessionState.isRunning,
                    statusMessage = sessionState.statusMessage,
                    diagnostics = sessionState.diagnostics,
                    lastEjectResult = sessionState.lastEjectResult,
                    capturedFrameCount = sessionState.capturedFrameCount,
                    captureResolution = sessionState.captureResolution,
                    currentPackageName = sessionState.currentPackageName,
                    onRequestOverlay = ::openOverlaySettings,
                    onRequestAccessibility = ::openAccessibilitySettings,
                    onRequestProjection = ::requestProjection,
                    onStopProtection = { ScreenCaptureService.stop(this) },
                    onLockCurrentApp = ::lockCurrentAppForDebug,
                    onUnlockAll = ::unlockAll,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        CaptureSessionState.refreshPermissions(
            overlayGranted = PermissionHelper.hasOverlay(this),
            accessibilityGranted = PermissionHelper.hasAccessibility(this),
        )
    }

    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName"),
        )
        startActivity(intent)
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun requestProjection() {
        projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun lockCurrentAppForDebug() {
        val packageName = CurrentAppTracker.currentPackage.value
        if (packageName.isNullOrBlank()) {
            CaptureSessionState.updateStatus("No foreground app tracked yet. Open the target app first.")
            return
        }

        if (packageName == packageName()) {
            CaptureSessionState.updateStatus("GuardAI cannot lock itself. Open another app first.")
            return
        }

        lockManager.lockPackage(
            packageName = packageName,
            expiresAt = System.currentTimeMillis() + LockConfig.DEFAULT_LOCK_DURATION_MS,
        )
        CaptureSessionState.updateStatus("Locked $packageName for debug. Reopen it to test re-blocking.")
    }

    private fun unlockAll() {
        lockManager.unlockAll()
        CaptureSessionState.updateStatus("All app locks cleared.")
    }

    private fun packageName(): String = applicationContext.packageName
}
