package com.tonapps.wallet.data.multichain.exchange

import org.koin.dsl.module

val mcExchangeModule = module {
    single { SwapRepository(get(), get()) }
}
