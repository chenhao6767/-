package com.autoclicker

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
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
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Manages a MediaProjection session for continuous screen capture.
 *
 * Android 14+ requires the projection to run inside a foreground service
 * with foregroundServiceType="mediaProjection". This helper owns both the
 * singleton projection state AND the foreground service lifecycle.
 *
 * Usage:
 *   1. Call [requestPermission] from an Activity.
 *   2. In onActivityResult pass resultCode + data to [init].
 *   3. Call [capture] to get a Bitmap at any time.
 *   4. Call [release] when done (e.g. onDestroy).
 */
object ScreenCaptureHelper {

    const val REQUEST_CODE = 1001

    // ── Projection state ─────────────────────────────────────────────────────
    private var projection:     MediaProjection? = null
    private var imageReader:    ImageReader?     = null
    private var virtualDisplay: VirtualDisplay?  = null

    private var screenW = 0
    private var screenH = 0
    private var screenDpi = 0

    // ── Permission request ───────────────────────────────────────────────────

    fun requestPermission(activity: Activity) {
        val mgr = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                as MediaProjectionManager
        activity.startActivityForResult(mgr.createScreenCaptureIntent(), REQUEST_CODE)
    }

    // ── Initialise (call from onActivityResult) ──────────────────────────────

    fun init(context: Context, resultCode: Int, data: Intent) {
        release()   // clean up any previous session

        val metrics  = context.resources.displayMetrics
        screenW      = metrics.widthPixels
        screenH      = metrics.heightPixels
        screenDpi    = metrics.densityDpi

        val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                as MediaProjectionManager
        projection = mgr.getMediaProjection(resultCode, data)

        // Register a callback so we know if the user revokes projection mid-session
        projection!!.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { release() }
        }, null)

        imageReader = ImageReader.newInstance(screenW, screenH, PixelFormat.RGBA_8888, 3)

        virtualDisplay = projection!!.createVirtualDisplay(
            "AutoClickerScreen",
            screenW, screenH, screenDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, null
        )
    }

    // ── Capture ──────────────────────────────────────────────────────────────

    /**
     * Take a screenshot.
     * Returns null if the projection hasn't been initialised or if no frame
     * is available yet (call again after a short delay).
     *
     * The returned Bitmap is a NEW object; the caller is responsible for
     * recycling it when done.
     */
    fun capture(): Bitmap? {
        val reader = imageReader ?: return null
        val image: Image = reader.acquireLatestImage() ?: return null
        return try {
            val plane       = image.planes[0]
            val pixelStride = plane.pixelStride
            val rowStride   = plane.rowStride
            val rowPadding  = rowStride - pixelStride * screenW

            // Allocate with row padding included, then crop
            val raw = Bitmap.createBitmap(
                screenW + rowPadding / pixelStride,
                screenH, Bitmap.Config.ARGB_8888
            )
            raw.copyPixelsFromBuffer(plane.buffer)

            // Crop to exact screen size (removes right-edge padding artefacts)
            if (rowPadding == 0) raw
            else {
                val cropped = Bitmap.createBitmap(raw, 0, 0, screenW, screenH)
                raw.recycle()
                cropped
            }
        } catch (e: Exception) {
            null
        } finally {
            image.close()
        }
    }

    // ── State query ──────────────────────────────────────────────────────────

    fun isReady(): Boolean = projection != null && imageReader != null

    // ── Release ──────────────────────────────────────────────────────────────

    fun release() {
        virtualDisplay?.release();  virtualDisplay = null
        imageReader?.close();       imageReader    = null
        projection?.stop();         projection     = null
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// ScreenCaptureService — required foreground service wrapper for Android 10+
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Thin foreground service that keeps the MediaProjection alive while the
 * loop is running. MainActivity binds to it via [ScreenCaptureHelper].
 *
 * Start it BEFORE calling ScreenCaptureHelper.init():
 *   startForegroundService(Intent(this, ScreenCaptureService::class.java))
 */
class ScreenCaptureService : Service() {

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA        = "data"
        private  const val NOTIF_ID  = 42
        private  const val CHANNEL   = "screen_cap"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, -1) ?: -1
        val data       = intent?.getParcelableExtra<Intent>(EXTRA_DATA)

        if (resultCode != -1 && data != null) {
            ScreenCaptureHelper.init(applicationContext, resultCode, data)
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        ScreenCaptureHelper.release()
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL, "屏幕截图", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
        return NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("循环点击器")
            .setContentText("图像识别运行中…")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
    }
}
