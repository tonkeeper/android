package com.tonkeeper.fragment.passcode.create.pager

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeperx.R
import uikit.extensions.inflate
import uikit.widget.PinInputView

class InputHolder(
    parent: ViewGroup
): RecyclerView.ViewHolder(parent.inflate(R.layout.view_passcode_input)) {

    private val titleView = itemView.findViewById<AppCompatTextView>(R.id.title)
    private val inputView = itemView.findViewById<PinInputView>(R.id.input)

    fun setInputType(type: InputType) {
        setTitle(type)
    }

    fun setCount(count: Int) {
        inputView.setCount(count)
    }

    fun setError() {
        inputView.setError()
    }

    fun setSuccess() {
        inputView.setSuccess()
    }

    private fun setTitle(type: InputType) {
        if (type == InputType.ENTER) {
            titleView.setText(R.string.passcode_create)
        } else {
            titleView.setText(R.string.passcode_re_enter)
        }
    }
}
