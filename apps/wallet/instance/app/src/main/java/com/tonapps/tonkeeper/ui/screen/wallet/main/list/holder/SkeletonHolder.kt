package com.tonapps.tonkeeper.ui.screen.wallet.main.list.holder

import android.view.ViewGroup
import com.facebook.shimmer.Shimmer
import com.facebook.shimmer.ShimmerFrameLayout
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.iconSecondaryColor
import uikit.extensions.withAlpha

class SkeletonHolder(parent: ViewGroup): Holder<Item.Skeleton>(parent, R.layout.view_wallet_skeleton) {

    private val shimmerView = findViewById<ShimmerFrameLayout>(R.id.shimmer)

    init {
        shimmerView.setShimmer(Shimmer.ColorHighlightBuilder()
            .setDirection(Shimmer.Direction.LEFT_TO_RIGHT)
            .setBaseColor(context.iconSecondaryColor.withAlpha(.2f))
            .setHighlightColor(context.iconSecondaryColor.withAlpha(.6f))
            .build())
    }

    override fun onBind(item: Item.Skeleton) {

    }

}