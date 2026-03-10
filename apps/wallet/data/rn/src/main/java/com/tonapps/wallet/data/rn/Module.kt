package com.tonapps.wallet.data.rn

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val rnLegacyModule = module {
    singleOf(::RNLegacy)
}