package com.guardai.accessibility

import android.accessibilityservice.AccessibilityService
import java.lang.ref.WeakReference

object AccessibilityEjector {
    private var serviceRef: WeakReference<AccessibilityService>? = null

    fun register(service: AccessibilityService) {
        serviceRef = WeakReference(service)
    }

    fun unregister(service: AccessibilityService) {
        val current = serviceRef?.get()
        if (current === service) {
            serviceRef?.clear()
            serviceRef = null
        }
    }

    fun eject(): EjectResult {
        val service = serviceRef?.get() ?: return EjectResult(success = false, mode = "NO_SERVICE")
        if (service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)) {
            return EjectResult(success = true, mode = "HOME")
        }

        val backWorked = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        val homeWorked = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        return EjectResult(
            success = backWorked || homeWorked,
            mode = if (backWorked || homeWorked) "BACK_HOME" else "FAILED",
        )
    }
}

data class EjectResult(
    val success: Boolean,
    val mode: String,
)
