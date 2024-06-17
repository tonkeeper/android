package com.tonapps.wallet.data.tonconnect

import org.koin.dsl.module

val tonConnectModule = module {
    single { TonConnectRepository(get(), get(), get(), get(), get()) }
}