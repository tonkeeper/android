package com.tonapps.tonkeeper.fragment.settings.list.holder

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeper.fragment.settings.list.item.SettingsIconItem
import com.tonapps.tonkeeper.fragment.settings.list.item.SettingsItem
import com.tonapps.uikit.color.accentBlueColor
import com.tonapps.uikit.color.iconPrimaryColor
import com.tonapps.uikit.color.iconSecondaryColor
import uikit.extensions.drawable
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
        if (item.secondaryIcon) {
            view.setIconTintColor(context.iconSecondaryColor)
        } else {
            view.setIconTintColor(context.accentBlueColor)
        }
        view.iconRes = item.iconRes
        view.text = getString(item.titleRes)
        view.dot = item.dot
    }

}