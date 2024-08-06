package com.tonapps.wallet.data.events

import org.koin.dsl.module

val eventsModule = module {
    single { EventsRepository(get(), get(), get()) }
}