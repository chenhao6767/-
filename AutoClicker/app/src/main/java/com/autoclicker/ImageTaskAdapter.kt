package com.autoclicker

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView

class ImageTaskAdapter(
    private val tasks:     MutableList<ImageMatchTask>,
    private val onDelete:  (Int) -> Unit,
    private val onCapture: (Int) -> Unit
) : RecyclerView.Adapter<ImageTaskAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val ivThumb:      ImageView   = v.findViewById(R.id.iv_thumb)
        val tvNoThumb:    TextView    = v.findViewById(R.id.tv_no_thumb)
        val etLabel:      EditText    = v.findViewById(R.id.et_img_label)
        val spinAction:   Spinner     = v.findViewById(R.id.spin_action)
        val sbThreshold:  SeekBar     = v.findViewById(R.id.sb_threshold)
        val tvThreshold:  TextView    = v.findViewById(R.id.tv_threshold_val)
        val switchEnabled:Switch      = v.findViewById(R.id.switch_enabled)
        val etCooldown:   EditText    = v.findViewById(R.id.et_cooldown)
        val btnCapture:   Button      = v.findViewById(R.id.btn_img_capture)
        val btnDelete:    ImageButton = v.findViewById(R.id.btn_img_delete)
        val tvStatus:     TextView    = v.findViewById(R.id.tv_match_status)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_image_task, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val task = tasks[position]

        // ── Label ──────────────────────────────────────────────────────────
        holder.etLabel.setText(task.label)
        holder.etLabel.setOnFocusChangeListener { _, has ->
            if (!has) task.label = holder.etLabel.text.toString()
        }

        // ── Thumbnail ──────────────────────────────────────────────────────
        val bytes = task.templateBytes
        if (bytes != null) {
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            task.templateBitmap = bmp
            holder.ivThumb.setImageBitmap(bmp)
            holder.ivThumb.visibility  = View.VISIBLE
            holder.tvNoThumb.visibility = View.GONE
        } else {
            holder.ivThumb.visibility  = View.GONE
            holder.tvNoThumb.visibility = View.VISIBLE
        }

        // ── Action spinner ─────────────────────────────────────────────────
        val actionLabels = arrayOf("找到后点击", "找到后跳过本轮", "找到后停止运行")
        holder.spinAction.adapter = ArrayAdapter(
            holder.itemView.context,
            android.R.layout.simple_spinner_dropdown_item,
            actionLabels
        )
        holder.spinAction.setSelection(task.action.coerceIn(0, 2))
        holder.spinAction.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { task.action = pos }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        // ── Threshold (50–100 % → progress 0–100) ─────────────────────────
        holder.sbThreshold.progress = ((task.threshold - 0.5f) * 200f).toInt().coerceIn(0, 100)
        holder.tvThreshold.text = "%.0f%%".format(task.threshold * 100)
        holder.sbThreshold.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                task.threshold = 0.5f + p / 200f
                holder.tvThreshold.text = "%.0f%%".format(task.threshold * 100)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        // ── Enable switch ──────────────────────────────────────────────────
        holder.switchEnabled.isChecked = task.enabled
        holder.switchEnabled.setOnCheckedChangeListener { _, checked -> task.enabled = checked }

        // ── Cooldown input ─────────────────────────────────────────────────
        holder.etCooldown.setText((task.cooldownMs / 1000f).let {
            if (it == it.toLong().toFloat()) it.toLong().toString() else "%.1f".format(it)
        })
        holder.etCooldown.setOnFocusChangeListener { _, has ->
            if (!has) {
                task.cooldownMs = ((holder.etCooldown.text.toString().toFloatOrNull() ?: 2f) * 1000).toLong()
                    .coerceAtLeast(200L)
            }
        }

        // ── Match status ───────────────────────────────────────────────────
        val ago = System.currentTimeMillis() - task.lastMatchedAt
        holder.tvStatus.text = when {
            task.lastMatchedAt == 0L -> "尚未匹配"
            ago < 5_000              -> "刚刚匹配"
            ago < 60_000             -> "${ago / 1000}秒前匹配"
            else                     -> "较久前匹配"
        }

        // ── Buttons ────────────────────────────────────────────────────────
        holder.btnCapture.setOnClickListener { onCapture(holder.adapterPosition) }
        holder.btnDelete.setOnClickListener  { onDelete(holder.adapterPosition)  }
    }

    override fun getItemCount() = tasks.size
}
