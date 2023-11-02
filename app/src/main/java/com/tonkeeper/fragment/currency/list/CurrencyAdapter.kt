package com.tonkeeper.fragment.currency.list

import android.view.ViewGroup
import uikit.list.BaseListAdapter
import uikit.list.BaseListHolder
import uikit.list.BaseListItem
import uikit.list.DiffListAdapter

class CurrencyAdapter(
    items: List<CurrencyItem>,
    private val onClick: (item: CurrencyItem) -> Unit
): DiffListAdapter(items) {

    override fun createHolder(parent: ViewGroup, viewType: Int): BaseListHolder<out BaseListItem> {
        return CurrencyHolder(parent, onClick)
    }
}