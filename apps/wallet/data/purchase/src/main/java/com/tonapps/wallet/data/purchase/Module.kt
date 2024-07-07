package com.tonapps.wallet.data.purchase

import org.koin.dsl.module

val purchaseModule = module {
    single { PurchaseRepository(get(), get()) }
}