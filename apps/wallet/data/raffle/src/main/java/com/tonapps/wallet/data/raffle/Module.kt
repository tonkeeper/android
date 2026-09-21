package com.tonapps.wallet.data.raffle

import com.tonapps.wallet.data.raffle.debug.RaffleDebugStore
import org.koin.core.module.dsl.createdAtStart
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val raffleModule = module {
    singleOf(::RaffleDebugStore) { createdAtStart() }
    singleOf(::RaffleRepository)
}
