package com.tonkeeper.fragment.settings.list.holder

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonkeeper.R
import com.tonkeeper.fragment.settings.list.item.SettingsItem
import com.tonkeeper.fragment.settings.list.item.SettingsLogoItem

class SettingsLogoHolder(
    parent: ViewGroup,
    onClick: ((SettingsItem) -> Unit)?
): SettingsHolder<SettingsLogoItem>(parent, R.layout.view_settings_logo, onClick) {

    private val versionView = findViewById<AppCompatTextView>(R.id.version)

    override fun onBind(item: SettingsLogoItem) {
        itemView.setOnClickListener { onClick?.invoke(item) }
        versionView.text = context.getString(R.string.version, item.versionName, item.versionCode)
    }
}