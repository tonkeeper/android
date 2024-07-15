package com.tonapps.tonkeeper.ui.screen.wallet.picker.list.holder

import android.view.View
import android.view.ViewGroup
import com.tonapps.tonkeeper.ui.screen.wallet.picker.list.Item
import com.tonapps.tonkeeperx.R
import uikit.extensions.drawable

class SkeletonHolder(
    parent: ViewGroup
): Holder<Item.Skeleton>(parent, R.layout.view_wallet_item) {

    init {
        findViewById<View>(R.id.wallet_color).visibility = View.GONE
    }

    override fun onBind(item: Item.Skeleton) {
        itemView.background = item.position.drawable(context)
    }
}