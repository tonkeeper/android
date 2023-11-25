package com.tonkeeper.fragment.country.list

import android.view.ViewGroup
import uikit.list.BaseListHolder
import uikit.list.BaseListItem
import uikit.list.DiffListAdapter

class CountryAdapter(
    items: List<CountryItem>,
    private val onClick: (item: CountryItem) -> Unit
): DiffListAdapter(items) {
    override fun createHolder(parent: ViewGroup, viewType: Int): BaseListHolder<out BaseListItem> {
        return CountryHolder(parent, onClick)
    }

}