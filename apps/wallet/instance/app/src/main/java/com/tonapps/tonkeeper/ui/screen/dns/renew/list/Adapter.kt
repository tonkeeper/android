package com.tonapps.tonkeeper.ui.screen.dns.renew.list

import android.view.ViewGroup
import com.tonapps.tonkeeper.ui.screen.dns.renew.list.holder.Holder
import com.tonapps.uikit.list.BaseListAdapter
import com.tonapps.uikit.list.BaseListHolder
import com.tonapps.uikit.list.BaseListItem

class Adapter: BaseListAdapter() {

    override fun createHolder(parent: ViewGroup, viewType: Int): BaseListHolder<out BaseListItem> {
        return Holder(parent)
    }
}