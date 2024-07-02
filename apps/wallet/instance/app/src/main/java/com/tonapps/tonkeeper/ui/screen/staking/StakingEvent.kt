package com.tonapps.tonkeeper.ui.screen.staking

import com.tonapps.icu.Coins
import com.tonapps.wallet.data.staking.entities.PoolInfoEntity

sealed class StakingEvent {
    data object OpenOptions: StakingEvent()
    data class OpenDetails(val pool: PoolInfoEntity): StakingEvent()
    data class OpenConfirm(val pool: PoolInfoEntity, val amount: Coins): StakingEvent()
    data object Finish: StakingEvent()
}