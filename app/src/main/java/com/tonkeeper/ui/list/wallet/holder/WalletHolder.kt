package com.tonkeeper.ui.list.wallet.holder

import android.view.ViewGroup
import androidx.annotation.LayoutRes
import com.tonkeeper.ui.list.wallet.item.WalletItem
import com.tonkeeper.ui.list.base.BaseListHolder

abstract class WalletHolder<I: WalletItem>(
    parent: ViewGroup,
    @LayoutRes resId: Int
): BaseListHolder<I>(parent, resId)