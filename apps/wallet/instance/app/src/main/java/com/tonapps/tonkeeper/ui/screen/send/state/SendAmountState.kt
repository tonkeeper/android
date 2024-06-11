package com.tonapps.tonkeeper.ui.screen.send.state

import com.tonapps.icu.Coins

data class SendAmountState(
    val remainingFormat: CharSequence = "",
    val convertedFormat: CharSequence = "",
    val remaining: Coins = Coins.ZERO,
    val converted: Coins = Coins.ZERO,
    val insufficientBalance: Boolean = false,
)