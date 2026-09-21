package com.tonapps.portfolio

import com.tonapps.portfolio.domain.WalletFiatBalanceInteractor
import com.tonapps.portfolio.domain.WalletFiatBalanceInteractorImpl
import com.tonapps.portfolio.wallet.WalletLookup
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val portfolioModule = module {
    singleOf(::WalletLookup)
    singleOf(::WalletFiatBalanceInteractorImpl) { bind<WalletFiatBalanceInteractor>() }
}
