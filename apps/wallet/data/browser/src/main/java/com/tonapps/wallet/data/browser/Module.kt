package com.tonapps.wallet.data.browser

import org.koin.dsl.module

val browserModule = module {
    single { BrowserRepository(get(), get()) }
}