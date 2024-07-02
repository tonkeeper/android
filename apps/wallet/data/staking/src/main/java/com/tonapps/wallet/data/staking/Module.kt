package com.tonapps.wallet.data.staking

import org.koin.dsl.module

val stakingModule = module {
    single { StakingRepository(get(), get()) }
}