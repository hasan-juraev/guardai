package com.guardai.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.guardai.accessibility.AccessibilityEjector
import com.guardai.lock.LockConfig
import com.guardai.lock.LockManager
import com.guardai.overlay.OverlayManager
import com.guardai.session.CaptureSessionState
import com.guardai.tracking.CurrentAppTracker

class GuardAccessibilityService : AccessibilityService() {
    private lateinit var lockManager: LockManager
    private lateinit var overlayManager: OverlayManager
    private var lastEjectTimestamp = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        lockManager = LockManager(applicationContext)
        overlayManager = OverlayManager(applicationContext)
        AccessibilityEjector.register(this)
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = flags or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
        CaptureSessionState.refreshPermissions(
            overlayGranted = CaptureSessionState.state.value.overlayGranted,
            accessibilityGranted = true,
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val foregroundPackage = event?.packageName?.toString() ?: return
        if (foregroundPackage == packageName) return
        CurrentAppTracker.update(foregroundPackage)

        if (lockManager.isLocked(foregroundPackage)) {
            overlayManager.show("Blocked app")
            ejectIfNeeded()
        } else {
            overlayManager.hide()
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        AccessibilityEjector.unregister(this)
        overlayManager.hide()
        super.onDestroy()
    }

    private fun ejectIfNeeded() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastEjectTimestamp < LockConfig.EJECT_COOLDOWN_MS) return
        lastEjectTimestamp = now
        performGlobalAction(GLOBAL_ACTION_HOME)
    }
}
