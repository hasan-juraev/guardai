package com.guardai.lock

object LockConfig {
    const val DEFAULT_LOCK_DURATION_MS = 10 * 60 * 1000L
    const val EJECT_COOLDOWN_MS = 1_000L
    const val EJECT_RETRY_WINDOW_MS = 5_000L
    const val FRAME_STALL_TIMEOUT_MS = 3_000L
    const val WATCHDOG_INTERVAL_MS = 1_500L
}
