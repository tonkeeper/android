package com.tonapps.tonkeeper.ui.screen.send.main

import com.tonapps.icu.Coins
import com.tonapps.tonkeeper.core.Amount
import com.tonapps.tonkeeper.ui.screen.send.main.helper.InsufficientBalanceType
import com.tonapps.wallet.api.entity.TokenEntity

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
        val balance: Coins,
        val amount: Coins,
        val fee: com.tonapps.tonkeeper.core.Fee,
        val format: CharSequence,
        val convertedFormat: CharSequence,
        val isBattery: Boolean,
        val isGasless: Boolean,
        val showGaslessToggle: Boolean,
        val tokenSymbol: String,
        val insufficientFunds: Boolean
    ): SendEvent()

    data object ResetAddress: SendEvent()
}