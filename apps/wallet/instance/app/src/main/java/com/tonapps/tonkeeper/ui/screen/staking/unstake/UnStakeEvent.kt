package com.tonapps.tonkeeper.ui.screen.staking.unstake

import com.tonapps.icu.Coins
import com.tonapps.wallet.data.staking.entities.PoolEntity

sealed class UnStakeEvent {

    data class OpenConfirm(
        val pool: PoolEntity,
        val amount: Coins
    ): UnStakeEvent()

    data object Finish: UnStakeEvent()

}