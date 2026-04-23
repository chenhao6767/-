package com.autoclicker

import android.graphics.Bitmap
import java.io.Serializable

/**
 * One image-recognition rule.
 *
 * Each loop iteration the engine captures the screen and checks every
 * enabled ImageMatchTask. On a match it performs the configured [action].
 *
 * Actions:
 *   ACTION_CLICK  – tap the centre of the matched region, then continue
 *   ACTION_SKIP   – skip remaining click-points this loop pass, start next loop
 *   ACTION_STOP   – stop the entire run immediately
 *
 * [searchRegion] optionally restricts the search to a sub-rectangle of the
 * screen (left/top/right/bottom as fractions of screen size, 0.0–1.0).
 * Set to null to search the whole screen.
 *
 * [cooldownMs] prevents the same task from firing more than once per
 * [cooldownMs] milliseconds.
 */
data class ImageMatchTask(
    val id:          Long    = System.currentTimeMillis(),
    var label:       String  = "识别任务",
    var action:      Int     = ACTION_CLICK,
    var threshold:   Float   = 0.85f,
    var enabled:     Boolean = true,
    var cooldownMs:  Long    = 2000L,
    var searchRegion: SearchRegion? = null,

    // Stored as PNG bytes for serialisation
    var templateBytes:  ByteArray? = null,

    // Runtime-only (not serialised)
    @Transient var templateBitmap: Bitmap?  = null,
    @Transient var lastMatchedAt:  Long     = 0L
) : Serializable {

    companion object {
        const val ACTION_CLICK = 0
        const val ACTION_SKIP  = 1
        const val ACTION_STOP  = 2
    }

    fun actionLabel() = when (action) {
        ACTION_CLICK -> "找到后点击"
        ACTION_SKIP  -> "找到后跳过"
        ACTION_STOP  -> "找到后停止"
        else         -> "找到后点击"
    }

    /** True if the cooldown has elapsed since the last match. */
    fun isCooledDown(): Boolean =
        System.currentTimeMillis() - lastMatchedAt >= cooldownMs

    data class SearchRegion(
        val left:   Float = 0f,
        val top:    Float = 0f,
        val right:  Float = 1f,
        val bottom: Float = 1f
    ) : Serializable
}
