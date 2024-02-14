package com.tonapps.tonkeeper.fragment.passcode.create.pager

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class InputAdapter: RecyclerView.Adapter<InputHolder>() {

    private val types = InputType.entries.toTypedArray()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InputHolder {
        return InputHolder(parent)
    }

    override fun getItemCount(): Int {
        return types.size
    }

    override fun onBindViewHolder(holder: InputHolder, position: Int) {
        holder.setInputType(types[position])
    }
}