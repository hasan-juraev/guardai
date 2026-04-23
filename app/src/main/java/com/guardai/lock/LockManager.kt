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
        if (expiry <= now && expiry != 0L) {
            prefs.edit().remove(packageName).apply()
            return false
        }
        return expiry > now
    }

    fun expiresAt(packageName: String): Long? {
        val expiry = prefs.getLong(packageName, 0L)
        return expiry.takeIf { it > 0L }
    }

    fun unlockAll() {
        prefs.edit().clear().apply()
    }
}
