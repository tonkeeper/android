package com.tonapps.tonkeeper.ui.screen.browser.main.list.connected

import android.view.ViewGroup
import com.tonapps.uikit.list.BaseListAdapter
import com.tonapps.uikit.list.BaseListHolder
import com.tonapps.uikit.list.BaseListItem

class ConnectedAdapter(
    private val onLongClick: (ConnectedItem) -> Unit
): BaseListAdapter() {
    override fun createHolder(parent: ViewGroup, viewType: Int): BaseListHolder<out BaseListItem> {
        return ConnectedAppHolder(parent, onLongClick)
    }
}
