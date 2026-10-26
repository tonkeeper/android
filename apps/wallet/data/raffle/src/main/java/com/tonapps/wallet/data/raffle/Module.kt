package com.tonapps.wallet.data.raffle

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val raffleModule = module {
    singleOf(::RaffleRepository)
}
