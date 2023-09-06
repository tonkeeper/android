package com.tonkeeper.ui.list.wallet.holder

import android.view.ViewGroup
import androidx.annotation.LayoutRes
import com.tonkeeper.ui.list.wallet.item.WalletCellItem
import com.tonkeeper.ui.drawable.CellDrawable
import com.tonkeeper.ui.list.base.BaseListItem

abstract class WalletCellHolder<I: WalletCellItem>(
    parent: ViewGroup,
    @LayoutRes resId: Int
): WalletHolder<I>(parent, resId) {

    override fun bind(item: BaseListItem) {
        super.bind(item)
        val position = (item as WalletCellItem).position
        itemView.background = CellDrawable(position)
    }
}