package com.guardai

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class GuardAIApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            ScreenCaptureNotification.CHANNEL_ID,
            getString(R.string.service_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.service_channel_description)
        }

        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
