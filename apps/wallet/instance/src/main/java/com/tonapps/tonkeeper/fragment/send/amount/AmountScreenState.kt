package com.tonapps.tonkeeper.fragment.send.amount

import com.tonapps.tonkeeper.App
import com.tonapps.wallet.data.core.Currency
import io.tonapi.models.JettonBalance
import ton.wallet.Wallet
import uikit.mvi.UiState

data class AmountScreenState(
    val wallet: Wallet? = null,
    val tonBalance: Float = 0f,
    val amount: Float = 0f,
    val currency: Currency = App.settings.currency,
    val available: String = "",
    val rate: String = "0 ${App.settings.currency.code}",
    val insufficientBalance: Boolean = false,
    val remaining: String = "",
    val canContinue: Boolean = false,
    val maxActive: Boolean = false,
    val jettons: List<JettonBalance> = emptyList(),
    val selectedJetton: JettonBalance? = null,
    val decimals: Int = 9
): UiState() {

    val selectedToken: String
        get() = selectedJetton?.jetton?.symbol ?: "TON"
}