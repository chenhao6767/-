package com.autoclicker

import android.graphics.Bitmap
import android.graphics.Rect
import kotlinx.coroutines.*

/**
 * High-level engine that orchestrates screen capture + template matching.
 *
 * Call [checkAll] once per loop iteration, passing the current list of tasks.
 * It returns a [CheckResult] describing what (if anything) was found.
 */
object ImageRecognitionEngine {

    // ── Result types ─────────────────────────────────────────────────────────

    sealed class CheckResult {
        /** No task matched → continue normal click sequence */
        object NoMatch : CheckResult()

        /** A CLICK task matched → tap [tapX],[tapY] then continue */
        data class ClickAt(
            val task:   ImageMatchTask,
            val tapX:   Int,
            val tapY:   Int,
            val score:  Float,
            val region: Rect
        ) : CheckResult()

        /** A SKIP task matched → skip this loop pass */
        data class Skip(val task: ImageMatchTask, val score: Float) : CheckResult()

        /** A STOP task matched → stop the run */
        data class Stop(val task: ImageMatchTask, val score: Float) : CheckResult()
    }

    // ── Main check ────────────────────────────────────────────────────────────

    /**
     * Take a screenshot and run all enabled, cooled-down tasks against it.
     * Returns the FIRST task that matches (tasks are checked in list order).
     *
     * This suspends on [Dispatchers.Default]; safe to call from a coroutine.
     */
    suspend fun checkAll(tasks: List<ImageMatchTask>): CheckResult {
        // Filter to runnable tasks
        val runnable = tasks.filter { it.enabled && it.templateBitmap != null && it.isCooledDown() }
        if (runnable.isEmpty()) return CheckResult.NoMatch

        // Capture screen once and reuse for all tasks
        val screen = ScreenCaptureHelper.capture() ?: return CheckResult.NoMatch

        try {
            for (task in runnable) {
                val bitmap = task.templateBitmap ?: continue

                // Optionally crop to search region
                val searchBitmap = cropToRegion(screen, task.searchRegion)

                val result = ImageMatcher.match(searchBitmap, bitmap, task.threshold)

                if (searchBitmap !== screen) searchBitmap.recycle()

                if (!result.found) continue

                // Update cooldown timestamp
                task.lastMatchedAt = System.currentTimeMillis()

                // Adjust region coords if we searched a sub-region
                val adjustedRegion = adjustRegion(result.region, screen, task.searchRegion)

                return when (task.action) {
                    ImageMatchTask.ACTION_CLICK -> CheckResult.ClickAt(
                        task   = task,
                        tapX   = adjustedRegion.centerX(),
                        tapY   = adjustedRegion.centerY(),
                        score  = result.score,
                        region = adjustedRegion
                    )
                    ImageMatchTask.ACTION_SKIP -> CheckResult.Skip(task, result.score)
                    ImageMatchTask.ACTION_STOP -> CheckResult.Stop(task, result.score)
                    else -> CheckResult.NoMatch
                }
            }
            return CheckResult.NoMatch
        } finally {
            screen.recycle()
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun cropToRegion(screen: Bitmap, region: ImageMatchTask.SearchRegion?): Bitmap {
        if (region == null) return screen
        val x = (screen.width  * region.left  ).toInt().coerceIn(0, screen.width)
        val y = (screen.height * region.top   ).toInt().coerceIn(0, screen.height)
        val w = ((screen.width  * (region.right  - region.left)).toInt()).coerceAtLeast(1)
            .coerceAtMost(screen.width  - x)
        val h = ((screen.height * (region.bottom - region.top )).toInt()).coerceAtLeast(1)
            .coerceAtMost(screen.height - y)
        return Bitmap.createBitmap(screen, x, y, w, h)
    }

    private fun adjustRegion(
        region: Rect,
        screen: Bitmap,
        searchRegion: ImageMatchTask.SearchRegion?
    ): Rect {
        if (searchRegion == null) return region
        val offsetX = (screen.width  * searchRegion.left).toInt()
        val offsetY = (screen.height * searchRegion.top ).toInt()
        return Rect(
            region.left   + offsetX,
            region.top    + offsetY,
            region.right  + offsetX,
            region.bottom + offsetY
        )
    }
}
