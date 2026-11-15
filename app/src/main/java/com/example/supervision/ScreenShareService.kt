package com.example.supervision

import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
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
import android.os.IBinder
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.WindowManager
import com.example.R
import com.example.data.repository.FocusGardenRepository
import com.example.notifications.FocusNotificationHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

class ScreenShareService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureJob: Job? = null
    private var projectionCallback: MediaProjection.Callback? = null

    private var captureWidth: Int = 360
    private var captureHeight: Int = 720
    private var densityDpi: Int = 160
    private var frameSequence: Long = 0L

    private lateinit var notifier: FocusNotificationHelper
    private lateinit var repository: FocusGardenRepository

    override fun onCreate() {
        super.onCreate()
        notifier = FocusNotificationHelper(applicationContext)
        val dbId = getString(R.string.firestore_database_id)
        repository = FocusGardenRepository(FirebaseFirestore.getInstance(dbId), Firebase.auth)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSharingAndCleanUp()
            return START_NOT_STICKY
        }

        val currentUser = Firebase.auth.currentUser
        if (currentUser == null) {
            stopSelfCleanly()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        val projectionData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        if (resultCode != Activity.RESULT_OK || projectionData == null) {
            stopSelfCleanly()
            return START_NOT_STICKY
        }

        val notification = notifier.buildScreenShareForegroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                FocusNotificationHelper.NOTIF_ID_SCREEN_SHARE_FOREGROUND,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(FocusNotificationHelper.NOTIF_ID_SCREEN_SHARE_FOREGROUND, notification)
        }

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        val projection = try {
            projectionManager?.getMediaProjection(resultCode, projectionData)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to obtain MediaProjection: ${e.message}", e)
            null
        }
        if (projection == null) {
            stopSelfCleanly()
            return START_NOT_STICKY
        }

        tearDownProjectionObjects()
        mediaProjection = projection
        _isSharing.value = true

        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                mainHandler.post { stopSharingAndCleanUp() }
            }

            override fun onCapturedContentResize(width: Int, height: Int) {
                if (width > 0 && height > 0) {
                    resizeCaptureSurface(width, height)
                }
            }
        }
        projectionCallback = callback
        projection.registerCallback(callback, mainHandler)

        val (rawW, rawH, dpi) = computeDisplayMetrics()
        densityDpi = dpi
        setupVirtualDisplay(projection, rawW, rawH, dpi)
        startCaptureLoop()

        return START_NOT_STICKY
    }

    private fun computeDisplayMetrics(): Triple<Int, Int, Int> {
        val dpi = resources.configuration.densityDpi.coerceAtLeast(120)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val wm = getSystemService(WindowManager::class.java)
            val bounds = wm.maximumWindowMetrics.bounds
            Triple(bounds.width().coerceAtLeast(360), bounds.height().coerceAtLeast(640), dpi)
        } else {
            val dm = resources.displayMetrics
            Triple(dm.widthPixels.coerceAtLeast(360), dm.heightPixels.coerceAtLeast(640), dpi)
        }
    }

    private fun computeScaledSize(rawW: Int, rawH: Int): Pair<Int, Int> {
        val maxSide = max(rawW, rawH).coerceAtLeast(1)
        val scale = (MAX_CAPTURE_DIMENSION.toFloat() / maxSide.toFloat()).coerceAtMost(1f)
        val w = ((rawW * scale).roundToInt()).coerceAtLeast(180)
        val h = ((rawH * scale).roundToInt()).coerceAtLeast(180)
        // Align width/height to even numbers for surface compatibility
        return (w and 1.inv()).coerceAtLeast(180) to (h and 1.inv()).coerceAtLeast(180)
    }

    private fun setupVirtualDisplay(projection: MediaProjection, rawW: Int, rawH: Int, dpi: Int) {
        val (scaledW, scaledH) = computeScaledSize(rawW, rawH)
        captureWidth = scaledW
        captureHeight = scaledH
        imageReader?.close()
        val reader = ImageReader.newInstance(scaledW, scaledH, PixelFormat.RGBA_8888, 2)
        imageReader = reader
        virtualDisplay?.release()
        virtualDisplay = projection.createVirtualDisplay(
            VIRTUAL_DISPLAY_NAME,
            scaledW,
            scaledH,
            dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            mainHandler
        )
    }

    private fun resizeCaptureSurface(rawW: Int, rawH: Int) {
        val (scaledW, scaledH) = computeScaledSize(rawW, rawH)
        if (scaledW == captureWidth && scaledH == captureHeight) return
        captureWidth = scaledW
        captureHeight = scaledH
        try {
            val newReader = ImageReader.newInstance(scaledW, scaledH, PixelFormat.RGBA_8888, 2)
            virtualDisplay?.resize(scaledW, scaledH, densityDpi)
            virtualDisplay?.setSurface(newReader.surface)
            imageReader?.close()
            imageReader = newReader
        } catch (e: Exception) {
            Log.w(TAG, "Resize capture surface failed: ${e.message}")
        }
    }

    private fun startCaptureLoop() {
        captureJob?.cancel()
        captureJob = serviceScope.launch {
            while (isActive && _isSharing.value) {
                try {
                    val encoded = captureLatestFrameBase64()
                    if (encoded != null) {
                        frameSequence = (frameSequence + 1L).coerceAtMost(999_999_999L)
                        repository.publishChildScreenShare(
                            isSharing = true,
                            frameBase64 = encoded,
                            frameWidth = captureWidth.toLong(),
                            frameHeight = captureHeight.toLong(),
                            frameSeq = frameSequence,
                            captureMode = "full_screen"
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Frame capture loop error: ${e.message}")
                }
                delay(CAPTURE_INTERVAL_MS)
            }
        }
    }

    private fun captureLatestFrameBase64(): String? {
        val reader = imageReader ?: return null
        var image: Image? = null
        var rawBitmap: Bitmap? = null
        var croppedBitmap: Bitmap? = null
        return try {
            image = reader.acquireLatestImage() ?: return null
            val planes = image.planes
            if (planes.isEmpty()) return null
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width
            val bitmapWidth = image.width + (rowPadding / pixelStride.coerceAtLeast(1))

            rawBitmap = Bitmap.createBitmap(bitmapWidth, image.height, Bitmap.Config.ARGB_8888)
            rawBitmap.copyPixelsFromBuffer(buffer)
            croppedBitmap = if (bitmapWidth != image.width) {
                Bitmap.createBitmap(rawBitmap, 0, 0, image.width, image.height)
            } else {
                rawBitmap
            }

            val output = ByteArrayOutputStream()
            var quality = 52
            croppedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
            while (output.size() > 160_000 && quality > 22) {
                output.reset()
                quality -= 10
                croppedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
            }
            Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.w(TAG, "Image to JPEG failed: ${e.message}")
            null
        } finally {
            image?.close()
            if (croppedBitmap != null && croppedBitmap !== rawBitmap) {
                croppedBitmap.recycle()
            }
            rawBitmap?.recycle()
        }
    }

    private fun stopSharingAndCleanUp() {
        _isSharing.value = false
        captureJob?.cancel()
        tearDownProjectionObjects()
        serviceScope.launch {
            runCatching {
                repository.publishChildScreenShare(
                    isSharing = false,
                    frameBase64 = "",
                    frameWidth = 0L,
                    frameHeight = 0L,
                    frameSeq = frameSequence,
                    captureMode = ""
                )
            }
            mainHandler.post { stopSelfCleanly() }
        }
    }

    private fun tearDownProjectionObjects() {
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { imageReader?.close() }
        imageReader = null
        val callback = projectionCallback
        if (callback != null) {
            runCatching { mediaProjection?.unregisterCallback(callback) }
        }
        projectionCallback = null
        runCatching { mediaProjection?.stop() }
        mediaProjection = null
    }

    private fun stopSelfCleanly() {
        _isSharing.value = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        _isSharing.value = false
        captureJob?.cancel()
        tearDownProjectionObjects()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "ScreenShareSvc"
        private const val VIRTUAL_DISPLAY_NAME = "FocusGardenScreenShare"
        private const val MAX_CAPTURE_DIMENSION = 640
        private const val CAPTURE_INTERVAL_MS = 2500L

        const val ACTION_START = "com.example.supervision.SCREEN_SHARE_START"
        const val ACTION_STOP = "com.example.supervision.SCREEN_SHARE_STOP"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        private val _isSharing = MutableStateFlow(false)
        val isSharing: StateFlow<Boolean> = _isSharing.asStateFlow()

        fun start(context: Context, resultCode: Int, resultData: Intent) {
            val intent = Intent(context, ScreenShareService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, resultData)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ScreenShareService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching { context.startService(intent) }
        }
    }
}
