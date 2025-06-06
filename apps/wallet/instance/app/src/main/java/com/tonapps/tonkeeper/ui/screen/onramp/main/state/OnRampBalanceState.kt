package com.tonapps.tonkeeper.ui.screen.onramp.main.state

import com.tonapps.wallet.api.entity.BalanceEntity

data class OnRampBalanceState(
    val balance: BalanceEntity? = null,
    val insufficientBalance: Boolean = false,
    val remainingFormat: CharSequence? = null
)