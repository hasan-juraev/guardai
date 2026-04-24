package com.guardai.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context.WINDOW_SERVICE
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.guardai.R
import com.guardai.ScreenCaptureNotification
import com.guardai.ai.DetectionAction
import com.guardai.ai.DetectionSummary
import com.guardai.ai.NsfwClassifier
import com.guardai.detection.DetectionCoordinator
import com.guardai.detection.DetectionOutcome
import com.guardai.session.CaptureSessionState
import com.guardai.tracking.CurrentAppTracker

class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null
    private var projectionCallback: MediaProjection.Callback? = null

    private var captureWidth = 0
    private var captureHeight = 0
    private var displayDensity = 0
    private var lastFrameTimestamp = 0L
    private var capturedFrames = 0L
    private var latestBitmap: Bitmap? = null
    private var blurFrameStreak = 0
    private var lockFrameStreak = 0
    private var blurHoldUntil = 0L
    private lateinit var classifier: NsfwClassifier
    private lateinit var detectionCoordinator: DetectionCoordinator

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startCapture(intent)
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        classifier = NsfwClassifier(applicationContext)
        detectionCoordinator = DetectionCoordinator(applicationContext)
        classifier.warmUp()
    }

    override fun onDestroy() {
        detectionCoordinator.clearOverlay()
        cleanupCaptureResources()
        mediaProjection?.let { projection ->
            projectionCallback?.let(projection::unregisterCallback)
            projection.stop()
        }
        mediaProjection = null
        projectionCallback = null
        CaptureSessionState.markStopped()
        super.onDestroy()
    }

    private fun startCapture(intent: Intent) {
        startForeground(ScreenCaptureNotification.NOTIFICATION_ID, buildNotification())

        if (mediaProjection != null) {
            CaptureSessionState.markStarted("Projection session already active.")
            return
        }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val projectionData = intent.parcelableIntent(EXTRA_RESULT_DATA)
        if (resultCode == 0 || projectionData == null) {
            CaptureSessionState.markStopped("Projection permission data was missing.")
            stopSelf()
            return
        }

        val manager = getSystemService(MediaProjectionManager::class.java)
        mediaProjection = manager.getMediaProjection(resultCode, projectionData)
        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                CaptureSessionState.markStopped("Projection session ended.")
                CaptureSessionState.updateDiagnostics("MediaProjection onStop callback fired.")
                stopSelf()
            }
        }
        projectionCallback = callback
        mediaProjection?.registerCallback(
            callback,
            null,
        )

        val metrics = currentDisplayMetrics()
        captureWidth = metrics.widthPixels
        captureHeight = metrics.heightPixels
        displayDensity = metrics.densityDpi

        CaptureSessionState.markStarted("Projection granted. Initializing frame capture.")
        startCapturePipeline()
    }

    private fun startCapturePipeline() {
        if (mediaProjection == null) {
            CaptureSessionState.markStopped("Projection session unavailable.")
            stopSelf()
            return
        }

        cleanupCaptureResources()

        captureThread = HandlerThread("guardai-capture").apply { start() }
        captureHandler = Handler(captureThread!!.looper)
        imageReader = ImageReader.newInstance(
            captureWidth,
            captureHeight,
            PixelFormat.RGBA_8888,
            2,
        ).apply {
            setOnImageAvailableListener({ reader ->
                processLatestImage(reader)
            }, captureHandler)
        }

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "GuardAI Capture",
            captureWidth,
            captureHeight,
            displayDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            captureHandler,
        )

        CaptureSessionState.updateCaptureMetrics(
            capturedFrameCount = 0,
            captureResolution = "${captureWidth}x${captureHeight}",
            statusMessage = "Frame capture active.",
        )
        CaptureSessionState.updateDiagnostics(
            "VirtualDisplay=${captureWidth}x${captureHeight} density=$displayDensity sample=${SAMPLE_INTERVAL_MS}ms",
        )
    }

    private fun processLatestImage(reader: ImageReader) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastFrameTimestamp < SAMPLE_INTERVAL_MS) {
            reader.acquireLatestImage()?.close()
            return
        }

        val image = reader.acquireLatestImage() ?: return
        try {
            val bitmap = image.toBitmap(reusable = latestBitmap)
            latestBitmap = bitmap
            lastFrameTimestamp = now
            capturedFrames += 1
            runInference(bitmap)

            if (capturedFrames == 1L || capturedFrames % METRICS_UPDATE_INTERVAL == 0L) {
                CaptureSessionState.updateCaptureMetrics(
                    capturedFrameCount = capturedFrames,
                    captureResolution = "${bitmap.width}x${bitmap.height}",
                    statusMessage = "Capturing frames for inference.",
                )
            }
        } finally {
            image.close()
        }
    }

    private fun runInference(bitmap: Bitmap) {
        val result: DetectionSummary = classifier.classify(bitmap) ?: run {
            if (capturedFrames == 1L || capturedFrames % METRICS_UPDATE_INTERVAL == 0L) {
                CaptureSessionState.updateCaptureMetrics(
                    capturedFrameCount = capturedFrames,
                    captureResolution = "${bitmap.width}x${bitmap.height}",
                    statusMessage = "Frame capture active. Model file not loaded yet.",
                )
            }
            return
        }

        val now = SystemClock.elapsedRealtime()
        updateTemporalState(result, now)
        val smoothedResult = result.toSmoothedResult(
            blurFrameStreak = blurFrameStreak,
            lockFrameStreak = lockFrameStreak,
        )
        if (smoothedResult.action == DetectionAction.NONE && now > blurHoldUntil) {
            detectionCoordinator.clearOverlay()
        }

        val outcome = detectionCoordinator.handleClassification(
            packageName = CurrentAppTracker.currentPackage.value,
            result = smoothedResult,
        )

        when (outcome) {
            is DetectionOutcome.NoAction -> Unit
            is DetectionOutcome.Blurred -> {
                blurHoldUntil = now + BLUR_HOLD_MS
                CaptureSessionState.updateCaptureMetrics(
                    capturedFrameCount = capturedFrames,
                    captureResolution = "${bitmap.width}x${bitmap.height}",
                    statusMessage = "Blur triggered by ${smoothedResult.topLabel.name} at ${(smoothedResult.confidence * 100).toInt()}%.",
                )
                CaptureSessionState.updateDiagnostics(
                    "streak blur=$blurFrameStreak lock=$lockFrameStreak labels=${smoothedResult.matchedLabels.joinToString()}",
                )
            }
            is DetectionOutcome.Locked -> {
                blurHoldUntil = now + BLUR_HOLD_MS
                CaptureSessionState.updateCaptureMetrics(
                    capturedFrameCount = capturedFrames,
                    captureResolution = "${bitmap.width}x${bitmap.height}",
                    statusMessage = "Package locked by ${smoothedResult.topLabel.name} at ${(smoothedResult.confidence * 100).toInt()}%.",
                )
                CaptureSessionState.updateDiagnostics(
                    "lock streak=$lockFrameStreak eject=${outcome.ejectResult.mode} success=${outcome.ejectResult.success}",
                )
            }
        }
    }

    private fun updateTemporalState(result: DetectionSummary, now: Long) {
        when (result.action) {
            DetectionAction.LOCK -> {
                lockFrameStreak += 1
                blurFrameStreak += 1
                blurHoldUntil = now + BLUR_HOLD_MS
            }
            DetectionAction.BLUR -> {
                blurFrameStreak += 1
                lockFrameStreak = 0
                blurHoldUntil = now + BLUR_HOLD_MS
            }
            DetectionAction.NONE -> {
                if (now > blurHoldUntil) {
                    blurFrameStreak = 0
                }
                lockFrameStreak = 0
            }
        }
    }

    private fun cleanupCaptureResources() {
        imageReader?.setOnImageAvailableListener(null, null)
        imageReader?.close()
        imageReader = null

        virtualDisplay?.release()
        virtualDisplay = null

        latestBitmap?.recycle()
        latestBitmap = null

        captureHandler = null
        captureThread?.quitSafely()
        captureThread = null

        capturedFrames = 0
        lastFrameTimestamp = 0L
        blurFrameStreak = 0
        lockFrameStreak = 0
        blurHoldUntil = 0L
    }

    private fun currentDisplayMetrics(): android.util.DisplayMetrics {
        val metrics = android.util.DisplayMetrics()
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return metrics
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, ScreenCaptureNotification.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_text))
            .addAction(
                0,
                getString(R.string.service_notification_action_stop),
                stopPendingIntent,
            )
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val ACTION_START = "com.guardai.action.START_CAPTURE"
        private const val ACTION_STOP = "com.guardai.action.STOP_CAPTURE"
        private const val EXTRA_RESULT_CODE = "extra_result_code"
        private const val EXTRA_RESULT_DATA = "extra_result_data"
        private const val SAMPLE_INTERVAL_MS = 125L
        private const val METRICS_UPDATE_INTERVAL = 10L
        private const val BLUR_HOLD_MS = 1_500L
        private const val BLUR_TRIGGER_FRAMES = 1
        private const val LOCK_TRIGGER_FRAMES = 2

        fun startWithProjection(
            context: Context,
            resultCode: Int,
            data: Intent,
        ) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}

