package com.tonkeeper.fragment.currency.list

import android.view.ViewGroup
import com.tonkeeper.uikit.list.BaseListAdapter
import com.tonkeeper.uikit.list.BaseListHolder
import com.tonkeeper.uikit.list.BaseListItem

class CurrencyAdapter(items: List<CurrencyItem>): BaseListAdapter<CurrencyItem>(items) {
    override fun createHolder(parent: ViewGroup, viewType: Int): BaseListHolder<out BaseListItem> {
        return CurrencyHolder(parent)
    }
}