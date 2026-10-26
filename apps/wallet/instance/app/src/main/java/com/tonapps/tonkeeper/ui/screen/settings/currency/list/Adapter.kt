package com.tonapps.tonkeeper.ui.screen.settings.currency.list

import android.view.ViewGroup
import com.tonapps.uikit.list.BaseListAdapter
import com.tonapps.uikit.list.BaseListHolder
import com.tonapps.uikit.list.BaseListItem
import com.tonapps.wallet.api.entity.CurrencyEntity

class Adapter(
    private val onClick: (currency: CurrencyEntity) -> Unit
): BaseListAdapter() {

    val selectedIndex: Int
        get() = currentList.indexOfFirst { (it as? Item)?.selected == true }

    override fun createHolder(parent: ViewGroup, viewType: Int): BaseListHolder<out BaseListItem> {
        return Holder(parent, onClick)
    }
}