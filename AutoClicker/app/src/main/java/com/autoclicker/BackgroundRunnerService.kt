package com.autoclicker

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Foreground service that keeps the app alive while the user switches to
 * other applications.
 *
 * Android aggressively kills background processes. By running a foreground
 * service the OS keeps our process alive, which means:
 *   1. [ClickAccessibilityService] continues executing gestures.
 *   2. [ScreenCaptureHelper] / [ImageRecognitionEngine] continue capturing
 *      and matching.
 *   3. [SoundAlertManager] can still play tones.
 *
 * The persistent notification shows real-time status and has quick-action
 * buttons for Stop and Open-App.
 *
 * Lifecycle:
 *   MainActivity.startRunning()  →  startForegroundService(BackgroundRunnerService)
 *   MainActivity.stopRunning()   →  stopService(BackgroundRunnerService)
 *   ClickAccessibilityService    →  calls updateNotification() via companion
 */
class BackgroundRunnerService : Service() {

    // ── Companion: static helpers usable from anywhere ────────────────────────
    companion object {
        const val CHANNEL_ID   = "autoclicker_bg"
        const val NOTIF_ID     = 100

        // Intent actions for notification buttons
        const val ACTION_STOP       = "com.autoclicker.ACTION_STOP"
        const val ACTION_OPEN_APP   = "com.autoclicker.ACTION_OPEN_APP"

        // Shared state shown in notification
        @Volatile var notifLoops:    Int    = 0
        @Volatile var notifClicks:   Int    = 0
        @Volatile var notifLastMsg:  String = "运行中…"

        private var instance: BackgroundRunnerService? = null

        /** Call from anywhere to refresh the persistent notification text. */
        fun updateNotification(loops: Int, clicks: Int, msg: String) {
            notifLoops   = loops
            notifClicks  = clicks
            notifLastMsg = msg
            instance?.refreshNotification()
        }

        fun isRunning(context: Context): Boolean {
            val mgr = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            @Suppress("DEPRECATION")
            return mgr.getRunningServices(Int.MAX_VALUE)
                .any { it.service.className == BackgroundRunnerService::class.java.name }
        }
    }

    // ── Service lifecycle ─────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        WakeLockManager.acquire(this)   // keep CPU awake
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                // User tapped Stop in notification
                ClickAccessibilityService.instance?.stopClicking()
                stopSelf()
            }
            ACTION_OPEN_APP -> {
                // User tapped Open in notification
                val launch = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(launch)
            }
            else -> {
                // Normal start → show foreground notification
                startForeground(NOTIF_ID, buildNotification())
            }
        }
        return START_STICKY   // Restart if killed by OS
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        WakeLockManager.release()   // release CPU lock
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "循环点击器 · 后台运行",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示自动点击器的运行状态"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    fun refreshNotification() {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(NOTIF_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        // ── Pending intents ─────────────────────────────────────────────────
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        else PendingIntent.FLAG_UPDATE_CURRENT

        val openIntent = PendingIntent.getService(
            this, 0,
            Intent(this, BackgroundRunnerService::class.java).apply { action = ACTION_OPEN_APP },
            flags
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, BackgroundRunnerService::class.java).apply { action = ACTION_STOP },
            flags
        )
        val tapIntent = PendingIntent.getActivity(
            this, 2,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            flags
        )

        // ── Build notification ──────────────────────────────────────────────
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("循环点击器 · 后台运行中")
            .setContentText("循环 $notifLoops 次 · 点击 $notifClicks 下")
            .setSubText(notifLastMsg.take(40))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(tapIntent)
            .addAction(
                android.R.drawable.ic_media_pause,
                "⏹ 停止",
                stopIntent
            )
            .addAction(
                android.R.drawable.ic_menu_view,
                "打开 App",
                openIntent
            )
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("循环 $notifLoops 次 · 点击 $notifClicks 下\n$notifLastMsg")
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
