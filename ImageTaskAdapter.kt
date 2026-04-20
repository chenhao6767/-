package com.autoclicker

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView

class ImageTaskAdapter(
    private val tasks: MutableList<ImageMatchTask>,
    private val onDelete: (Int) -> Unit,
    private val onCapture: (Int) -> Unit       // request screenshot crop for this index
) : RecyclerView.Adapter<ImageTaskAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val ivThumb:     ImageView  = view.findViewById(R.id.iv_thumb)
        val etLabel:     EditText   = view.findViewById(R.id.et_img_label)
        val spinAction:  Spinner    = view.findViewById(R.id.spin_action)
        val sbThreshold: SeekBar    = view.findViewById(R.id.sb_threshold)
        val tvThreshold: TextView   = view.findViewById(R.id.tv_threshold_val)
        val btnCapture:  Button     = view.findViewById(R.id.btn_img_capture)
        val btnDelete:   ImageButton= view.findViewById(R.id.btn_img_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_image_task, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val task = tasks[position]

        holder.etLabel.setText(task.label)
        holder.etLabel.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) task.label = holder.etLabel.text.toString()
        }

        // Thumbnail
        if (task.templateBytes != null) {
            val bmp = BitmapFactory.decodeByteArray(
                task.templateBytes, 0, task.templateBytes!!.size
            )
            task.templateBitmap = bmp
            holder.ivThumb.setImageBitmap(bmp)
            holder.ivThumb.visibility = View.VISIBLE
        } else {
            holder.ivThumb.visibility = View.GONE
        }

        // Action spinner
        val actions = arrayOf("找到后点击", "找到后跳过", "找到后停止")
        holder.spinAction.adapter = ArrayAdapter(
            holder.itemView.context,
            android.R.layout.simple_spinner_dropdown_item,
            actions
        )
        holder.spinAction.setSelection(task.action)
        holder.spinAction.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                task.action = pos
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        // Threshold seekbar (0–100 maps to 0.50–1.00)
        holder.sbThreshold.progress = ((task.threshold - 0.5f) * 200).toInt().coerceIn(0, 100)
        holder.tvThreshold.text = "%.0f%%".format(task.threshold * 100)
        holder.sbThreshold.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                task.threshold = 0.5f + progress / 200f
                holder.tvThreshold.text = "%.0f%%".format(task.threshold * 100)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        holder.btnCapture.setOnClickListener { onCapture(holder.adapterPosition) }
        holder.btnDelete.setOnClickListener  { onDelete(holder.adapterPosition) }
    }

    override fun getItemCount() = tasks.size
}
