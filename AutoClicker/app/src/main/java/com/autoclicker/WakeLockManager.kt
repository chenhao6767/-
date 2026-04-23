package com.autoclicker

import android.content.Context
import android.os.PowerManager

/**
 * Acquires a partial WakeLock while the click loop is running.
 *
 * A partial WakeLock lets the CPU keep running even when the screen turns off,
 * which is essential for background operation. Without it Android will throttle
 * or pause coroutines once the display sleeps.
 *
 * Usage:
 *   WakeLockManager.acquire(context)   // call when starting the loop
 *   WakeLockManager.release()          // call when stopping the loop
 */
object WakeLockManager {

    private var wakeLock: PowerManager.WakeLock? = null

    fun acquire(context: Context) {
        if (wakeLock?.isHeld == true) return   // already held
        val pm = context.applicationContext
            .getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AutoClicker::LoopWakeLock"
        ).also {
            // Safety timeout: auto-release after 2 hours to avoid battery drain
            it.acquire(2 * 60 * 60 * 1000L)
        }
    }

    fun release() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {}
        wakeLock = null
    }

    val isHeld: Boolean get() = wakeLock?.isHeld == true
}
