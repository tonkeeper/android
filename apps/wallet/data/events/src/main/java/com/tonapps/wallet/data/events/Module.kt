package com.tonapps.wallet.data.events

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val eventsModule = module {
    singleOf(::EventsRepository)
}