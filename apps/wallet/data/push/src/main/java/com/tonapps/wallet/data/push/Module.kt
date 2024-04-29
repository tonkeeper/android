package com.tonapps.wallet.data.push

import org.koin.dsl.module

val pushModule = module {
    single { PushManager(get(), get(), get(), get(), get(), get(), get()) }
}