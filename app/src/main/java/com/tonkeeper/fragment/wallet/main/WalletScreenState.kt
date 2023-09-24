package com.tonkeeper.fragment.wallet.main

import com.tonkeeper.ton.SupportedCurrency
import com.tonkeeper.ton.Ton
import com.tonkeeper.uikit.mvi.AsyncState
import com.tonkeeper.uikit.mvi.UiState

data class WalletScreenState(
    val asyncState: AsyncState = AsyncState.Default,
    val currency: SupportedCurrency = SupportedCurrency.USD,
    val address: String = "0",
    val tonBalance: Ton = Ton.ZERO,
    val displayBalance: String = "0",
): UiState() {


    val shortAddress: String by lazy {
        if (address.length < 8) return@lazy address

        address.substring(0, 4) + "…" + address.substring(address.length - 4, address.length)
    }

}