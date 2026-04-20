package com.autoclicker

import android.app.*
import android.content.Intent
import android.graphics.*
import android.os.*
import android.view.*
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat

/**
 * Shows a full-screen overlay where the user drags a rectangle to select
 * the region they want to use as the image template.
 *
 * After selection, takes a screenshot via [ScreenCaptureHelper] and broadcasts
 * the cropped PNG bytes back to MainActivity.
 */
class CropOverlayService : Service() {

    companion object {
        const val ACTION_CROP_DONE  = "com.autoclicker.CROP_DONE"
        const val EXTRA_TASK_INDEX  = "task_index"
        const val EXTRA_PNG_BYTES   = "png_bytes"
        const val INTENT_TASK_INDEX = "intent_task_index"
    }

    private lateinit var wm: WindowManager
    private var overlay: CropView? = null
    private var taskIndex = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        taskIndex = intent?.getIntExtra(INTENT_TASK_INDEX, 0) ?: 0
        startForegroundNotification()
        showOverlay()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        overlay?.let { wm.removeView(it) }
    }

    private fun startForegroundNotification() {
        val ch = "crop_ch"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(ch, "截图区域选择", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        startForeground(2, NotificationCompat.Builder(this, ch)
            .setContentTitle("拖动选择识别区域")
            .setSmallIcon(android.R.drawable.ic_menu_crop)
            .build())
    }

    private fun showOverlay() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        val cropView = CropView(this) { rect ->
            // User finished selection — take screenshot and crop
            val screen = ScreenCaptureHelper.capture()
            if (screen != null && !rect.isEmpty) {
                val safeRect = Rect(
                    rect.left.coerceIn(0, screen.width),
                    rect.top.coerceIn(0, screen.height),
                    rect.right.coerceIn(0, screen.width),
                    rect.bottom.coerceIn(0, screen.height)
                )
                if (safeRect.width() > 0 && safeRect.height() > 0) {
                    val cropped = Bitmap.createBitmap(
                        screen, safeRect.left, safeRect.top,
                        safeRect.width(), safeRect.height()
                    )
                    val out = java.io.ByteArrayOutputStream()
                    cropped.compress(Bitmap.CompressFormat.PNG, 100, out)
                    val bytes = out.toByteArray()

                    sendBroadcast(Intent(ACTION_CROP_DONE).apply {
                        putExtra(EXTRA_TASK_INDEX, taskIndex)
                        putExtra(EXTRA_PNG_BYTES, bytes)
                        setPackage(packageName)
                    })
                    screen.recycle()
                }
            }
            stopSelf()
        }

        overlay = cropView

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        wm.addView(cropView, params)
    }
}

// ── Crop selection view ───────────────────────────────────────────────────────
class CropView(context: android.content.Context, private val onDone: (Rect) -> Unit)
    : FrameLayout(context) {

    private val paintDim    = Paint().apply { color = 0xAA000000.toInt() }
    private val paintBorder = Paint().apply {
        color = 0xFF00DC96.toInt(); style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val paintFill   = Paint().apply { color = 0x1A00DC96 }
    private val paintLabel  = Paint().apply {
        color = 0xFF00DC96.toInt(); textSize = 36f; isAntiAlias = true
    }

    private var startX = 0f; private var startY = 0f
    private var endX   = 0f; private var endY   = 0f
    private var dragging = false

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = e.rawX; startY = e.rawY
                endX   = e.rawX; endY   = e.rawY
                dragging = true
            }
            MotionEvent.ACTION_MOVE -> {
                endX = e.rawX; endY = e.rawY
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                endX = e.rawX; endY = e.rawY
                dragging = false
                invalidate()
                val rect = selectionRect()
                if (rect.width() > 10 && rect.height() > 10) onDone(rect)
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paintDim)

        if (dragging || (endX != 0f)) {
            val r = selectionRectF()
            canvas.drawRect(r, paintFill)
            canvas.drawRect(r, paintBorder)

            val w = (r.width()).toInt()
            val h = (r.height()).toInt()
            val label = "  ${w} × ${h}  "
            canvas.drawText(label, r.left, r.top - 8f, paintLabel)
        }

        // Instruction
        val hint = "拖动选择识别区域，松手确认"
        canvas.drawText(hint, (width / 2f) - 200f, 80f, paintLabel)
    }

    private fun selectionRectF() = RectF(
        minOf(startX, endX), minOf(startY, endY),
        maxOf(startX, endX), maxOf(startY, endY)
    )

    private fun selectionRect(): Rect {
        val f = selectionRectF()
        return Rect(f.left.toInt(), f.top.toInt(), f.right.toInt(), f.bottom.toInt())
    }
}
