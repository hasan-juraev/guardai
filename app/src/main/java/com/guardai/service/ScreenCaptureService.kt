package com.guardai.service

import android.app.Notification
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
        val result = classifier.classify(bitmap)
        if (result == null) {
            if (capturedFrames == 1L || capturedFrames % METRICS_UPDATE_INTERVAL == 0L) {
                CaptureSessionState.updateCaptureMetrics(
                    capturedFrameCount = capturedFrames,
                    captureResolution = "${bitmap.width}x${bitmap.height}",
                    statusMessage = "Frame capture active. Model file not loaded yet.",
                )
            }
            return
        }

        when (
            detectionCoordinator.handleClassification(
                packageName = CurrentAppTracker.currentPackage.value,
                result = result,
            )
        ) {
            is DetectionOutcome.NoAction -> Unit
            is DetectionOutcome.Blurred -> {
                CaptureSessionState.updateCaptureMetrics(
                    capturedFrameCount = capturedFrames,
                    captureResolution = "${bitmap.width}x${bitmap.height}",
                    statusMessage = "Blur triggered at ${(result.confidence * 100).toInt()}% confidence.",
                )
            }
            is DetectionOutcome.Locked -> {
                CaptureSessionState.updateCaptureMetrics(
                    capturedFrameCount = capturedFrames,
                    captureResolution = "${bitmap.width}x${bitmap.height}",
                    statusMessage = "Package locked after ${(result.confidence * 100).toInt()}% confidence.",
                )
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
    }

    private fun currentDisplayMetrics(): android.util.DisplayMetrics {
        val metrics = android.util.DisplayMetrics()
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return metrics
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, ScreenCaptureNotification.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_text))
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val ACTION_START = "com.guardai.action.START_CAPTURE"
        private const val ACTION_STOP = "com.guardai.action.STOP_CAPTURE"
        private const val EXTRA_RESULT_CODE = "extra_result_code"
        private const val EXTRA_RESULT_DATA = "extra_result_data"
        private const val SAMPLE_INTERVAL_MS = 250L
        private const val METRICS_UPDATE_INTERVAL = 10L

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
