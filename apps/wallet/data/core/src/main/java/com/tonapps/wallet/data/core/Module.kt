package com.tonapps.wallet.data.core

import org.koin.dsl.module

val dataModule = module {
    single { ScreenCacheSource(get()) }
}