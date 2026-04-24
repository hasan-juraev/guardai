package com.guardai.detection

import android.content.Context
import com.guardai.accessibility.AccessibilityEjector
import com.guardai.accessibility.EjectResult
import com.guardai.ai.DetectionAction
import com.guardai.ai.DetectionSummary
import com.guardai.lock.LockManager
import com.guardai.lock.LockConfig
import com.guardai.overlay.OverlayManager

class DetectionCoordinator(context: Context) {

    private val overlayManager = OverlayManager(context)
    private val lockManager = LockManager(context)

    fun handleClassification(
        packageName: String?,
        result: DetectionSummary,
        lockDurationMs: Long = LockConfig.DEFAULT_LOCK_DURATION_MS,
    ): DetectionOutcome {
        if (result.action == DetectionAction.NONE) {
            overlayManager.hide()
            return DetectionOutcome.NoAction
        }

        overlayManager.show(
            if (result.matchedLabels.isNotEmpty()) {
                "Detected ${result.matchedLabels.joinToString { it.name }}"
            } else {
                "Explicit content detected"
            },
        )

        if (result.action == DetectionAction.LOCK && !packageName.isNullOrBlank()) {
            lockManager.lockPackage(
                packageName = packageName,
                expiresAt = System.currentTimeMillis() + lockDurationMs,
            )
            val ejectResult = AccessibilityEjector.eject()
            return DetectionOutcome.Locked(packageName, result.confidence, ejectResult)
        }

        return DetectionOutcome.Blurred(result.confidence)
    }

    fun clearOverlay() {
        overlayManager.hide()
    }
}

sealed interface DetectionOutcome {
    data object NoAction : DetectionOutcome
    data class Blurred(val confidence: Float) : DetectionOutcome
    data class Locked(
        val packageName: String,
        val confidence: Float,
        val ejectResult: EjectResult,
    ) : DetectionOutcome
}
