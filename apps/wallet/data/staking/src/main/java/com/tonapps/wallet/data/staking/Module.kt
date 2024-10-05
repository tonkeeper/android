package com.tonapps.wallet.data.staking

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val stakingModule = module {
    singleOf(::StakingRepository)
}