package com.tonapps.wallet.features.events

import com.tonapps.perps.screens.details.PerpsMarketHistorySection
import com.tonapps.wallet.features.events.data.HistoryNftResolver
import com.tonapps.wallet.features.events.data.HistoryPerpsMarketResolver
import com.tonapps.wallet.features.events.data.McEventsRepository
import com.tonapps.wallet.features.events.screens.market.PerpsMarketHistorySectionImpl
import org.koin.dsl.module

val eventsFeatureModule = module {
    single { McEventsRepository(get(), get(), get()) }
    single { HistoryNftResolver(get()) }
    single { HistoryPerpsMarketResolver(get()) }
    single { TxEventUiMapper(get(), get(), get()) }
    single<PerpsMarketHistorySection> { PerpsMarketHistorySectionImpl() }
}
