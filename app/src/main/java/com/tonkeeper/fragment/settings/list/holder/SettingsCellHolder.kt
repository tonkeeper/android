package com.tonkeeper.fragment.settings.list.holder

import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import com.tonkeeper.R
import com.tonkeeper.fragment.currency.CurrencyScreen
import com.tonkeeper.fragment.settings.list.item.SettingsCellItem
import com.tonkeeper.uikit.drawable.CellBackgroundDrawable
import com.tonkeeper.uikit.navigation.Navigation.Companion.nav

class SettingsCellHolder(parent: ViewGroup): SettingsHolder<SettingsCellItem>(parent, R.layout.view_settings_cell) {

    private val textView = itemView.findViewById<AppCompatTextView>(R.id.text)
    private val iconView = itemView.findViewById<AppCompatImageView>(R.id.icon)
    private val rightView = itemView.findViewById<AppCompatTextView>(R.id.right)

    override fun onBind(item: SettingsCellItem) {
        itemView.setOnClickListener { itemClick(item.id) }
        itemView.background = CellBackgroundDrawable(itemView.context, item.position)
        textView.setText(item.titleRes)
        if (item.hasIcon) {
            iconView.visibility = View.VISIBLE
            iconView.setImageResource(item.iconRes)
            rightView.visibility = View.GONE
        } else {
            iconView.visibility = View.GONE
            rightView.visibility = View.VISIBLE
            rightView.text = item.right
        }
    }

    private fun itemClick(id: Int) {
        if (id == SettingsCellItem.CURRENCY_ID) {
            context.nav()?.add(CurrencyScreen.newInstance())
        }
    }

}