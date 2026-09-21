package com.tonapps.perps

import com.tonapps.perps.data.CatalogSearchRepository
import com.tonapps.perps.data.PerpsAccountRefresh
import com.tonapps.perps.data.PerpsCandleFeed
import com.tonapps.perps.data.PerpsLivePrices
import com.tonapps.perps.data.PerpsPriceFeed
import com.tonapps.perps.data.PerpsRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val perpsModule = module {
    singleOf(::CatalogSearchRepository)
    singleOf(::PerpsCandleFeed)
    singleOf(::PerpsPriceFeed)
    singleOf(::PerpsLivePrices)
    singleOf(::PerpsRepository)
    singleOf(::PerpsAccountRefresh)
}
