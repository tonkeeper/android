package com.tonapps.tonkeeper

import com.tonapps.tonkeeper.api.account.AccountRepository
import com.tonapps.tonkeeper.fragment.main.MainViewModel
import com.tonapps.tonkeeper.fragment.root.RootViewModel
import com.tonapps.tonkeeper.fragment.wallet.main.WalletScreenFeature
import com.tonapps.tonkeeper.ui.screen.name.base.NameViewModel
import com.tonapps.tonkeeper.ui.screen.init.InitViewModel
import com.tonapps.tonkeeper.ui.screen.init.pager.child.phrase.PhraseViewModel
import com.tonapps.tonkeeper.ui.screen.picker.WalletPickerViewModel
import kotlinx.coroutines.Dispatchers
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val koinModel = module {
    factory { Dispatchers.Default }
    single { App.walletManager }
    single { AccountRepository() }

    viewModel { parameters -> NameViewModel(mode = parameters.get(), get(), get()) }
    viewModel { parameters -> InitViewModel(action = parameters.get(), argsName = parameters.get(), argsPkBase64 = parameters.get(), get(), get()) }
    viewModel { PhraseViewModel() }
    viewModel { MainViewModel() }
    viewModel { WalletScreenFeature(get()) }
    viewModel { RootViewModel(get()) }
    viewModel { WalletPickerViewModel(get(), get()) }
}