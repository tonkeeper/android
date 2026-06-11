package com.tonapps.tonkeeper.ui.screen.swap.omniston.state

import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.legacy.enteties.AssetsEntity
import com.tonapps.blockchain.model.legacy.BalanceEntity

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