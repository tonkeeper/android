package com.tonapps.wallet.data.rn

import org.koin.dsl.module

val rnLegacyModule = module {
    single { RNLegacy(get()) }
}