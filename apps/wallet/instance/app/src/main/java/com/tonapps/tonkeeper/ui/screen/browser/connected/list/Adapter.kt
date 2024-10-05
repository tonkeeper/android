package com.tonapps.tonkeeper.ui.screen.browser.connected.list

import android.view.ViewGroup
import com.tonapps.uikit.list.BaseListAdapter
import com.tonapps.uikit.list.BaseListHolder
import com.tonapps.uikit.list.BaseListItem
import com.tonapps.wallet.data.dapps.entities.AppEntity

class Adapter(
    private val onLongClick: (AppEntity) -> Unit
): BaseListAdapter() {
    override fun createHolder(parent: ViewGroup, viewType: Int): BaseListHolder<out BaseListItem> {
        return AppHolder(parent, onLongClick)
    }
}