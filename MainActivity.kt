package com.autoclicker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Collections

class MainActivity : AppCompatActivity() {

    // ── Views ──────────────────────────────────────────────────────────────
    private lateinit var rvPoints:     RecyclerView
    private lateinit var rvImageTasks: RecyclerView
    private lateinit var btnStart:     Button
    private lateinit var btnCapture:   Button
    private lateinit var btnAddManual: Button
    private lateinit var btnAddImage:  Button
    private lateinit var tvLoops:      TextView
    private lateinit var tvTotalClicks:TextView
    private lateinit var tvStatus:     TextView
    private lateinit var tvLog:        TextView
    private lateinit var tvSpeedLabel: TextView
    private lateinit var sbSpeed:      SeekBar
    private lateinit var etRepeat:     EditText
    private lateinit var tabCoords:    TextView
    private lateinit var tabImages:    TextView
    private lateinit var panelCoords:  View
    private lateinit var panelImages:  View

    // ── State ──────────────────────────────────────────────────────────────
    private val points     = mutableListOf<ClickPoint>()
    private val imageTasks = mutableListOf<ImageMatchTask>()
    private lateinit var pointAdapter:     PointAdapter
    private lateinit var imageTaskAdapter: ImageTaskAdapter
    private var isRunning        = false
    private var pendingCropIndex = -1

