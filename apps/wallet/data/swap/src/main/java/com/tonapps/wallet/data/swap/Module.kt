package com.tonapps.wallet.data.swap

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val swapModule = module {
    singleOf(::SwapRepository)
}