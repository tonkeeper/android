package com.tonapps.tonkeeper

import com.tonapps.network.NetworkMonitor
import com.tonapps.tonkeeper.api.account.AccountRepository
import com.tonapps.tonkeeper.ui.screen.main.MainViewModel
import com.tonapps.tonkeeper.ui.screen.root.RootViewModel
import com.tonapps.tonkeeper.fragment.send.amount.AmountScreenFeature
import com.tonapps.tonkeeper.fragment.send.confirm.ConfirmScreenFeature
import com.tonapps.tonkeeper.fragment.tonconnect.auth.TCAuthViewModel
import com.tonapps.tonkeeper.password.PasscodeDataStore
import com.tonapps.tonkeeper.password.PasscodeRepository
import com.tonapps.tonkeeper.ui.screen.collectibles.CollectiblesViewModel
import com.tonapps.tonkeeper.ui.screen.events.EventsViewModel
import com.tonapps.tonkeeper.ui.screen.settings.currency.CurrencyViewModel
import com.tonapps.tonkeeper.ui.screen.init.InitViewModel
import com.tonapps.tonkeeper.ui.screen.settings.language.LanguageViewModel
import com.tonapps.tonkeeper.ui.screen.name.base.NameViewModel
import com.tonapps.tonkeeper.ui.screen.name.edit.EditNameViewModel
import com.tonapps.tonkeeper.ui.screen.picker.PickerViewModel
import com.tonapps.tonkeeper.ui.screen.settings.main.SettingsViewModel
import com.tonapps.tonkeeper.ui.screen.settings.security.SecurityViewModel
import com.tonapps.tonkeeper.ui.screen.settings.theme.ThemeViewModel
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
    single { PasscodeDataStore(get()) }
    single { PasscodeRepository(get(), get()) }
    single { NetworkMonitor(get(), get()) }

    viewModel { parameters -> NameViewModel(mode = parameters.get(), get(), get()) }
    viewModel { parameters -> InitViewModel(parameters.get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { MainViewModel() }
    viewModel { RootViewModel(get(), get(), get()) }
    viewModel { AmountScreenFeature(get()) }
    viewModel { PickerViewModel(get(), get(), get()) }
    viewModel { WalletViewModel(get(), get(), get(), get(), get()) }
    viewModel { ConfirmScreenFeature(get(), get()) }
    viewModel { CurrencyViewModel(get()) }
    viewModel { SettingsViewModel(get(), get(), get()) }
    viewModel { EditNameViewModel(get()) }
    viewModel { LanguageViewModel(get()) }
    viewModel { SecurityViewModel(get(), get(), get()) }
    viewModel { ThemeViewModel(get()) }
    viewModel { EventsViewModel(get(), get(), get()) }
    viewModel { parameters -> TCAuthViewModel(request = parameters.get(), get(), get()) }
    viewModel { CollectiblesViewModel(get(), get(), get()) }
}