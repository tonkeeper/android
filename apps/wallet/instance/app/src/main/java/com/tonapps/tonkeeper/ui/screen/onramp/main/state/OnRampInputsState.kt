package com.tonapps.tonkeeper.ui.screen.onramp.main.state

import com.tonapps.icu.Coins

data class OnRampInputsState(
    val from: Coins = Coins.ZERO,
    val to: Coins = Coins.ZERO,
) {

    val isEmpty: Boolean
        get() = from.isZero && to.isZero
}