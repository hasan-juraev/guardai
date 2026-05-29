package com.guardai.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.guardai.lock.LockConfig
import com.guardai.metrics.MetricsLogger
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object AccessibilityEjector {
    private var serviceRef: WeakReference<AccessibilityService>? = null
    @Volatile private var pendingPackage: String? = null
    @Volatile private var pendingUntilElapsed: Long = 0L
    private const val MAIN_THREAD_TIMEOUT_MS = 300L
    private const val HOME_RETRY_DELAY_MS = 80L

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

    fun requestEject(packageName: String) {
        pendingPackage = packageName
        pendingUntilElapsed = SystemClock.elapsedRealtime() + LockConfig.EJECT_RETRY_WINDOW_MS
        MetricsLogger.logEvent(
            event = "eject_requested",
            fields = mapOf(
                "package" to packageName,
                "retry_window_ms" to LockConfig.EJECT_RETRY_WINDOW_MS,
            ),
        )
    }

    fun maybeEjectForeground(packageName: String): EjectResult? {
        val target = pendingPackage ?: return null
        if (SystemClock.elapsedRealtime() > pendingUntilElapsed) {
            MetricsLogger.logEvent(
                event = "eject_pending_expired",
                fields = mapOf("package" to target),
            )
            clearPending()
            return null
        }
        if (target != packageName) {
            return null
        }

        val result = eject()
        if (result.success) {
            clearPending()
        }
        return result
    }

    fun clearPending() {
        val target = pendingPackage
        pendingPackage = null
        pendingUntilElapsed = 0L
        if (target != null) {
            MetricsLogger.logEvent(
                event = "eject_pending_cleared",
                fields = mapOf("package" to target),
            )
        }
    }

    fun eject(): EjectResult {
        val service = serviceRef?.get() ?: return EjectResult(success = false, mode = "NO_SERVICE")
        val result = runOnServiceMainThread {
            if (service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)) {
                return@runOnServiceMainThread EjectResult(success = true, mode = "HOME")
            }

            val backWorked = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            if (backWorked) {
                SystemClock.sleep(HOME_RETRY_DELAY_MS)
            }
            val homeWorked = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            EjectResult(
                success = homeWorked,
                mode = if (homeWorked) "BACK_HOME" else if (backWorked) "BACK_ONLY" else "FAILED",
            )
        }
        MetricsLogger.logEvent(
            event = "eject_result",
            fields = mapOf(
                "success" to result.success,
                "mode" to result.mode,
            ),
        )
        return result
    }

    private fun runOnServiceMainThread(
        block: () -> EjectResult,
    ): EjectResult {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return block()
        }

        val latch = CountDownLatch(1)
        val resultHolder = arrayOfNulls<EjectResult>(1)
        Handler(Looper.getMainLooper()).post {
            resultHolder[0] = block()
            latch.countDown()
        }

        return if (latch.await(MAIN_THREAD_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            resultHolder[0] ?: EjectResult(success = false, mode = "NO_RESULT")
        } else {
            EjectResult(success = false, mode = "TIMEOUT")
        }
    }
}

data class EjectResult(
    val success: Boolean,
    val mode: String,
)
