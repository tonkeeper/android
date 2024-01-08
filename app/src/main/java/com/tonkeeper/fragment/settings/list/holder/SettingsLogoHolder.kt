package com.tonkeeper.fragment.settings.list.holder

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeperx.BuildConfig
import com.tonapps.tonkeeperx.R
import com.tonkeeper.fragment.dev.DevFragment
import com.tonkeeper.fragment.settings.list.item.SettingsItem
import com.tonkeeper.fragment.settings.list.item.SettingsLogoItem

class SettingsLogoHolder(
    parent: ViewGroup,
    onClick: ((SettingsItem) -> Unit)?
): SettingsHolder<SettingsLogoItem>(parent, R.layout.view_settings_logo, onClick) {

    private val versionView = findViewById<AppCompatTextView>(R.id.version)

    init {
        itemView.setOnLongClickListener {
            if (BuildConfig.DEBUG) {
                nav?.add(DevFragment.newInstance())
                true
            } else {
                false
            }
        }
    }

    override fun onBind(item: SettingsLogoItem) {
        itemView.setOnClickListener { onClick?.invoke(item) }
        versionView.text = context.getString(R.string.version, item.versionName, item.versionCode)
    }
}