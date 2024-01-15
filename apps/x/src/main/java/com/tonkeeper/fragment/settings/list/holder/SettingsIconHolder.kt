package com.tonkeeper.fragment.settings.list.holder

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tonkeeper.fragment.settings.list.item.SettingsIconItem
import com.tonkeeper.fragment.settings.list.item.SettingsItem
import uikit.list.ListCell.Companion.drawable
import uikit.widget.item.ItemIconView

class SettingsIconHolder(
    parent: ViewGroup,
    onClick: ((SettingsItem, View) -> Unit)?
): SettingsHolder<SettingsIconItem>(ItemIconView(parent.context), onClick) {

    private val view = itemView as ItemIconView

    init {
        view.layoutParams = RecyclerView.LayoutParams(
            RecyclerView.LayoutParams.MATCH_PARENT,
            RecyclerView.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onBind(item: SettingsIconItem) {
        view.background = item.position.drawable(context)
        view.setOnClickListener { onClick?.invoke(item, it) }
        if (item.colorRes != 0) {
            view.setIconTintColor(context.getColor(item.colorRes))
        }
        view.iconRes = item.iconRes
        view.text = getString(item.titleRes)
        view.dot = item.dot
    }

}