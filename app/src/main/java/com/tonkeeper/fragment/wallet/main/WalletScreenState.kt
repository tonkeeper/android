package com.tonkeeper.fragment.wallet.main

import com.tonkeeper.fragment.wallet.main.pager.WalletScreenItem
import ton.SupportedCurrency
import ton.Ton
import uikit.mvi.AsyncState
import uikit.mvi.UiState

data class WalletScreenState(
    val asyncState: AsyncState = AsyncState.Default,
    val title: String? = null,
    val currency: SupportedCurrency = SupportedCurrency.USD,
    val address: String = "",
    val tonBalance: Long = 0L,
    val displayBalance: String = "",
    val pages: List<WalletScreenItem> = emptyList()
): UiState() {


    val shortAddress: String by lazy {
        if (address.length < 8) return@lazy address

        address.substring(0, 4) + "…" + address.substring(address.length - 4, address.length)
    }

}