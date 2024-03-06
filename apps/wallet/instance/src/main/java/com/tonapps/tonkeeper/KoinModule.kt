package com.tonapps.tonkeeper

import com.tonapps.tonkeeper.api.account.AccountRepository
import com.tonapps.tonkeeper.fragment.main.MainViewModel
import com.tonapps.tonkeeper.fragment.root.RootViewModel
import com.tonapps.tonkeeper.fragment.wallet.history.HistoryScreenFeature
import com.tonapps.tonkeeper.ui.screen.currency.CurrencyViewModel
import com.tonapps.tonkeeper.ui.screen.name.base.NameViewModel
import com.tonapps.tonkeeper.ui.screen.init.InitViewModel
import com.tonapps.tonkeeper.ui.screen.init.pager.child.phrase.PhraseViewModel
import com.tonapps.tonkeeper.ui.screen.name.edit.EditNameViewModel
import com.tonapps.tonkeeper.ui.screen.picker.PickerViewModel
import com.tonapps.tonkeeper.ui.screen.settings.SettingsViewModel
import com.tonapps.tonkeeper.ui.screen.wallet.WalletViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val koinModel = module {
    factory { Dispatchers.Default }
    single { CoroutineScope(Dispatchers.IO + SupervisorJob()) }
    single { App.walletManager }
    single { App.settings }
    single { AccountRepository() }

    viewModel { parameters -> NameViewModel(mode = parameters.get(), get(), get()) }
    viewModel { parameters -> InitViewModel(action = parameters.get(), get(), get()) }
    viewModel { PhraseViewModel() }
    viewModel { MainViewModel() }
    viewModel { RootViewModel(get()) }
    viewModel { PickerViewModel(get(), get(), get()) }
    viewModel { WalletViewModel(get(), get(), get()) }
    viewModel { CurrencyViewModel(get()) }
    viewModel { SettingsViewModel(get(), get()) }
    viewModel { EditNameViewModel(get()) }
}