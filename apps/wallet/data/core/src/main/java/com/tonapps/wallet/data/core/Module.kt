package com.tonapps.wallet.data.core

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val dataModule = module {
    singleOf(::ScreenCacheSource)
}