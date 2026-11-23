package com.tonapps.wallet.data.loyalty

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val loyaltyModule = module {
    singleOf(::LoyaltyRepository)
}
