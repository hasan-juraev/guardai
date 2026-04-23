package com.guardai.lock

import android.content.Context

class LockManager(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        "guardai_locks",
        Context.MODE_PRIVATE,
    )

    fun lockPackage(packageName: String, expiresAt: Long) {
        prefs.edit().putLong(packageName, expiresAt).apply()
    }

    fun isLocked(packageName: String, now: Long = System.currentTimeMillis()): Boolean {
        val expiry = prefs.getLong(packageName, 0L)
        return expiry > now
    }

    fun unlockAll() {
        prefs.edit().clear().apply()
    }
}
