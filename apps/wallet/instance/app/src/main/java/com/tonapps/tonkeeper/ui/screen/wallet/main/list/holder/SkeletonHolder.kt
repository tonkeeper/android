package com.tonapps.tonkeeper.ui.screen.wallet.main.list.holder

import android.view.ViewGroup
import com.facebook.shimmer.ShimmerFrameLayout
import com.tonapps.tonkeeper.extensions.applyColors
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.Item
import com.tonapps.tonkeeperx.R

class SkeletonHolder(parent: ViewGroup): Holder<Item.Skeleton>(parent, R.layout.view_wallet_skeleton) {

    private val shimmerView = findViewById<ShimmerFrameLayout>(R.id.shimmer)

    init {
        shimmerView.applyColors()
    }

    override fun onBind(item: Item.Skeleton) {

    }

}