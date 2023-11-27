package com.tonkeeper.fragment.currency.list

import android.view.ViewGroup
import uikit.list.BaseListHolder
import uikit.list.BaseListItem
import uikit.list.BaseListAdapter

class CurrencyAdapter(
    private val onClick: (item: CurrencyItem) -> Unit
): BaseListAdapter() {

    override fun createHolder(parent: ViewGroup, viewType: Int): BaseListHolder<out BaseListItem> {
        return CurrencyHolder(parent, onClick)
    }
}