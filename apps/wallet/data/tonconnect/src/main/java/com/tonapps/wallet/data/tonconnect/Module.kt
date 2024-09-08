package com.tonapps.wallet.data.tonconnect

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val tonConnectModule = module {
    singleOf(::TonConnectRepository)
}