package com.autoclicker

/**
 * A simple singleton that applies a global speed multiplier to every delay.
 *
 * Multiplier:
 *   0.25x → delays are 4× shorter  (very fast)
 *   1.0x  → original delays        (normal)
 *   3.0x  → delays are 3× longer   (slow)
 */
object GlobalSpeedController {

    /** Speed multiplier [0.25 .. 3.0]. Default = 1.0 (normal). */
    var multiplier: Float = 1.0f
        set(value) { field = value.coerceIn(0.25f, 3.0f) }

    /** Apply multiplier to a delay in ms. */
    fun apply(delayMs: Long): Long = (delayMs * multiplier).toLong().coerceAtLeast(50L)

    val label: String get() = when {
        multiplier <= 0.26f -> "极速 ×0.25"
        multiplier <= 0.51f -> "快速 ×0.5"
        multiplier <= 1.01f -> "正常 ×1"
        multiplier <= 2.01f -> "慢速 ×2"
        else                -> "极慢 ×3"
    }
}
