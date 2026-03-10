package com.tonapps.tonkeeper.ui.screen.swap.omniston.state

import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.core.entities.AssetsEntity
import com.tonapps.wallet.api.entity.BalanceEntity

data class SwapTokenState(
    val fromToken: AssetsEntity.Token? = null,
    val remaining: Coins = Coins.ZERO,
) {

    val tokenBalance: BalanceEntity?
        get() = fromToken?.token?.balance

    val balance: Coins
        get() = tokenBalance?.value ?: Coins.ZERO

    val insufficientBalance: Boolean
        get() = remaining.isNegative

    val isTon: Boolean
        get() = fromToken?.token?.isTon == true

    val remainingFormat: CharSequence? by lazy {
        CurrencyFormatter.format("", remaining)
    }
}