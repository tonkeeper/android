package com.tonapps.wallet.data.collectibles

import org.koin.dsl.module

val collectiblesModule = module {
    single { CollectiblesRepository(get(), get()) }
}