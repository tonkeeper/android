package com.tonapps.deposit

import com.tonapps.deposit.data.ExchangeRepository
import com.tonapps.deposit.multicoin.data.RampRepository
import com.tonapps.deposit.multicoin.domain.ReceiveAccountsInteractor
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val depositModule = module {
    singleOf(::ExchangeRepository)
    singleOf(::RampRepository)
    factoryOf(::ReceiveAccountsInteractor)
}
