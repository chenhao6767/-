package com.autoclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.*

class ClickAccessibilityService : AccessibilityService() {

    companion object {
        var instance: ClickAccessibilityService? = null
        var isRunning = false

        var onActiveIndexChanged: ((Int) -> Unit)?  = null
        var onLoopCountChanged:   ((Int) -> Unit)?  = null
        var onTotalClicksChanged: ((Int) -> Unit)?  = null
        var onStatusMessage:      ((String) -> Unit)? = null
        var onStopped:            (() -> Unit)?     = null
    }

    private var job: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { stopClicking() }
    override fun onDestroy() { super.onDestroy(); instance = null; stopClicking() }

    // ── Public API ──────────────────────────────────────────────────────────

    fun startClicking(
        points: List<ClickPoint>,
        imageTasks: List<ImageMatchTask>,
        repeatTimes: Int
    ) {
        if (points.isEmpty()) return
        stopClicking()
        isRunning = true

        job = CoroutineScope(Dispatchers.Default).launch {
            var loopsDone   = 0
            var totalClicks = 0

            outer@ while (isActive && isRunning) {

                // ── 1. Image-recognition checks before each loop pass ─────
                for (task in imageTasks) {
                    if (!isActive || !isRunning) break
                    val bitmap = task.templateBitmap ?: continue
                    val screen = ScreenCaptureHelper.capture() ?: continue
                    val result = ImageMatcher.match(screen, bitmap, task.threshold)
                    screen.recycle()

                    if (result.found) {
                        val pct = (result.score * 100).toInt()
                        notify("🔍 ${task.label} 匹配成功 ($pct%)")
                        when (task.action) {
                            ImageMatchTask.ACTION_CLICK -> {
                                performTap(
                                    result.region.centerX().toFloat(),
                                    result.region.centerY().toFloat()
                                )
                                totalClicks++
                                mainHandler.post { onTotalClicksChanged?.invoke(totalClicks) }
                                delay(GlobalSpeedController.apply(300L))
                            }
                            ImageMatchTask.ACTION_SKIP -> {
                                notify("⏭ ${task.label} → 跳过本轮")
                                loopsDone++
                                mainHandler.post { onLoopCountChanged?.invoke(loopsDone) }
                                if (repeatTimes > 0 && loopsDone >= repeatTimes) break@outer
                                continue@outer
                            }
                            ImageMatchTask.ACTION_STOP -> {
                                notify("⏹ ${task.label} → 停止循环")
                                isRunning = false
                                break@outer
                            }
                        }
                    }
                }

                if (!isRunning) break

                // ── 2. Execute coordinate click points ────────────────────
                for ((index, point) in points.withIndex()) {
                    if (!isActive || !isRunning) break
                    mainHandler.post { onActiveIndexChanged?.invoke(index) }
                    performTap(point.x.toFloat(), point.y.toFloat())
                    totalClicks++
                    mainHandler.post { onTotalClicksChanged?.invoke(totalClicks) }
                    delay(GlobalSpeedController.apply(point.delayMs))
                }

                loopsDone++
                mainHandler.post { onLoopCountChanged?.invoke(loopsDone) }
                if (repeatTimes > 0 && loopsDone >= repeatTimes) break
            }

            isRunning = false
            mainHandler.post {
                onActiveIndexChanged?.invoke(-1)
                onStopped?.invoke()
            }
        }
    }

    fun stopClicking() {
        isRunning = false
        job?.cancel()
        job = null
        mainHandler.post { onActiveIndexChanged?.invoke(-1); onStopped?.invoke() }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun performTap(x: Float, y: Float) {
        val path   = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 50L)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    private fun notify(msg: String) = mainHandler.post { onStatusMessage?.invoke(msg) }
}
