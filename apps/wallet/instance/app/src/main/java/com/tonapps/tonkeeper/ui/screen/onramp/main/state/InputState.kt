package com.tonapps.tonkeeper.ui.screen.onramp.main.state

import com.tonapps.icu.Coins
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.core.currency.WalletCurrency

data class InputState(
    val currency: CurrencyInputState,
    val value: Coins
) {

    val decimals: Int
        get() = currency.decimals

    val symbol: String
        get() = currency.symbol

    val address: String
        get() = currency.address

    val fiat: Boolean
        get() = currency.fiat

    companion object {

        fun create(fiat: WalletCurrency): InputState {
            val currency = CurrencyInputState.Fiat(fiat)
            return InputState(currency, Coins.ZERO)
        }

        fun create(token: TokenEntity = TokenEntity.TON): InputState {
            val currency = CurrencyInputState.TONAsset(token)
            return InputState(currency, Coins.ZERO)
        }
    }
}