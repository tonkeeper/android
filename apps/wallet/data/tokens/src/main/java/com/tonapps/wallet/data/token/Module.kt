package com.tonapps.wallet.data.token

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val tokenModule = module {
    singleOf(::TokenRepository)
}