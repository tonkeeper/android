package com.tonapps.wallet.features.ledger

import com.tonapps.wallet.features.ledger.pairing.LedgerPairingFeature
import com.tonapps.wallet.features.ledger.progress.LedgerProgressPresenter
import com.tonapps.wallet.features.ledger.wallet.LedgerWalletFeature
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val ledgerFeatureModule = module {
    single { LedgerProgressPresenter(get()) }
    viewModel { params -> LedgerPairingFeature(params.get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { params -> LedgerWalletFeature(params.get(), params.get(), get(), get(), get(), get()) }
}
