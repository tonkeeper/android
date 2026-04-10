package com.tonapps.tonkeeper.ui.screen.swap.omniston.state

import com.tonapps.icu.Coins
import com.tonapps.legacy.enteties.AssetsEntity
import com.tonapps.tonkeeper.helper.TwinInput
import com.tonapps.blockchain.model.legacy.BalanceEntity

data class SwapInputsState(
    val input: TwinInput.State = TwinInput.State(),
    val fromToken: AssetsEntity.Token? = null
) {

    val tokenBalance: BalanceEntity?
        get() = fromToken?.token?.balance

    val remaining: Coins by lazy {
        // (fromToken?.balance ?: Coins.ZERO) - fromAmount
        Coins.ZERO
    }

    val isFromTON: Boolean
        get() = fromToken?.token?.isTon ?: false

    val isMaxTON: Boolean by lazy {
        val token = fromToken ?: return@lazy false
        // token.token.isTon && fromAmount == token.token.balance.value
        false
    }

    val insufficientBalance: Boolean
        get() = remaining.isNegative



    val fromFormat: CharSequence by lazy {
        // CurrencyFormatter.format(from.code, fromAmount)
        "ss"
    }

    val isEmpty: Boolean
        get() = false // insufficientBalance || !fromAmount.isPositive
}