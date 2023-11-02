package com.tonkeeper.fragment.settings.list.holder

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tonkeeper.fragment.settings.list.item.SettingsIconItem
import com.tonkeeper.fragment.settings.list.item.SettingsItem
import uikit.widget.item.ItemIconView

class SettingsIconHolder(
    parent: ViewGroup,
    onClick: ((SettingsItem) -> Unit)?
): SettingsHolder<SettingsIconItem>(ItemIconView(parent.context), onClick) {

    private val view = itemView as ItemIconView

    init {
        view.layoutParams = RecyclerView.LayoutParams(
            RecyclerView.LayoutParams.MATCH_PARENT,
            RecyclerView.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onBind(item: SettingsIconItem) {
        view.setOnClickListener { onClick?.invoke(item) }
        view.iconRes = item.iconRes
        view.text = getString(item.titleRes)
    }

}