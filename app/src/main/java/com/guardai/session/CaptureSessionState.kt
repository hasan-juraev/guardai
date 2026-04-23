package com.guardai.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CaptureUiState(
    val isRunning: Boolean = false,
    val statusMessage: String = "Protection is idle.",
    val overlayGranted: Boolean = false,
    val accessibilityGranted: Boolean = false,
    val capturedFrameCount: Long = 0,
    val captureResolution: String = "",
    val currentPackageName: String = "",
)

object CaptureSessionState {
    private val mutableState = MutableStateFlow(CaptureUiState())
    val state = mutableState.asStateFlow()

    fun markStarted(message: String = "Projection session started.") {
        mutableState.value = mutableState.value.copy(
            isRunning = true,
            statusMessage = message,
            capturedFrameCount = 0,
        )
    }

    fun markStopped(message: String = "Protection is idle.") {
        mutableState.value = mutableState.value.copy(
            isRunning = false,
            statusMessage = message,
            capturedFrameCount = 0,
            captureResolution = "",
            currentPackageName = "",
        )
    }

    fun refreshPermissions(
        overlayGranted: Boolean,
        accessibilityGranted: Boolean,
    ) {
        mutableState.value = mutableState.value.copy(
            overlayGranted = overlayGranted,
            accessibilityGranted = accessibilityGranted,
        )
    }

    fun updateCaptureMetrics(
        capturedFrameCount: Long,
        captureResolution: String,
        statusMessage: String = mutableState.value.statusMessage,
    ) {
        mutableState.value = mutableState.value.copy(
            capturedFrameCount = capturedFrameCount,
            captureResolution = captureResolution,
            statusMessage = statusMessage,
        )
    }

    fun updateCurrentPackage(packageName: String?) {
        mutableState.value = mutableState.value.copy(
            currentPackageName = packageName.orEmpty(),
        )
    }

    fun updateStatus(message: String) {
        mutableState.value = mutableState.value.copy(
            statusMessage = message,
        )
    }
}
