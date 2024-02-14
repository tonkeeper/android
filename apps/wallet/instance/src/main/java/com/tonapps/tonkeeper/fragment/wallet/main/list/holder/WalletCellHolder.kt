package com.tonapps.tonkeeper.fragment.wallet.main.list.holder

import android.view.ViewGroup
import androidx.annotation.LayoutRes
import com.tonapps.tonkeeper.fragment.wallet.main.list.item.WalletCellItem
import uikit.extensions.getDimensionPixelSize
import com.tonapps.uikit.list.BaseListItem
import uikit.extensions.drawable

abstract class WalletCellHolder<I: WalletCellItem>(
    parent: ViewGroup,
    @LayoutRes resId: Int
): WalletHolder<I>(parent, resId) {

    private val offset = context.getDimensionPixelSize(uikit.R.dimen.offsetMedium)

    override fun bind(item: BaseListItem) {
        super.bind(item)
        val position = (item as WalletCellItem).position
        itemView.background = position.drawable(context)
    }
}