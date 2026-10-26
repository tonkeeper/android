package com.tonapps.wallet.data.attribution

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val attributionModule = module {
    singleOf(::AttributionManager)
}
