package com.tonapps.wallet.data.multichain.exchange

import com.tonapps.wallet.data.multichain.exchange.status.SwapStatusTracker
import org.koin.dsl.module

val mcExchangeModule = module {
    single { SwapRepository(get(), get(), get()) }
    single { SwapStatusTracker(get(), get(), get(), get()) }
}
