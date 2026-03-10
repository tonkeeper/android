package com.tonapps.wallet.data.browser

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val browserModule = module {
    singleOf(::BrowserRepository)
}