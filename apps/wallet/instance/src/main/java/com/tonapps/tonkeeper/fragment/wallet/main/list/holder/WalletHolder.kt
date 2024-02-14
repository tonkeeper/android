package com.tonapps.tonkeeper.fragment.wallet.main.list.holder

import android.view.ViewGroup
import androidx.annotation.LayoutRes
import com.tonapps.tonkeeper.fragment.wallet.main.list.item.WalletItem

abstract class WalletHolder<I: WalletItem>(
    parent: ViewGroup,
    @LayoutRes resId: Int
): com.tonapps.uikit.list.BaseListHolder<I>(parent, resId)