    // ── Broadcast receivers ────────────────────────────────────────────────
    private val captureReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == FloatingWindowService.ACTION_COORDINATE_CAPTURED) {
                addPoint(
                    intent.getIntExtra(FloatingWindowService.EXTRA_X, 0),
                    intent.getIntExtra(FloatingWindowService.EXTRA_Y, 0)
                )
            }
        }
    }

    private val cropReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == CropOverlayService.ACTION_CROP_DONE) {
                val idx   = intent.getIntExtra(CropOverlayService.EXTRA_TASK_INDEX, -1)
                val bytes = intent.getByteArrayExtra(CropOverlayService.EXTRA_PNG_BYTES)
                if (idx >= 0 && idx < imageTasks.size && bytes != null) {
                    imageTasks[idx].templateBytes  = bytes
                    imageTasks[idx].templateBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    imageTaskAdapter.notifyItemChanged(idx)
                    appendLog("✅ [${imageTasks[idx].label}] 图片已更新")
                }
            }
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        setupTabs()
        setupSpeed()
        setupRecyclerViews()
        setupButtons()
        setupServiceCallbacks()

        registerReceiver(captureReceiver,
            IntentFilter(FloatingWindowService.ACTION_COORDINATE_CAPTURED), RECEIVER_NOT_EXPORTED)
        registerReceiver(cropReceiver,
            IntentFilter(CropOverlayService.ACTION_CROP_DONE), RECEIVER_NOT_EXPORTED)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(captureReceiver)
        unregisterReceiver(cropReceiver)
    }

    // Options menu (Save / Load / Manage profiles)
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "💾 保存方案")
        menu.add(0, 2, 0, "📂 加载方案")
        menu.add(0, 3, 0, "🗑 管理方案")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            1 -> ProfileDialogHelper.showSave(this, points) { }
            2 -> ProfileDialogHelper.showLoad(this) { _, loaded ->
                    points.clear(); points.addAll(loaded)
                    pointAdapter.notifyDataSetChanged()
                    updatePointCount()
                }
            3 -> ProfileDialogHelper.showManage(this) { }
        }
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ScreenCaptureHelper.REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            ScreenCaptureHelper.init(this, resultCode, data)
            appendLog("📷 截图权限已授予")
            if (pendingCropIndex >= 0) {
                startCropOverlay(pendingCropIndex)
                pendingCropIndex = -1
            }
        }
    }

    // ── View binding ────────────────────────────────────────────────────────
    private fun bindViews() {
        rvPoints       = findViewById(R.id.rv_points)
        rvImageTasks   = findViewById(R.id.rv_image_tasks)
        btnStart       = findViewById(R.id.btn_start)
        btnCapture     = findViewById(R.id.btn_capture)
        btnAddManual   = findViewById(R.id.btn_add_manual)
        btnAddImage    = findViewById(R.id.btn_add_image)
        tvLoops        = findViewById(R.id.tv_loops)
        tvTotalClicks  = findViewById(R.id.tv_total_clicks)
        tvStatus       = findViewById(R.id.tv_status)
        tvLog          = findViewById(R.id.tv_log)
        tvSpeedLabel   = findViewById(R.id.tv_speed_label)
        sbSpeed        = findViewById(R.id.sb_speed)
        etRepeat       = findViewById(R.id.et_repeat)
        tabCoords      = findViewById(R.id.tab_coords)
        tabImages      = findViewById(R.id.tab_images)
        panelCoords    = findViewById(R.id.panel_coords)
        panelImages    = findViewById(R.id.panel_images)
    }

    // ── Tabs ────────────────────────────────────────────────────────────────
    private fun setupTabs() {
        tabCoords.setOnClickListener { showTab(true) }
        tabImages.setOnClickListener { showTab(false) }
        showTab(true)
    }

    private fun showTab(coordsActive: Boolean) {
        panelCoords.visibility = if (coordsActive) View.VISIBLE else View.GONE
        panelImages.visibility = if (coordsActive) View.GONE   else View.VISIBLE
        tabCoords.setTextColor(if (coordsActive) 0xFF00DC96.toInt() else 0xFF1A4030.toInt())
        tabImages.setTextColor(if (coordsActive) 0xFF1A4030.toInt() else 0xFF00DC96.toInt())
    }

    // ── Speed SeekBar ────────────────────────────────────────────────────────
    // Seek steps map to: 0.25x, 0.5x, 1.0x, 2.0x, 3.0x  (5 positions = progress 0–4)
    private val speedSteps = floatArrayOf(0.25f, 0.5f, 1.0f, 2.0f, 3.0f)

    private fun setupSpeed() {
        sbSpeed.max = speedSteps.size - 1
        sbSpeed.progress = 2  // default = 1.0x
        tvSpeedLabel.text = GlobalSpeedController.label

        sbSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                GlobalSpeedController.multiplier = speedSteps[progress]
                tvSpeedLabel.text = GlobalSpeedController.label
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    // ── RecyclerViews ────────────────────────────────────────────────────────
    private fun setupRecyclerViews() {
        pointAdapter = PointAdapter(points) { removePoint(it) }
        rvPoints.layoutManager = LinearLayoutManager(this)
        rvPoints.adapter = pointAdapter
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder,
                                target: RecyclerView.ViewHolder): Boolean {
                Collections.swap(points, vh.adapterPosition, target.adapterPosition)
                pointAdapter.notifyItemMoved(vh.adapterPosition, target.adapterPosition)
                return true
            }
            override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {}
        }).attachToRecyclerView(rvPoints)

        imageTaskAdapter = ImageTaskAdapter(
            imageTasks,
            onDelete  = { idx -> imageTasks.removeAt(idx); imageTaskAdapter.notifyItemRemoved(idx) },
            onCapture = { idx -> requestCropForTask(idx) }
        )
        rvImageTasks.layoutManager = LinearLayoutManager(this)
        rvImageTasks.adapter = imageTaskAdapter
    }

    // ── Buttons ─────────────────────────────────────────────────────────────
    private fun setupButtons() {
        btnStart.setOnClickListener     { if (isRunning) stopClicking() else startClicking() }
        btnAddManual.setOnClickListener { addPoint(500, 500) }
        btnAddImage.setOnClickListener  { addImageTask() }
        btnCapture.setOnClickListener   {
            if (!Settings.canDrawOverlays(this)) { requestOverlayPermission(); return@setOnClickListener }
            startService(Intent(this, FloatingWindowService::class.java))
            moveTaskToBack(true)
        }
        findViewById<Button>(R.id.btn_reset).setOnClickListener {
            tvLoops.text = "0"; tvTotalClicks.text = "0"
        }
    }

    // ── Service callbacks ────────────────────────────────────────────────────
    private fun setupServiceCallbacks() {
        ClickAccessibilityService.onActiveIndexChanged = { pointAdapter.setActiveIndex(it) }
        ClickAccessibilityService.onLoopCountChanged   = { tvLoops.text = it.toString() }
        ClickAccessibilityService.onTotalClicksChanged = { tvTotalClicks.text = it.toString() }
        ClickAccessibilityService.onStatusMessage      = { appendLog(it) }
        ClickAccessibilityService.onStopped            = { isRunning = false; updateUI() }
    }

    // ── Start / Stop ─────────────────────────────────────────────────────────
    private fun startClicking() {
        if (!isAccessibilityEnabled()) { openAccessibilitySettings(); return }
        if (points.isEmpty()) {
            Toast.makeText(this, "请先添加坐标点", Toast.LENGTH_SHORT).show(); return
        }
        if (imageTasks.any { it.templateBitmap != null } && !ScreenCaptureHelper.isReady()) {
            Toast.makeText(this, "图像识别需要截图权限，请授权", Toast.LENGTH_LONG).show()
            ScreenCaptureHelper.requestPermission(this)
            return
        }
        val repeat = etRepeat.text.toString().toIntOrNull() ?: 0
        ClickAccessibilityService.instance?.startClicking(points, imageTasks, repeat)
        isRunning = true
        updateUI()
        appendLog("▶ 开始运行，速度：${GlobalSpeedController.label}")
    }

    private fun stopClicking() {
        ClickAccessibilityService.instance?.stopClicking()
        isRunning = false
        updateUI()
        appendLog("⏹ 已停止")
    }

    // ── Point management ─────────────────────────────────────────────────────
    private fun addPoint(x: Int, y: Int) {
        points.add(ClickPoint(label = "点 ${points.size + 1}", x = x, y = y))
        pointAdapter.notifyItemInserted(points.size - 1)
        updatePointCount()
    }

    private fun removePoint(pos: Int) {
        points.removeAt(pos); pointAdapter.notifyItemRemoved(pos); updatePointCount()
    }

    // ── Image task management ────────────────────────────────────────────────
    private fun addImageTask() {
        imageTasks.add(ImageMatchTask(label = "识别任务 ${imageTasks.size + 1}"))
        imageTaskAdapter.notifyItemInserted(imageTasks.size - 1)
        showTab(false)
    }

    private fun requestCropForTask(taskIndex: Int) {
        if (!Settings.canDrawOverlays(this)) { requestOverlayPermission(); return }
        if (!ScreenCaptureHelper.isReady()) {
            pendingCropIndex = taskIndex
            ScreenCaptureHelper.requestPermission(this)
            return
        }
        startCropOverlay(taskIndex)
    }

    private fun startCropOverlay(taskIndex: Int) {
        startService(Intent(this, CropOverlayService::class.java).apply {
            putExtra(CropOverlayService.INTENT_TASK_INDEX, taskIndex)
        })
        moveTaskToBack(true)
    }

    // ── UI helpers ─────────────────────────────────────────────────────────
    private fun updateUI() {
        if (isRunning) {
            btnStart.text = "⏹ 停止"
            btnStart.setBackgroundColor(0xFFCC0033.toInt())
            tvStatus.text = "● 运行中"
            tvStatus.setTextColor(0xFF00DC96.toInt())
        } else {
            btnStart.text = "▶ 开始运行"
            btnStart.setBackgroundColor(0xFF00DC96.toInt())
            tvStatus.text = "○ 待机"
            tvStatus.setTextColor(0xFF1A3025.toInt())
        }
        btnCapture.isEnabled   = !isRunning
        btnAddManual.isEnabled = !isRunning
        btnAddImage.isEnabled  = !isRunning
        sbSpeed.isEnabled      = !isRunning
    }

    private fun updatePointCount() {
        findViewById<TextView>(R.id.tv_point_count).text = points.size.toString()
    }

    private fun appendLog(msg: String) {
        val lines = tvLog.text.toString().lines().takeLast(6)
        tvLog.text = (lines + msg).joinToString("\n")
    }

    // ── Permissions ──────────────────────────────────────────────────────────
    private fun isAccessibilityEnabled(): Boolean {
        val svc = "${packageName}/${ClickAccessibilityService::class.java.canonicalName}"
        return try {
            Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED) == 1 &&
            Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?.split(":")?.any { it.equals(svc, true) } == true
        } catch (e: Exception) { false }
    }

    private fun openAccessibilitySettings() {
        Toast.makeText(this, "请开启「循环点击器」的无障碍服务", Toast.LENGTH_LONG).show()
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun requestOverlayPermission() {
        Toast.makeText(this, "请授予「显示在其他应用上层」权限", Toast.LENGTH_LONG).show()
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")))
    }
}
