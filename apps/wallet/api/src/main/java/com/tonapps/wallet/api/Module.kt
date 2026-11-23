package com.tonapps.wallet.api

import com.tonapps.time.ServerTimeProvider
import com.tonapps.time.TimeProvider
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val apiModule = module {
    singleOf(::ServerTimeProvider) { bind<TimeProvider>() }
    single(createdAtStart = true) { API(get(), get(), get(), get(), get(), get()) }
}
