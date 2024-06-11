package com.tonapps.tonkeeper.ui.screen.send.state

import com.tonapps.icu.Coins

data class SendFeeState(
    val value: Coins,
    val format: CharSequence,
    val convertedFormat: CharSequence,
)