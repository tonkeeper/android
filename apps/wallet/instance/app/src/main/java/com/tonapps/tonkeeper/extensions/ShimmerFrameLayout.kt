package com.tonapps.tonkeeper.extensions

import com.facebook.shimmer.Shimmer
import com.facebook.shimmer.ShimmerFrameLayout
import com.tonapps.uikit.color.iconSecondaryColor
import uikit.extensions.withAlpha

fun ShimmerFrameLayout.applyColors() {
    setShimmer(
        Shimmer.ColorHighlightBuilder()
        .setDirection(Shimmer.Direction.LEFT_TO_RIGHT)
        .setBaseColor(context.iconSecondaryColor.withAlpha(.2f))
        .setHighlightColor(context.iconSecondaryColor.withAlpha(.6f))
        .build())
}