private fun DetectionSummary.toSmoothedResult(
    blurFrameStreak: Int,
    lockFrameStreak: Int,
): DetectionSummary {
    val smoothedAction = when {
        action == DetectionAction.LOCK && lockFrameStreak >= 2 -> DetectionAction.LOCK
        action == DetectionAction.BLUR && blurFrameStreak >= 1 -> DetectionAction.BLUR
        action == DetectionAction.LOCK && blurFrameStreak >= 1 -> DetectionAction.BLUR
        else -> DetectionAction.NONE
    }
    return copy(action = smoothedAction)
}

private fun Image.toBitmap(reusable: Bitmap?): Bitmap {
    val plane = planes.first()
    val buffer = plane.buffer
    val pixelStride = plane.pixelStride
    val rowStride = plane.rowStride
    val rowPadding = rowStride - pixelStride * width
    val bitmapWidth = width + rowPadding / pixelStride
    val bitmap = reusable?.takeIf {
        it.width == bitmapWidth && it.height == height && !it.isRecycled
    } ?: Bitmap.createBitmap(bitmapWidth, height, Bitmap.Config.ARGB_8888)

    buffer.rewind()
    bitmap.copyPixelsFromBuffer(buffer)
    return if (bitmapWidth == width) {
        bitmap
    } else {
        Bitmap.createBitmap(bitmap, 0, 0, width, height)
    }
}

private fun Intent.parcelableIntent(key: String): Intent? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(key, Intent::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(key)
    }
}
