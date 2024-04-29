package com.tonapps.tonkeeper.fragment.country.list

import android.view.ViewGroup

class CountryAdapter(
    private val onClick: (item: CountryItem) -> Unit
): com.tonapps.uikit.list.BaseListAdapter() {

    override fun createHolder(parent: ViewGroup, viewType: Int): com.tonapps.uikit.list.BaseListHolder<out com.tonapps.uikit.list.BaseListItem> {
        return CountryHolder(parent, onClick)
    }
}