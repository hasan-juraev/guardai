package com.guardai.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import com.guardai.metrics.MetricsLogger

class OverlayManager(context: Context) {

    private val appContext = context.applicationContext
    private val windowManager =
        appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var overlayView: View? = null

    fun show(message: String = "Content blocked") {
        mainHandler.post {
            if (overlayView != null) return@post

            val view = FrameLayout(appContext).apply {
                setBackgroundColor(0xFF000000.toInt())
            }

            windowManager.addView(view, layoutParams())
            overlayView = view
            MetricsLogger.logEvent(
                event = "overlay_attached",
                fields = mapOf("message" to message),
            )
        }
    }

    fun hide() {
        mainHandler.post {
            val view = overlayView ?: return@post
            windowManager.removeView(view)
            overlayView = null
            MetricsLogger.logEvent(event = "overlay_hidden")
        }
    }

    private fun layoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
        }
    }
}
