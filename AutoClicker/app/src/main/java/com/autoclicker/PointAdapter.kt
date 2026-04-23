package com.autoclicker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView

class PointAdapter(
    private val points: MutableList<ClickPoint>,
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<PointAdapter.VH>() {

    private var activeIndex = -1

    fun setActiveIndex(idx: Int) {
        val old = activeIndex
        activeIndex = idx
        if (old >= 0 && old < itemCount) notifyItemChanged(old)
        if (idx >= 0 && idx < itemCount) notifyItemChanged(idx)
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvIndex:  TextView   = view.findViewById(R.id.tv_index)
        val etLabel:  EditText   = view.findViewById(R.id.et_label)
        val etX:      EditText   = view.findViewById(R.id.et_x)
        val etY:      EditText   = view.findViewById(R.id.et_y)
        val etDelay:  EditText   = view.findViewById(R.id.et_delay)
        val btnDel:   ImageButton = view.findViewById(R.id.btn_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_point, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val point    = points[position]
        val isActive = position == activeIndex

        holder.tvIndex.text = (position + 1).toString()
        holder.etLabel.setText(point.label)
        holder.etX.setText(point.x.toString())
        holder.etY.setText(point.y.toString())
        holder.etDelay.setText(point.delayMs.toString())

        // Highlight active row
        holder.itemView.setBackgroundColor(
            if (isActive) 0x1400DC96.toInt() else 0x00000000
        )
        holder.tvIndex.setTextColor(
            if (isActive) 0xFF00DC96.toInt() else 0xFF2A5040.toInt()
        )

        // Persist edits on focus loss
        holder.etLabel.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) point.label = holder.etLabel.text.toString()
        }
        holder.etX.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) point.x = holder.etX.text.toString().toIntOrNull() ?: point.x
        }
        holder.etY.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) point.y = holder.etY.text.toString().toIntOrNull() ?: point.y
        }
        holder.etDelay.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) point.delayMs = holder.etDelay.text.toString().toLongOrNull() ?: point.delayMs
        }

        holder.btnDel.setOnClickListener { onDelete(holder.adapterPosition) }
    }

    override fun getItemCount() = points.size
}
