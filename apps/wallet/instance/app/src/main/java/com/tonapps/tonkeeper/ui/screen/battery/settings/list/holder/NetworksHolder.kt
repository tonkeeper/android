package com.tonapps.tonkeeper.ui.screen.battery.settings.list.holder

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.ui.screen.battery.settings.list.Item
import com.tonapps.tonkeeperx.R

class NetworksHolder(
    parent: ViewGroup
): Holder<Item.Networks>(parent, R.layout.view_battery_settings_networks) {

    private val textView = findViewById<AppCompatTextView>(R.id.text)

    override fun onBind(item: Item.Networks) {
        textView.text = item.text
    }
}