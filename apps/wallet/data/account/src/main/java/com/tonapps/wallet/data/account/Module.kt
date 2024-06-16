package com.tonapps.wallet.data.account

import org.koin.dsl.module

val accountModule = module {
    single<AccountRepository>(createdAtStart = true) { AccountRepository(get(), get(), get()) }
}