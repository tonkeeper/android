package com.tonkeeper.fragment.wallet.main.list.holder

import android.view.ViewGroup
import androidx.annotation.LayoutRes
import com.tonkeeper.fragment.wallet.main.list.item.WalletItem
import uikit.list.BaseListHolder

abstract class WalletHolder<I: WalletItem>(
    parent: ViewGroup,
    @LayoutRes resId: Int
): BaseListHolder<I>(parent, resId)
