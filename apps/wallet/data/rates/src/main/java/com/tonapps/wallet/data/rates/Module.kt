package com.tonapps.wallet.data.rates

import org.koin.dsl.module

val ratesModule = module {
    single { RatesRepository(get(), get()) }
}