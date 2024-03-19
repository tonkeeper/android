package com.tonapps.wallet.api

import org.koin.dsl.module

val apiModule = module {
    single { API(get(), get()) }
}