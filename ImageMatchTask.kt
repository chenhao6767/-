package com.autoclicker

import android.graphics.Bitmap
import java.io.Serializable

/**
 * An image-matching task.
 *
 * When the screen contains [templateBitmap], the runner will either:
 *  - ACTION_CLICK  : tap the center of the matched region
 *  - ACTION_SKIP   : skip the current ClickPoint and move to the next one
 *  - ACTION_STOP   : stop the entire loop
 *
 * [threshold] 0.0–1.0, how similar the region must be (default 0.85)
 * [templateBytes] PNG bytes stored for serialisation / persistence
 */
data class ImageMatchTask(
    val id: Long = System.currentTimeMillis(),
    var label: String = "识别任务",
    var action: Int = ACTION_CLICK,
    var threshold: Float = 0.85f,
    var templateBytes: ByteArray? = null,   // PNG bytes
    @Transient var templateBitmap: Bitmap? = null
) : Serializable {
    companion object {
        const val ACTION_CLICK = 0   // 找到后点击
        const val ACTION_SKIP  = 1   // 找到后跳过当前坐标点
        const val ACTION_STOP  = 2   // 找到后停止循环
    }

    fun actionLabel() = when (action) {
        ACTION_CLICK -> "点击"
        ACTION_SKIP  -> "跳过"
        ACTION_STOP  -> "停止"
        else         -> "点击"
    }
}
