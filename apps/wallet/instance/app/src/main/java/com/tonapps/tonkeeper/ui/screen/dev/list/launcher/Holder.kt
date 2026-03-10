package com.tonapps.tonkeeper.ui.screen.dev.list.launcher

import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.list.BaseListHolder
import uikit.widget.RadioView

class Holder(
    parent: ViewGroup,
    private val callback: (View, Int) -> Unit
): BaseListHolder<Item>(parent, R.layout.view_launcher_icon) {

    private val iconView = findViewById<AppCompatImageView>(R.id.icon)
    private val titleView = findViewById<AppCompatTextView>(R.id.title)
    private val radio = findViewById<RadioView>(R.id.radio)

    init {
        itemView.setOnClickListener { radio.toggle() }
    }

    override fun onBind(item: Item) {
        iconView.setImageResource(item.iconRes)
        titleView.text = item.title
        radio.checked = item.isEnabled(context)
        radio.doOnCheckedChanged = { checked ->
            if (checked && !item.isEnabled(context)) {
                callback(radio, bindingAdapterPosition)
            }
        }
    }

}