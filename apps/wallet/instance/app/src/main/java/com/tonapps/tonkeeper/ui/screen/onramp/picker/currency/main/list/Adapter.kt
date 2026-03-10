package com.tonapps.tonkeeper.ui.screen.onramp.picker.currency.main.list

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeper.ui.screen.onramp.picker.currency.main.list.holder.CurrencyHolder
import com.tonapps.tonkeeper.ui.screen.onramp.picker.currency.main.list.holder.MoreHolder
import com.tonapps.tonkeeper.ui.screen.onramp.picker.currency.main.list.holder.TitleHolder
import com.tonapps.uikit.list.BaseListAdapter
import com.tonapps.uikit.list.BaseListHolder
import com.tonapps.uikit.list.BaseListItem

class Adapter(
    private val onCurrencyClick: ((Item.Currency) -> Unit),
    private val onMoreClick: ((Item.More) -> Unit),
): BaseListAdapter() {

    override fun createHolder(parent: ViewGroup, viewType: Int): BaseListHolder<out BaseListItem> {
        return when(viewType) {
            Item.TYPE_TITLE -> TitleHolder(parent)
            Item.TYPE_CURRENCY -> CurrencyHolder(parent, onCurrencyClick)
            Item.TYPE_MORE -> MoreHolder(parent, onMoreClick)
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        recyclerView.isNestedScrollingEnabled = true
    }
}