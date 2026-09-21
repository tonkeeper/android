package com.tonapps.wallet.features.events

import com.tonapps.wallet.features.events.data.HistoryNftResolver
import com.tonapps.wallet.features.events.data.McEventsRepository
import org.koin.dsl.module

val eventsFeatureModule = module {
    single { McEventsRepository(get(), get()) }
    single { HistoryNftResolver(get()) }
    single { TxEventUiMapper(get(), get(), get()) }
}
