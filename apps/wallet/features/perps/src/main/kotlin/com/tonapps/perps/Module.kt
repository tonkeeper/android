package com.tonapps.perps

import com.tonapps.async.Async
import com.tonapps.chainkit.CryptoKitClient
import com.tonapps.deposit.multicoin.screens.confirm.perpetual.PerpetualConfirmDelegate
import com.tonapps.deposit.multicoin.screens.confirm.perpetual.PerpetualDepositAuthorizer
import com.tonapps.deposit.multicoin.screens.confirm.perpetual.PerpetualDepositTracker
import com.tonapps.perps.data.CatalogSearchRepository
import com.tonapps.perps.data.PerpsAccountRefresh
import com.tonapps.perps.data.PerpsCandleFeed
import com.tonapps.perps.data.PerpsLivePrices
import com.tonapps.perps.data.PerpsPortfolioStore
import com.tonapps.perps.data.PerpsPriceFeed
import com.tonapps.perps.data.PerpsRepository
import com.tonapps.perps.funding.LighterPublicApi
import com.tonapps.perps.funding.PerpsConfirmDelegate
import com.tonapps.perps.funding.PerpsDepositTracker
import com.tonapps.perps.funding.PerpsDepositRegistrar
import com.tonapps.perps.funding.PerpsDepositService
import com.tonapps.perps.funding.PerpsTransactionLookup
import com.tonapps.perps.funding.PerpsFundingRecovery
import com.tonapps.perps.funding.PerpsWithdrawService
import com.tonapps.perps.funding.PerpsWithdrawTracker
import com.tonapps.perps.trade.PerpsAccountService
import com.tonapps.perps.trade.PerpsAccountTierUpgrader
import com.tonapps.perps.trade.PerpsClientOrderIndexStore
import com.tonapps.perps.trade.PerpsOperationRecovery
import com.tonapps.perps.trade.PerpsPlannerAdapter
import com.tonapps.perps.trade.PerpsTradeExecutor
import com.tonapps.wallet.api.API
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val perpsModule = module {
    singleOf(::CatalogSearchRepository)
    singleOf(::PerpsCandleFeed)
    singleOf(::PerpsPriceFeed)
    singleOf(::PerpsLivePrices)
    singleOf(::PerpsRepository)
    singleOf(::PerpsAccountRefresh)
    singleOf(::PerpsClientOrderIndexStore)
    single { PerpsAccountTierUpgrader(get(), get(), Async.ioScope()) }
    single { PerpsAccountService(get(), get(), get(), get(), get(), get(), Async.ioScope()) }
    single { get<CryptoKitClient>().perpetual.lighter }
    singleOf(::PerpsPlannerAdapter)
    single { PerpsTradeExecutor(get(), get(), get(), get(), get(), Async.ioScope()) }
    single(createdAtStart = true) {
        PerpsOperationRecovery(get(), get(), Async.ioScope()).also { it.start() }
    }
    singleOf(::PerpsPortfolioStore)
    singleOf(::PerpsDepositRegistrar)
    singleOf(::PerpsTransactionLookup)
    single { LighterPublicApi(get<API>().defaultHttpClient) }
    singleOf(::PerpsWithdrawTracker)
    single(createdAtStart = true) {
        PerpsFundingRecovery(get(), get(), get(), get(), get(), get(), get(), Async.ioScope()).also { it.start() }
    }
    singleOf(::PerpsDepositService)
    single<PerpetualConfirmDelegate> { PerpsConfirmDelegate(get()) }
    single<PerpetualDepositTracker> { PerpsDepositTracker(get(), get(), get(), get()) }
    single<PerpetualDepositAuthorizer> { get<PerpsDepositService>() }
    singleOf(::PerpsWithdrawService)
    singleOf(::PerpsScreenTracker)
}
