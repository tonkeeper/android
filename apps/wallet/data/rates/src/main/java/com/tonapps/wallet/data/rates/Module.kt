package com.tonapps.wallet.data.rates

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val ratesModule = module {
    singleOf(::RatesRepository)
}