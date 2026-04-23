package com.guardai.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import com.guardai.session.CaptureSessionState
import com.guardai.tracking.CurrentAppTracker

class GuardAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
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
    }

    override fun onInterrupt() = Unit
}
