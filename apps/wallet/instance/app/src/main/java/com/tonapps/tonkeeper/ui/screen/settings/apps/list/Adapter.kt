package com.tonapps.tonkeeper.ui.screen.settings.apps.list

import android.view.ViewGroup
import com.tonapps.tonkeeper.ui.screen.settings.apps.list.holder.AppHolder
import com.tonapps.tonkeeper.ui.screen.settings.apps.list.holder.DisconnectAllHolder
import com.tonapps.tonkeeper.ui.screen.settings.apps.list.holder.EmptyHolder
import com.tonapps.uikit.list.BaseListAdapter
import com.tonapps.uikit.list.BaseListHolder
import com.tonapps.uikit.list.BaseListItem
import com.tonapps.wallet.data.dapps.entities.AppEntity

class Adapter(
    private val disconnectApp: (app: AppEntity) -> Unit,
    private val disconnectAll: () -> Unit
): BaseListAdapter() {
    override fun createHolder(parent: ViewGroup, viewType: Int): BaseListHolder<out BaseListItem> {
        return when (viewType) {
            Item.TYPE_APP -> AppHolder(parent, disconnectApp)
            Item.TYPE_DISCONNECT_ALL -> DisconnectAllHolder(parent, disconnectAll)
            Item.TYPE_EMPTY -> EmptyHolder(parent)
            else -> throw IllegalArgumentException("Unknown viewType: $viewType")
        }
    }
}