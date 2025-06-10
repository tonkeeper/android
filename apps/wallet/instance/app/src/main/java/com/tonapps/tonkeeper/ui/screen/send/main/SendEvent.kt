package com.tonapps.tonkeeper.ui.screen.send.main

import com.tonapps.icu.Coins
import com.tonapps.tonkeeper.core.Amount
import com.tonapps.tonkeeper.core.Fee
import com.tonapps.tonkeeper.ui.screen.send.main.helper.InsufficientBalanceType
import com.tonapps.tonkeeper.ui.screen.send.main.state.SendFee
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.core.currency.WalletCurrency

sealed class SendEvent {
    data class Failed(val throwable: Throwable): SendEvent()
    data object Canceled: SendEvent()
    data object Success: SendEvent()
    data object Loading: SendEvent()
    data class InsufficientBalance(
        val balance: Amount,
        val required: Amount,
        val withRechargeBattery: Boolean,
        val singleWallet: Boolean,
        val type: InsufficientBalanceType
    ): SendEvent()
    data object Confirm: SendEvent()

    data class Fee(
        val fee: SendFee = SendFee.Ton(
            amount = Fee(0L),
            extra = 0L,
            fiatAmount = Coins.ZERO,
            fiatCurrency = WalletCurrency.DEFAULT
        ),
        val format: CharSequence = "",
        val convertedFormat: CharSequence = "",
        val showToggle: Boolean = false,
        val insufficientFunds: Boolean = false,
        val failed: Boolean,
    ): SendEvent()

    data object ResetAddress: SendEvent()
}