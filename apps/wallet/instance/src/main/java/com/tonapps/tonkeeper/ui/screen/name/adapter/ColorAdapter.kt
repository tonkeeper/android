package com.tonapps.tonkeeper.ui.screen.name.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeper.data.AccountColor
import com.tonapps.tonkeeperx.R
import uikit.widget.ColorView

class ColorAdapter(
    private val listener: (Int) -> Unit
): RecyclerView.Adapter<ColorAdapter.Holder>() {

    var activeColor: Int = Color.TRANSPARENT
        set(value) {
            val oldIndex = AccountColor.all.indexOf(field)
            val newIndex = AccountColor.all.indexOf(value)
            field = value
            if (oldIndex >= 0) {
                notifyItemChanged(oldIndex)
            }
            notifyItemChanged(newIndex)
        }

    inner class Holder(parent: ViewGroup): RecyclerView.ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.view_name_color, parent, false)) {

        private val colorView = itemView.findViewById<ColorView>(R.id.color)

        fun bind(color: Int) {
            itemView.setOnClickListener {
                colorView.active = true
                activeColor = color
                listener(color)
            }
            colorView.color = color
            colorView.active = activeColor == color
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(parent)

    override fun getItemCount() = AccountColor.all.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(AccountColor.all[position])
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        recyclerView.setHasFixedSize(true)
        recyclerView.isNestedScrollingEnabled = false
        recyclerView.itemAnimator = null
        recyclerView.layoutAnimation = null
    }
}