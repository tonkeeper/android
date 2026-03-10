package com.tonapps.tonkeeper.ui.screen.send.main.state

import com.tonapps.icu.Coins

data class SendAmountState(
    val remainingFormat: CharSequence = "",
    val convertedFormat: CharSequence = "",
    val converted: Coins = Coins.ZERO,
    val insufficientBalance: Boolean = false,
    val currencyCode: String = "",
    val amountCurrency: Boolean = false,
    val hiddenBalance: Boolean = false,
)