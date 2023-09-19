package com.tonkeeper.fragment.wallet.creating.list

import android.view.ViewGroup
import androidx.annotation.LayoutRes
import com.tonkeeper.R
import com.tonkeeper.uikit.list.BaseListHolder

internal open class PagerHolder(
    parent: ViewGroup,
    @LayoutRes resId: Int
): BaseListHolder<PagerItem>(parent, resId) {

    class Generating(parent: ViewGroup): PagerHolder(parent, R.layout.view_wallet_generating)
    class Created(parent: ViewGroup): PagerHolder(parent, R.layout.view_wallet_created)

    class Attention(parent: ViewGroup): PagerHolder(parent, R.layout.view_wallet_attention)

    override fun onBind(item: PagerItem) {

    }

}