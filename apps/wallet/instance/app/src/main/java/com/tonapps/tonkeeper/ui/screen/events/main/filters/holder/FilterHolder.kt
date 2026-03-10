package com.tonapps.tonkeeper.ui.screen.events.main.filters.holder

import android.view.ViewGroup
import com.tonapps.tonkeeper.ui.screen.events.main.filters.FilterItem
import com.tonapps.wallet.localization.Localization
import uikit.extensions.dp
import uikit.extensions.setPaddingHorizontal

class FilterHolder(
    parent: ViewGroup,
    private val onClick: (item: FilterItem) -> Unit
): Holder<FilterItem>(parent) {

    init {
        itemView.setPaddingHorizontal(14.dp)
    }

    override fun onBind(item: FilterItem) {
        itemView.setOnClickListener { onClick(item) }
        titleView.setText(item.localization)
        updateSelected(item)
    }

    fun updateSelected(item: FilterItem) {
        setSelected(item.selected)
    }

}