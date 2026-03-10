package com.tonapps.tonkeeper.ui.screen.settings.apps.list.holder

import android.view.ViewGroup
import com.tonapps.tonkeeper.ui.screen.settings.apps.list.Item
import com.tonapps.tonkeeperx.R

class EmptyHolder(parent: ViewGroup): Holder<Item.Empty>(parent, R.layout.view_settings_app_empty) {
    override fun onBind(item: Item.Empty) {

    }
}