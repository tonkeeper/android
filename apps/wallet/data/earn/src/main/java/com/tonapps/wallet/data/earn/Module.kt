package com.tonapps.wallet.data.earn

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val earnModule = module {
    singleOf(::EarnRepository)
}