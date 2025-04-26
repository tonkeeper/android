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
        val balance: Coins = Coins.ZERO,
        val amount: Coins = Coins.ZERO,
        val fee: com.tonapps.tonkeeper.core.Fee = com.tonapps.tonkeeper.core.Fee(0L),
        val format: CharSequence = "",
        val convertedFormat: CharSequence = "",
        val isBattery: Boolean = false,
        val isGasless: Boolean = false,
        val showGaslessToggle: Boolean = false,
        val tokenSymbol: String = "",
        val insufficientFunds: Boolean = false,
        val failed: Boolean,
        val charges: Int? = null,
        val chargesFormat: CharSequence? = null,
        val chargesBalance: Int? = null,
        val chargesBalanceFormat: CharSequence? = null,
    ): SendEvent()

    data object ResetAddress: SendEvent()
}