package com.tonapps.tonkeeper.fragment.settings.list.holder

import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.wallet.localization.Localization
import com.tonapps.tonkeeperx.R
import com.tonapps.tonkeeper.fragment.dev.DevFragment
import com.tonapps.tonkeeper.fragment.settings.list.item.SettingsItem
import com.tonapps.tonkeeper.fragment.settings.list.item.SettingsLogoItem
import uikit.navigation.Navigation

class SettingsLogoHolder(
    parent: ViewGroup,
    onClick: ((SettingsItem, View) -> Unit)?
): SettingsHolder<SettingsLogoItem>(parent, R.layout.view_settings_logo, onClick) {

    private val versionView = findViewById<AppCompatTextView>(R.id.version)

    init {
        itemView.setOnLongClickListener {
            Navigation.from(context)?.add(DevFragment.newInstance())
            true
        }
    }

    override fun onBind(item: SettingsLogoItem) {
        itemView.setOnClickListener { onClick?.invoke(item, it) }
        versionView.text = context.getString(Localization.version, item.versionName, item.versionCode)
    }
}