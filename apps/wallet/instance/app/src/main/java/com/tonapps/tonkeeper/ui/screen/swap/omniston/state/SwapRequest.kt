package com.tonapps.tonkeeper.ui.screen.swap.omniston.state

import com.tonapps.icu.Coins
import com.tonapps.tonkeeper.helper.TwinInput
import com.tonapps.wallet.api.SwapAssetParam
import com.tonapps.wallet.data.core.currency.WalletCurrency

data class SwapRequest(
    val type: TwinInput.Type,
    val amount: Coins,
    val from: WalletCurrency,
    val to: WalletCurrency,
) {

    val isEmpty: Boolean
        get() = !amount.isPositive

    val fromParam: SwapAssetParam by lazy {
        val amount = if (type == TwinInput.Type.Send) amount.toNano(from.decimals) else null
        SwapAssetParam(from.address, amount)
    }

    val toParam: SwapAssetParam by lazy {
        val amount = if (type == TwinInput.Type.Receive) amount.toNano(to.decimals) else null
        SwapAssetParam(to.address, amount)
    }
}