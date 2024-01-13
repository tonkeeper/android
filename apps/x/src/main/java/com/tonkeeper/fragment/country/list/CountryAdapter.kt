package com.tonkeeper.fragment.country.list

import android.view.ViewGroup
import uikit.list.BaseListHolder
import uikit.list.BaseListItem
import uikit.list.BaseListAdapter

class CountryAdapter(
    private val onClick: (item: CountryItem) -> Unit
): BaseListAdapter() {

    override fun createHolder(parent: ViewGroup, viewType: Int): BaseListHolder<out BaseListItem> {
        return CountryHolder(parent, onClick)
    }
}