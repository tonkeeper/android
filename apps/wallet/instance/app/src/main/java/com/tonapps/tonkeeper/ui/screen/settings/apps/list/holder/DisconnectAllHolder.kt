package com.tonapps.tonkeeper.ui.screen.settings.apps.list.holder

import android.view.View
import android.view.ViewGroup
import com.tonapps.tonkeeper.ui.screen.settings.apps.list.Item
import com.tonapps.tonkeeperx.R

class DisconnectAllHolder(
    parent: ViewGroup,
    private val disconnectAll: () -> Unit
): Holder<Item.DisconnectAll>(parent, R.layout.view_settings_app_disconnect) {

    init {
        findViewById<View>(R.id.button).setOnClickListener { disconnectAll() }
    }

    override fun onBind(item: Item.DisconnectAll) {

    }
}