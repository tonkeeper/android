package com.tonapps.tonkeeper.fragment.currency.list

import android.view.ViewGroup

class CurrencyAdapter(
    private val onClick: (item: CurrencyItem) -> Unit
): com.tonapps.uikit.list.BaseListAdapter() {

    override fun createHolder(parent: ViewGroup, viewType: Int): com.tonapps.uikit.list.BaseListHolder<out com.tonapps.uikit.list.BaseListItem> {
        return CurrencyHolder(parent, onClick)
    }
}