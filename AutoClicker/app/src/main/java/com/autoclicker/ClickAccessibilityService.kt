package com.autoclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.*

/**
 * Core accessibility service — executes the click loop.
 *
 * New in this version:
 *   • Calls SoundAlertManager on every image-recognition event
 *   • Keeps BackgroundRunnerService notification in sync
 */
class ClickAccessibilityService : AccessibilityService() {

    companion object {
        var instance: ClickAccessibilityService? = null
        var isRunning = false

        var onActiveIndexChanged: ((Int)    -> Unit)? = null
        var onLoopCountChanged:   ((Int)    -> Unit)? = null
        var onTotalClicksChanged: ((Int)    -> Unit)? = null
        var onStatusMessage:      ((String) -> Unit)? = null
        var onStopped:            (()       -> Unit)? = null
    }

    private var job: Job? = null
    private val ui = Handler(Looper.getMainLooper())

    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { stopClicking() }
    override fun onDestroy()   { super.onDestroy(); instance = null; stopClicking() }

    // ── Public API ────────────────────────────────────────────────────────────

    fun startClicking(
        points:      List<ClickPoint>,
        imageTasks:  List<ImageMatchTask>,
        repeatTimes: Int
    ) {
        if (points.isEmpty()) return
        stopClicking()
        isRunning = true

        job = CoroutineScope(Dispatchers.Default).launch {
            var loopsDone   = 0
            var totalClicks = 0

            outer@ while (isActive && isRunning) {

                // ── Step 1: Image recognition ────────────────────────────
                if (imageTasks.any { it.enabled && it.templateBitmap != null }) {
                    when (val result = ImageRecognitionEngine.checkAll(imageTasks)) {

                        is ImageRecognitionEngine.CheckResult.ClickAt -> {
                            val pct = (result.score * 100).toInt()
                            val msg = "🔍 ${result.task.label} $pct% → 点击(${result.tapX},${result.tapY})"
                            notify(msg)
                            // ✅ Sound: success match
                            SoundAlertManager.playSuccess()
                            tap(result.tapX.toFloat(), result.tapY.toFloat())
                            totalClicks++
                            ui.post { onTotalClicksChanged?.invoke(totalClicks) }
                            syncNotification(loopsDone, totalClicks, msg)
                            delay(GlobalSpeedController.apply(400L))
                        }

                        is ImageRecognitionEngine.CheckResult.Skip -> {
                            val pct = (result.score * 100).toInt()
                            val msg = "⏭ ${result.task.label} $pct% → 跳过本轮"
                            notify(msg)
                            // ✅ Sound: success match (skip counts as a hit)
                            SoundAlertManager.playSuccess()
                            loopsDone++
                            ui.post { onLoopCountChanged?.invoke(loopsDone) }
                            syncNotification(loopsDone, totalClicks, msg)
                            if (repeatTimes > 0 && loopsDone >= repeatTimes) {
                                notify("✅ 已完成 $repeatTimes 次循环")
                                break@outer
                            }
                            delay(GlobalSpeedController.apply(300L))
                            continue@outer
                        }

                        is ImageRecognitionEngine.CheckResult.Stop -> {
                            val pct = (result.score * 100).toInt()
                            val msg = "⏹ ${result.task.label} $pct% → 停止运行"
                            notify(msg)
                            // 🔔 Sound: stop tone
                            SoundAlertManager.playStop()
                            syncNotification(loopsDone, totalClicks, msg)
                            break@outer
                        }

                        ImageRecognitionEngine.CheckResult.NoMatch -> {
                            // ❌ Sound: failure (only if enabled — off by default)
                            SoundAlertManager.playFailure()
                        }
                    }
                }

                if (!isRunning) break

                // ── Step 2: Coordinate click points ──────────────────────
                for ((index, point) in points.withIndex()) {
                    if (!isActive || !isRunning) break
                    ui.post { onActiveIndexChanged?.invoke(index) }
                    tap(point.x.toFloat(), point.y.toFloat())
                    totalClicks++
                    ui.post { onTotalClicksChanged?.invoke(totalClicks) }
                    delay(GlobalSpeedController.apply(point.delayMs))
                }

                // ── Step 3: Loop bookkeeping ──────────────────────────────
                loopsDone++
                ui.post { onLoopCountChanged?.invoke(loopsDone) }
                syncNotification(loopsDone, totalClicks, "第 $loopsDone 轮完成")
                if (repeatTimes > 0 && loopsDone >= repeatTimes) {
                    val msg = "✅ 已完成 $repeatTimes 次循环，自动停止"
                    notify(msg)
                    SoundAlertManager.playStop()
                    break
                }
            }

            isRunning = false
            ui.post { onActiveIndexChanged?.invoke(-1); onStopped?.invoke() }
        }
    }

    fun stopClicking() {
        isRunning = false
        job?.cancel(); job = null
        ui.post { onActiveIndexChanged?.invoke(-1); onStopped?.invoke() }
    }

    // ── Tap gesture ───────────────────────────────────────────────────────────

    private fun tap(x: Float, y: Float) {
        val path   = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 50L)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun notify(msg: String) = ui.post { onStatusMessage?.invoke(msg) }

    private fun syncNotification(loops: Int, clicks: Int, msg: String) {
        BackgroundRunnerService.updateNotification(loops, clicks, msg)
    }
}
