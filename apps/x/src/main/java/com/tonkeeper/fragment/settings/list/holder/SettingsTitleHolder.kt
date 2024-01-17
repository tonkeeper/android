package com.tonkeeper.fragment.settings.list.holder

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeperx.R
import com.tonkeeper.fragment.settings.list.item.SettingsTitleItem

class SettingsTitleHolder(parent: ViewGroup): SettingsHolder<SettingsTitleItem>(parent, R.layout.view_settings_title) {

    private val titleView = findViewById<AppCompatTextView>(R.id.title)

    override fun onBind(item: SettingsTitleItem) {
        titleView.setText(item.titleRes)
    }
}