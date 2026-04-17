package com.tonapps.deposit

import com.tonapps.deposit.data.ExchangeRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val depositModule = module {
    singleOf(::ExchangeRepository)
}
