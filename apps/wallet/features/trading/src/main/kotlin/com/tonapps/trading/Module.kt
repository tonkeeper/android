package com.tonapps.trading

import com.tonapps.trading.data.TradingRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val tradingModule = module {
    singleOf(::TradingRepository)
}
