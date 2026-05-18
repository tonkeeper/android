package com.tonapps.tonkeeper.ui.screen.send.main

import com.tonapps.icu.Coins
import com.tonapps.blockchain.model.legacy.Amount
import com.tonapps.blockchain.model.legacy.Fee
import com.tonapps.blockchain.model.legacy.errors.InsufficientBalanceType
import com.tonapps.deposit.screens.send.state.SendFee
import com.tonapps.deposit.usecase.emulation.TronFeesEmulation
import com.tonapps.blockchain.model.legacy.WalletCurrency

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
        val type: InsufficientBalanceType,
        val tronFees: Boolean = false,
        val tronFeesEmulation: TronFeesEmulation? = null,
    )

    data object Confirm: SendEvent()

    data class Fee(
        val fee: SendFee = SendFee.Ton(
            amount = Fee(0L),
            fiatAmount = Coins.ZERO,
            fiatCurrency = WalletCurrency.DEFAULT
        ),
        val format: CharSequence = "",
        val convertedFormat: CharSequence = "",
        val showToggle: Boolean = false,
        val insufficientFunds: Boolean = false,
        val failed: Boolean,
    )

    data object ResetAddress: SendEvent()
}