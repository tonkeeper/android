package com.tonapps.wallet.data.purchase

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val purchaseModule = module {
    singleOf(::PurchaseRepository)
}