package com.tonkeeper.fragment.wallet.main.list.holder

import android.view.ViewGroup
import androidx.annotation.LayoutRes
import com.tonkeeper.fragment.wallet.main.list.item.WalletCellItem
import uikit.drawable.CellBackgroundDrawable
import uikit.extensions.getDimensionPixelSize
import uikit.list.BaseListItem

abstract class WalletCellHolder<I: WalletCellItem>(
    parent: ViewGroup,
    @LayoutRes resId: Int
): WalletHolder<I>(parent, resId) {

    private val offset = context.getDimensionPixelSize(uikit.R.dimen.offsetMedium)

    override fun bind(item: BaseListItem) {
        super.bind(item)
        val position = (item as WalletCellItem).position
        itemView.background = CellBackgroundDrawable(context, position)
    }
}