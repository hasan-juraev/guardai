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

    fun goHome(): Boolean {
        val service = serviceRef?.get() ?: return false
        return service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    }
}
