package com.tonapps.tonkeeper.ui.screen.browser.search.list

import android.view.ViewGroup
import com.tonapps.tonkeeper.ui.screen.browser.search.list.holder.AppHolder
import com.tonapps.tonkeeper.ui.screen.browser.search.list.holder.LinkHolder
import com.tonapps.tonkeeper.ui.screen.browser.search.list.holder.SearchHolder
import com.tonapps.tonkeeper.ui.screen.browser.search.list.holder.TitleHolder
import com.tonapps.uikit.list.BaseListAdapter
import com.tonapps.uikit.list.BaseListHolder
import com.tonapps.uikit.list.BaseListItem

class Adapter(
    private val onClick: (title: String, url: String) -> Unit
): BaseListAdapter() {
    override fun createHolder(parent: ViewGroup, viewType: Int): BaseListHolder<out BaseListItem> {
        return when(viewType) {
            Item.TYPE_TITLE -> TitleHolder(parent)
            Item.TYPE_SEARCH -> SearchHolder(parent, onClick)
            Item.TYPE_LINK -> LinkHolder(parent, onClick)
            Item.TYPE_APP -> AppHolder(parent, onClick)
            else -> throw IllegalArgumentException("Unknown viewType: $viewType")
        }
    }

}