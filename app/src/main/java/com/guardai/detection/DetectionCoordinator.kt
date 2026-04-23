package com.guardai.detection

import android.content.Context
import com.guardai.lock.LockManager
import com.guardai.overlay.OverlayManager
import com.guardai.ai.ClassificationResult
import com.guardai.ai.NsfwLabel

class DetectionCoordinator(context: Context) {

    private val overlayManager = OverlayManager(context)
    private val lockManager = LockManager(context)

    fun handleClassification(
        packageName: String?,
        result: ClassificationResult,
        lockDurationMs: Long = 10 * 60 * 1000L,
    ): DetectionOutcome {
        if (!shouldBlur(result)) {
            return DetectionOutcome.NoAction
        }

        overlayManager.show("Explicit content detected")

        if (shouldLock(result) && !packageName.isNullOrBlank()) {
            lockManager.lockPackage(
                packageName = packageName,
                expiresAt = System.currentTimeMillis() + lockDurationMs,
            )
            return DetectionOutcome.Locked(packageName, result.confidence)
        }

        return DetectionOutcome.Blurred(result.confidence)
    }

    fun clearOverlay() {
        overlayManager.hide()
    }

    private fun shouldBlur(result: ClassificationResult): Boolean {
        return when (result.label) {
            NsfwLabel.SEXY -> result.confidence >= BLUR_THRESHOLD
            NsfwLabel.PORN, NsfwLabel.HENTAI -> result.confidence >= LOCK_THRESHOLD
            else -> false
        }
    }

    private fun shouldLock(result: ClassificationResult): Boolean {
        return when (result.label) {
            NsfwLabel.PORN, NsfwLabel.HENTAI -> result.confidence >= LOCK_THRESHOLD
            else -> false
        }
    }

    companion object {
        const val BLUR_THRESHOLD = 0.70f
        const val LOCK_THRESHOLD = 0.85f
    }
}

sealed interface DetectionOutcome {
    data object NoAction : DetectionOutcome
    data class Blurred(val confidence: Float) : DetectionOutcome
    data class Locked(val packageName: String, val confidence: Float) : DetectionOutcome
}
