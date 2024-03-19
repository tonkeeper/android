package com.tonapps.wallet.data.token

import org.koin.dsl.module

val tokenModule = module {
    single { TokenRepository(get(), get(), get()) }
}