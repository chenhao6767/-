package com.autoclicker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.TextView
import androidx.core.app.NotificationCompat

/**
 * Draws a full-screen transparent overlay for coordinate capture.
 * When the user taps anywhere, the (x,y) is broadcast back to MainActivity.
 */
class FloatingWindowService : Service() {

    companion object {
        const val ACTION_COORDINATE_CAPTURED = "com.autoclicker.COORDINATE_CAPTURED"
        const val EXTRA_X = "extra_x"
        const val EXTRA_Y = "extra_y"
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        showOverlay()
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
    }

    private fun startForegroundNotification() {
        val channelId = "capture_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "坐标抓取", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("点击屏幕任意位置抓取坐标")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()
        startForeground(1, notification)
    }

    private fun showOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Semi-transparent full-screen overlay
        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.overlay_capture, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        val coordLabel = overlayView!!.findViewById<TextView>(R.id.tv_coord)

        overlayView!!.setOnTouchListener { _, event ->
            val x = event.rawX.toInt()
            val y = event.rawY.toInt()

            when (event.action) {
                MotionEvent.ACTION_MOVE -> {
                    coordLabel.text = "($x, $y)"
                }
                MotionEvent.ACTION_UP -> {
                    // Broadcast coordinate back
                    val intent = Intent(ACTION_COORDINATE_CAPTURED).apply {
                        putExtra(EXTRA_X, x)
                        putExtra(EXTRA_Y, y)
                        setPackage(packageName)
                    }
                    sendBroadcast(intent)
                    stopSelf()
                }
            }
            true
        }

        windowManager.addView(overlayView, params)
    }

    private fun removeOverlay() {
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
        }
    }
}
