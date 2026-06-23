package com.tonapps.wallet.features.events

import org.koin.dsl.module

val eventsFeatureModule = module {
    single { TxEventUiMapper(get(), get(), get()) }
}
